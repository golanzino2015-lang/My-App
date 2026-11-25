#!/usr/bin/env python3
"""My AI backup server.

Keeps a copy of your AI's brain on this computer, so you can restore it on your phone after
reinstalling the app. Uses only Python's standard library: nothing to install except Python.

Run it:   python brain_server.py
Then, in the My AI app, enter the address and pairing code it prints.
Your phone and this computer must be on the same Wi-Fi.
"""
import json
import os
import secrets
import shutil
import socket
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("MYAI_PORT", "8765"))
HERE = os.path.dirname(os.path.abspath(sys.argv[0] if sys.argv[0] else __file__))
DATA = os.path.join(HERE, "my_ai_backup")
CONFIG = os.path.join(DATA, "pairing.json")
BRAIN, META, INCOMING = (os.path.join(DATA, n) for n in ("brain.bin", "brain.json", "incoming.bin"))
HISTORY = os.path.join(DATA, "history")
KEEP_OLD = 5                      # older copies kept in case something goes wrong
MAX_BYTES = 1024 * 1024 * 1024    # 1 GB


def pairing_code():
    os.makedirs(DATA, exist_ok=True)
    try:
        with open(CONFIG) as f:
            return json.load(f)["code"]
    except (OSError, ValueError, KeyError):
        code = "".join(secrets.choice("0123456789") for _ in range(6))
        with open(CONFIG, "w") as f:
            json.dump({"code": code}, f)
        return code


CODE = pairing_code()


def lan_address():
    """This computer's address on the Wi-Fi (no data is sent anywhere)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def summary():
    if not (os.path.exists(BRAIN) and os.path.exists(META)):
        return {"ok": True, "has_brain": False}
    with open(META, encoding="utf-8") as f:
        meta = json.load(f)
    return {"ok": True, "has_brain": True, "name": meta.get("name", ""), "size": meta.get("size", ""),
            "tokens": meta.get("tokens", 0), "saved_at": int(os.path.getmtime(META)),
            "bytes": os.path.getsize(BRAIN)}


class Handler(BaseHTTPRequestHandler):
    server_version = "MyAIBackup/1"

    def log_message(self, fmt, *args):
        pass  # quiet; we print our own short lines

    def reply(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        if secrets.compare_digest(self.headers.get("X-Pair-Code", ""), CODE):
            return True
        self.reply(403, {"ok": False, "error": "Wrong pairing code"})
        return False

    def do_GET(self):
        if not self.authorized():
            return
        if self.path == "/api/hello":
            return self.reply(200, summary())
        if self.path in ("/api/brain/meta", "/api/brain/file"):
            path = META if self.path.endswith("meta") else BRAIN
            if not os.path.exists(path):
                return self.reply(404, {"ok": False, "error": "No brain saved yet"})
            size = os.path.getsize(path)
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(size))
            self.end_headers()
            with open(path, "rb") as f:
                shutil.copyfileobj(f, self.wfile, 1 << 20)
            if path == BRAIN:
                print(time.strftime("%H:%M:%S"), "Phone restored the brain", f"({size / 1e6:.1f} MB)")
            return
        self.reply(404, {"ok": False, "error": "Unknown address"})

    def do_PUT(self):
        if not self.authorized():
            return
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > MAX_BYTES:
            return self.reply(400, {"ok": False, "error": "Bad size"})
        if self.path == "/api/brain/file":
            # 1) the brain arrives first and waits as "incoming"
            with open(INCOMING, "wb") as f:
                left = length
                while left:
                    chunk = self.rfile.read(min(left, 1 << 20))
                    if not chunk:
                        return self.reply(400, {"ok": False, "error": "Upload cut off"})
                    f.write(chunk)
                    left -= len(chunk)
            return self.reply(200, {"ok": True})
        if self.path == "/api/brain/meta":
            # 2) the details arrive and the new backup replaces the old one in one go
            raw = self.rfile.read(length)
            try:
                meta = json.loads(raw.decode("utf-8"))
            except ValueError:
                return self.reply(400, {"ok": False, "error": "Bad details"})
            if not os.path.exists(INCOMING):
                return self.reply(409, {"ok": False, "error": "Send the brain file first"})
            if os.path.exists(BRAIN) and os.path.exists(META):
                old = os.path.join(HISTORY, time.strftime("%Y%m%d-%H%M%S"))
                os.makedirs(old, exist_ok=True)
                shutil.copy2(BRAIN, os.path.join(old, "brain.bin"))
                shutil.copy2(META, os.path.join(old, "brain.json"))
                for stale in sorted(os.listdir(HISTORY))[:-KEEP_OLD]:
                    shutil.rmtree(os.path.join(HISTORY, stale), ignore_errors=True)
            os.replace(INCOMING, BRAIN)
            tmp = META + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(meta, f)
            os.replace(tmp, META)
            print(time.strftime("%H:%M:%S"), f"Backed up {meta.get('name', 'brain')}:",
                  f"{meta.get('tokens', 0):,} words read", f"({os.path.getsize(BRAIN) / 1e6:.1f} MB)")
            return self.reply(200, {"ok": True, "saved_at": int(time.time())})
        self.reply(404, {"ok": False, "error": "Unknown address"})


def main():
    address = f"http://{lan_address()}:{PORT}"
    print()
    print("  My AI backup server is running")
    print()
    print(f"  Address:       {address}")
    print(f"  Pairing code:  {CODE}")
    print()
    print("  Enter both in the My AI app (Backup to my PC).")
    print("  Keep this window open. Your phone must be on the same Wi-Fi.")
    print(f"  Backups are stored in: {DATA}")
    print()
    s = summary()
    if s["has_brain"]:
        print(f"  Saved brain: {s['name']} · {s['tokens']:,} words read")
        print()
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
    except OSError as e:
        print(f"\n  Couldn't start: {e}\n  Is the server already running in another window?")
        input("  Press Enter to close.")

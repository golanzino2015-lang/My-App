"""AI 3D Studio server. Run:  python -m server.app   (from the ai-3d-studio folder)."""
import io
import json
import os
import queue
import threading
import time
import traceback
import uuid
from collections import OrderedDict
from typing import Optional

import uvicorn
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from PIL import Image
from pydantic import BaseModel

from .generators import GENERATORS, GenerateParams

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB_DIR = os.path.join(ROOT, "web")
OUT_DIR = os.environ.get("STUDIO_OUTPUT_DIR", os.path.join(ROOT, "outputs"))
KEEP_IN_MEMORY = int(os.environ.get("STUDIO_KEEP_MODELS", "4"))  # raw results kept for re-export
TEXTURE_SIZES = (256, 512, 1024, 2048, 4096)
os.makedirs(OUT_DIR, exist_ok=True)

app = FastAPI(title="AI 3D Studio")

jobs: dict[str, dict] = {}
handles: "OrderedDict[str, tuple]" = OrderedDict()   # model_id -> (generator_id, handle)
work: "queue.Queue[tuple]" = queue.Queue()
lock = threading.Lock()


def _new_job(kind: str, model_id: str) -> dict:
    job = {"id": uuid.uuid4().hex[:12], "kind": kind, "model_id": model_id, "status": "queued",
           "stage": "Waiting in queue", "progress": 0.0, "error": None, "result": None,
           "created": time.time()}
    with lock:
        jobs[job["id"]] = job
    return job


def _meta_path(model_id: str) -> str:
    return os.path.join(OUT_DIR, model_id, "meta.json")


def _load_meta(model_id: str) -> Optional[dict]:
    try:
        with open(_meta_path(model_id)) as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def _do_export(job, gen, handle, meta, target_faces, texture_size, progress):
    version = meta.get("version", 0) + 1
    name = f"model_v{version}.glb"
    stats = gen.export(handle, target_faces, texture_size,
                       os.path.join(OUT_DIR, meta["id"], name), progress)
    meta.update(version=version, file=name, faces=stats.faces, vertices=stats.vertices,
                texture_size=stats.texture_size, target_faces=target_faces)
    with open(_meta_path(meta["id"]), "w") as f:
        json.dump(meta, f, indent=2)
    job["result"] = _public(meta)


def _public(meta: dict) -> dict:
    out = dict(meta)
    out["model_url"] = f"/outputs/{meta['id']}/{meta['file']}"
    out["can_reexport"] = meta["id"] in handles
    return out


def worker():
    while True:
        job, task = work.get()

        def progress(stage, p, job=job):
            job["stage"], job["progress"] = stage, float(p)

        job["status"] = "running"
        try:
            if job["kind"] == "generate":
                gen = GENERATORS[task["generator"]]
                t0 = time.time()
                handle = gen.generate(task["params"], progress)
                meta = {"id": job["model_id"], "prompt": task["params"].prompt,
                        "has_image": task["params"].image is not None,
                        "generator": gen.id, "ai": gen.is_ai, "seed": task["params"].seed,
                        "quality": task["params"].quality, "created": time.time(),
                        "source_faces": gen.source_faces(handle)}
                with lock:
                    handles[meta["id"]] = (gen.id, handle)
                    while len(handles) > KEEP_IN_MEMORY:
                        handles.popitem(last=False)
                target = min(task["target_faces"], meta["source_faces"])
                _do_export(job, gen, handle, meta, target, task["texture_size"], progress)
                meta["seconds"] = round(time.time() - t0, 1)
                with open(_meta_path(meta["id"]), "w") as f:
                    json.dump(meta, f, indent=2)
                job["result"] = _public(meta)
            else:  # re-export with new triangle count / texture resolution
                with lock:
                    entry = handles.get(job["model_id"])
                if entry is None:
                    raise RuntimeError("This model is no longer in memory. Generate it again "
                                       "(same prompt and seed gives the same result).")
                gen = GENERATORS[entry[0]]
                meta = _load_meta(job["model_id"])
                target = min(task["target_faces"], meta["source_faces"])
                _do_export(job, gen, entry[1], meta, target, task["texture_size"], progress)
            job.update(status="done", stage="Done", progress=1.0)
        except Exception as e:
            traceback.print_exc()
            msg = str(e)
            if "out of memory" in msg.lower():
                msg = "The GPU ran out of memory. Try a lower quality or a smaller texture."
            job.update(status="error", error=msg or e.__class__.__name__, stage="Failed")
        finally:
            work.task_done()


threading.Thread(target=worker, daemon=True).start()


def _clamp_settings(target_faces: int, texture_size: int) -> tuple[int, int]:
    if texture_size not in TEXTURE_SIZES:
        raise HTTPException(400, f"texture_size must be one of {TEXTURE_SIZES}")
    return max(500, min(int(target_faces), 2_000_000)), texture_size


@app.get("/api/status")
def status():
    gens = [g.info() for g in GENERATORS.values()]
    default = next((g["id"] for g in gens if g["available"] and g["ai"]), "demo")
    return {"generators": gens, "default": default, "texture_sizes": TEXTURE_SIZES,
            "queue": work.qsize()}


@app.post("/api/generate")
async def generate(prompt: str = Form(""), generator: str = Form("trellis"),
                   seed: int = Form(1), quality: str = Form("balanced"),
                   target_faces: int = Form(50_000), texture_size: int = Form(1024),
                   image: Optional[UploadFile] = File(None)):
    gen = GENERATORS.get(generator)
    if gen is None:
        raise HTTPException(400, "Unknown generator")
    ok, reason = gen.available()
    if not ok:
        raise HTTPException(400, f"{gen.label} is not available: {reason}")
    pil = None
    if image is not None and image.filename:
        try:
            pil = Image.open(io.BytesIO(await image.read()))
            pil.load()
            pil = pil.convert("RGBA")
        except Exception:
            raise HTTPException(400, "Could not read that image")
    if not prompt.strip() and pil is None:
        raise HTTPException(400, "Type a prompt or add an image")
    target_faces, texture_size = _clamp_settings(target_faces, texture_size)
    model_id = time.strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6]
    os.makedirs(os.path.join(OUT_DIR, model_id), exist_ok=True)
    if pil is not None:
        pil.save(os.path.join(OUT_DIR, model_id, "input.png"))
    job = _new_job("generate", model_id)
    work.put((job, {"generator": gen.id, "target_faces": target_faces, "texture_size": texture_size,
                    "params": GenerateParams(prompt=prompt.strip(), image=pil, seed=seed,
                                             quality=quality)}))
    return {"job_id": job["id"], "model_id": model_id}


class ExportBody(BaseModel):
    target_faces: int
    texture_size: int


@app.post("/api/models/{model_id}/export")
def reexport(model_id: str, body: ExportBody):
    if _load_meta(model_id) is None:
        raise HTTPException(404, "Model not found")
    target_faces, texture_size = _clamp_settings(body.target_faces, body.texture_size)
    job = _new_job("export", model_id)
    work.put((job, {"target_faces": target_faces, "texture_size": texture_size}))
    return {"job_id": job["id"], "model_id": model_id}


@app.get("/api/jobs/{job_id}")
def job_status(job_id: str):
    job = jobs.get(job_id)
    if job is None:
        raise HTTPException(404, "Job not found")
    return job


@app.get("/api/models")
def list_models():
    items = []
    for name in sorted(os.listdir(OUT_DIR), reverse=True):
        meta = _load_meta(name)
        if meta and meta.get("file"):
            items.append(_public(meta))
    return items[:100]


@app.delete("/api/models/{model_id}")
def delete_model(model_id: str):
    import shutil
    path = os.path.join(OUT_DIR, os.path.basename(model_id))
    if not os.path.isdir(path):
        raise HTTPException(404, "Model not found")
    shutil.rmtree(path)
    handles.pop(model_id, None)
    return {"ok": True}


app.mount("/outputs", StaticFiles(directory=OUT_DIR), name="outputs")


@app.get("/")
def index():
    return FileResponse(os.path.join(WEB_DIR, "index.html"))


app.mount("/", StaticFiles(directory=WEB_DIR), name="web")

if __name__ == "__main__":
    host = os.environ.get("STUDIO_HOST", "127.0.0.1")
    port = int(os.environ.get("STUDIO_PORT", "7860"))
    print(f"\n  AI 3D Studio running at http://{host}:{port}\n")
    uvicorn.run(app, host=host, port=port, log_level="warning")

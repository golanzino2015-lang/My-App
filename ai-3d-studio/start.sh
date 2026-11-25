#!/usr/bin/env bash
# Start AI 3D Studio fully offline, then open http://127.0.0.1:7860
cd "$(dirname "$0")"
[ -f venv/bin/activate ] && source venv/bin/activate
export HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1
exec python -m server.app

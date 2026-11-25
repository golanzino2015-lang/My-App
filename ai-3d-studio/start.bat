@echo off
rem Start AI 3D Studio fully offline, then open http://127.0.0.1:7860
cd /d "%~dp0"
if exist venv\Scripts\activate.bat call venv\Scripts\activate.bat
set HF_HUB_OFFLINE=1
set TRANSFORMERS_OFFLINE=1
python -m server.app

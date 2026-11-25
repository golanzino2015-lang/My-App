# AI 3D Studio

A local website that turns a text prompt (or an image) into a textured 3D model
with AI, then lets you view and adjust it.

- **Prompt or image to 3D.** Uses Microsoft **TRELLIS** (MIT license).
- **3D viewer** with Blender-style shading: **Solid** (untextured clay),
  **Textured**, **Normals**, plus a **Wireframe** overlay, grid, auto-spin and frame.
  Keys: `1` `2` `3`, `W`, `G`, `R`, `F`.
- **Triangle count:** pick anything from 500 up to the full-detail mesh.
- **Texture resolution:** 256 to 4096.
- **Apply to current model** re-simplifies and re-bakes the texture without
  generating again (the last 4 models stay in memory).
- History of everything you made, and **Download .glb** (opens in Blender, Unity, Godot and others).
- **Works fully offline** after a one-time model download. three.js is bundled,
  so nothing loads from the internet.

## Requirements for the AI

- An **NVIDIA GPU with 16 GB or more of VRAM** (24 GB recommended: RTX 3090, 4090, 5090, A5000 or similar).
- **Linux** is the officially supported OS for TRELLIS. On Windows, use WSL2 or a
  community Windows build of TRELLIS.
- About 15 GB of disk for the models.

Without a GPU, the site still runs in **Demo mode**, which makes procedural
shapes. Demo mode is *not AI*. It's there so you can try the viewer and controls.

## Setup (once, with internet)

```bash
cd ai-3d-studio

# 1. Get TRELLIS and install its GPU dependencies (creates a conda env "trellis")
git clone --recurse-submodules https://github.com/microsoft/TRELLIS.git
cd TRELLIS
. ./setup.sh --new-env --basic --xformers --flash-attn --diffoctreerast --spconv --mipgaussian --kaolin --nvdiffrast
cd ..

# 2. Install the studio's own packages into the same env
conda activate trellis
pip install -r requirements.txt

# 3. Download all models so it works offline afterwards
python scripts/download_models.py
```

If your GPU doesn't support flash-attn (older than RTX 30-series), leave out
`--flash-attn` and run with `ATTN_BACKEND=xformers`.

If TRELLIS lives somewhere else, set `TRELLIS_PATH=/path/to/TRELLIS`.

## Run (offline)

```bash
conda activate trellis
./start.sh            # Windows: start.bat
```

Open **http://127.0.0.1:7860**.

A text prompt takes roughly 10–60 seconds on a modern GPU. The first generation is
slower because the model loads first.

### Settings

| Environment variable | Default | Meaning |
|---|---|---|
| `STUDIO_PORT` | 7860 | Port |
| `STUDIO_HOST` | 127.0.0.1 | Use `0.0.0.0` to open it from other devices on your network |
| `STUDIO_KEEP_MODELS` | 4 | Recent models kept in memory for "Apply" |
| `STUDIO_MODELS_DIR` | `./models` | Where models are stored |
| `STUDIO_OUTPUT_DIR` | `./outputs` | Where generated `.glb` files go |

## Modifying it

```
server/app.py                    web server, job queue, API
server/generators/base.py        interface every engine implements
server/generators/trellis_gen.py TRELLIS (AI) engine
server/generators/demo.py        demo engine (no AI)
web/index.html, app.js, style.css   the website and 3D viewer (three.js)
```

**Adding another AI engine** (TRELLIS.2, Hunyuan3D, TripoSR and others): copy
`trellis_gen.py`, implement `generate`, `source_faces` and `export`, and add the
class to `GENERATORS` in `server/generators/__init__.py`. It then appears in the
Engine menu automatically.

## Licenses

- TRELLIS code and models: MIT, © Microsoft.
- three.js (bundled in `web/vendor/three`): MIT.

"""Download every AI model the studio needs, once, so it then works offline.

Run from the ai-3d-studio folder, with internet:
    python scripts/download_models.py

Fills:
  models/TRELLIS-text-xlarge, models/TRELLIS-image-large   (main models)
  ~/.cache/huggingface  (CLIP text encoder)
  ~/.cache/torch/hub    (DINOv2 image encoder)
  ~/.u2net              (background remover for image prompts)
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from huggingface_hub import snapshot_download  # noqa: E402

from server.generators.trellis_gen import (IMAGE_MODEL, MODELS_DIR, TEXT_MODEL,  # noqa: E402
                                           _setup_env, model_path)


def main():
    os.makedirs(MODELS_DIR, exist_ok=True)
    for repo in (TEXT_MODEL, IMAGE_MODEL):
        dest = os.path.join(MODELS_DIR, repo.split("/")[-1])
        print(f"Downloading {repo} -> {dest}")
        snapshot_download(repo, local_dir=dest)

    # Loading each pipeline once pulls in the helper models it fetches on its own.
    _setup_env()
    from trellis.pipelines import TrellisImageTo3DPipeline, TrellisTextTo3DPipeline
    print("Caching CLIP text encoder…")
    TrellisTextTo3DPipeline.from_pretrained(model_path(TEXT_MODEL))
    print("Caching DINOv2 image encoder…")
    TrellisImageTo3DPipeline.from_pretrained(model_path(IMAGE_MODEL))
    print("Caching background remover…")
    import rembg
    rembg.new_session("u2net")
    print("\nAll models downloaded. The studio now works offline.")


if __name__ == "__main__":
    main()

"""Real AI generator: Microsoft TRELLIS (MIT license).

Text -> 3D with TRELLIS-text-xlarge, image -> 3D with TRELLIS-image-large.
Needs an NVIDIA GPU (about 16 GB VRAM or more) and the TRELLIS code installed
(see README). Models are loaded from ./models so it works offline once
scripts/download_models.py has run.
"""
import gc
import os
import sys

from .base import ExportResult, GenerateParams, Generator

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODELS_DIR = os.environ.get("STUDIO_MODELS_DIR", os.path.join(ROOT, "models"))
TEXT_MODEL = "microsoft/TRELLIS-text-xlarge"
IMAGE_MODEL = "microsoft/TRELLIS-image-large"

QUALITY_STEPS = {"fast": 8, "balanced": 12, "high": 25}


def model_path(repo_id: str) -> str:
    """Local copy if downloaded, otherwise the Hugging Face id (needs internet)."""
    local = os.path.join(MODELS_DIR, repo_id.split("/")[-1])
    return local if os.path.exists(os.path.join(local, "pipeline.json")) else repo_id


def _setup_env():
    os.environ.setdefault("SPCONV_ALGO", "native")
    trellis_path = os.environ.get("TRELLIS_PATH", os.path.join(ROOT, "TRELLIS"))
    if os.path.isdir(trellis_path) and trellis_path not in sys.path:
        sys.path.insert(0, trellis_path)


class TrellisGenerator(Generator):
    id = "trellis"
    label = "TRELLIS (AI, needs NVIDIA GPU)"
    supports_text = True
    supports_image = True

    def __init__(self):
        self._pipes = {}
        self._checked = None

    def available(self):
        if self._checked is None:
            _setup_env()
            try:
                import torch
                if not torch.cuda.is_available():
                    self._checked = (False, "No NVIDIA GPU (CUDA) found on this computer")
                else:
                    import trellis.pipelines  # noqa: F401
                    self._checked = (True, torch.cuda.get_device_name(0))
            except ImportError as e:
                self._checked = (False, f"TRELLIS is not installed ({e.name} missing). See README")
            except Exception as e:  # broken CUDA install etc.
                self._checked = (False, f"TRELLIS failed to load: {e}")
        return self._checked

    def _pipeline(self, kind: str, progress):
        if kind in self._pipes:
            return self._pipes[kind]
        import torch
        from trellis.pipelines import TrellisImageTo3DPipeline, TrellisTextTo3DPipeline
        # Keep one model on the GPU at a time so 16-24 GB cards are enough.
        for other in list(self._pipes):
            del self._pipes[other]
        gc.collect()
        torch.cuda.empty_cache()
        progress(f"Loading {kind} model (first time is slow)", 0.05)
        cls, repo = ((TrellisTextTo3DPipeline, TEXT_MODEL) if kind == "text"
                     else (TrellisImageTo3DPipeline, IMAGE_MODEL))
        pipe = cls.from_pretrained(model_path(repo))
        pipe.cuda()
        self._pipes[kind] = pipe
        return pipe

    def generate(self, params: GenerateParams, progress):
        steps = QUALITY_STEPS.get(params.quality, 12)
        kwargs = dict(
            seed=int(params.seed),
            sparse_structure_sampler_params={"steps": steps, "cfg_strength": 7.5},
            slat_sampler_params={"steps": steps, "cfg_strength": 3 if params.image is not None else 7.5},
            formats=["gaussian", "mesh"],
        )
        if params.image is not None:
            pipe = self._pipeline("image", progress)
            progress("Generating 3D from image", 0.2)
            out = pipe.run(params.image, **kwargs)
        else:
            pipe = self._pipeline("text", progress)
            progress("Generating 3D from text", 0.2)
            out = pipe.run(params.prompt, **kwargs)
        # Move results off the model's working set; keep only what export needs.
        return {"gaussian": out["gaussian"][0], "mesh": out["mesh"][0]}

    def source_faces(self, handle):
        return int(handle["mesh"].faces.shape[0])

    def export(self, handle, target_faces, texture_size, out_path, progress):
        from trellis.utils import postprocessing_utils
        src = self.source_faces(handle)
        simplify = min(0.99, max(0.0, 1 - target_faces / max(src, 1)))
        progress("Simplifying mesh and baking texture", 0.5)
        glb = postprocessing_utils.to_glb(
            handle["gaussian"], handle["mesh"], simplify=simplify,
            texture_size=int(texture_size), verbose=False)
        progress("Saving", 0.95)
        glb.export(out_path)
        return ExportResult(faces=int(len(glb.faces)), vertices=int(len(glb.vertices)),
                            texture_size=int(texture_size))

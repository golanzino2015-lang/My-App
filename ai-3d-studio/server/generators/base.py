"""Common interface every 3D generator implements."""
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

Progress = Callable[[str, float], None]  # (stage text, 0..1)


@dataclass
class GenerateParams:
    prompt: str = ""
    image: Any = None            # PIL.Image or None
    seed: int = 1
    quality: str = "balanced"    # fast | balanced | high


@dataclass
class ExportResult:
    faces: int
    vertices: int
    texture_size: int
    extra: dict = field(default_factory=dict)


class Generator:
    id = "base"
    label = "Base"
    supports_text = False
    supports_image = False
    is_ai = True

    def available(self) -> tuple[bool, str]:
        return False, "not implemented"

    def generate(self, params: GenerateParams, progress: Progress) -> Any:
        """Run the model. Returns an opaque handle that export() understands."""
        raise NotImplementedError

    def source_faces(self, handle: Any) -> int:
        """Triangle count of the raw model before simplification."""
        raise NotImplementedError

    def export(self, handle: Any, target_faces: int, texture_size: int,
               out_path: str, progress: Progress) -> ExportResult:
        """Write a GLB at out_path with about target_faces triangles."""
        raise NotImplementedError

    def info(self) -> dict:
        ok, reason = self.available()
        return {
            "id": self.id, "label": self.label, "available": ok, "reason": reason,
            "text": self.supports_text, "image": self.supports_image, "ai": self.is_ai,
        }

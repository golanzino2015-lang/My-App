"""Demo generator: NOT AI.

Builds a procedural textured blob from the prompt so the whole website
(viewer, triangle count, texture resolution, export) can be tried on any
computer, without a GPU. The real AI generator is trellis_gen.py.
"""
import colorsys
import hashlib
import math

import numpy as np
import trimesh
from PIL import Image

from .base import ExportResult, GenerateParams, Generator

MAX_FACES = 400_000


class DemoGenerator(Generator):
    id = "demo"
    label = "Demo shapes (no AI, works on any computer)"
    supports_text = True
    supports_image = True
    is_ai = False

    def available(self):
        return True, ""

    def generate(self, params: GenerateParams, progress):
        progress("Building demo shape", 0.5)
        key = f"{params.prompt}|{params.seed}"
        if params.image is not None:
            key += "|" + hashlib.sha1(params.image.tobytes()).hexdigest()
        digest = hashlib.sha256(key.encode()).digest()
        rng = np.random.default_rng(int.from_bytes(digest[:8], "little"))
        avg = None
        if params.image is not None:
            avg = np.asarray(params.image.convert("RGB").resize((1, 1)), dtype=np.float32)[0, 0] / 255
        return {"rng_seed": int.from_bytes(digest[8:16], "little"),
                "bumps": rng.normal(size=(6, 4)), "hue": rng.random(), "avg": avg}

    def source_faces(self, handle):
        return MAX_FACES

    def export(self, handle, target_faces, texture_size, out_path, progress):
        progress("Meshing", 0.3)
        target = max(64, min(int(target_faces), MAX_FACES))
        lat = max(3, round((1 + math.sqrt(1 + target)) / 2))
        lon = 2 * lat

        v = np.linspace(0, 1, lat + 1)            # 0 = north pole
        u = np.linspace(0, 1, lon + 1)            # duplicate seam column for UVs
        uu, vv = np.meshgrid(u, v)
        theta, phi = uu * 2 * math.pi, vv * math.pi
        dirs = np.stack([np.sin(phi) * np.cos(theta), np.cos(phi), np.sin(phi) * np.sin(theta)], -1)

        r = np.ones_like(theta)
        for a, b, c, d in handle["bumps"]:
            fa, fb = 1 + int(abs(a) * 3) % 5, 1 + int(abs(b) * 3) % 4
            r += 0.08 * c * np.sin(fa * theta + d) * np.sin(fb * phi)
        verts = (dirs * r[..., None]).reshape(-1, 3)
        uv = np.stack([uu, 1 - vv], -1).reshape(-1, 2)

        idx = np.arange((lat + 1) * (lon + 1)).reshape(lat + 1, lon + 1)
        a, b = idx[:-1, :-1], idx[:-1, 1:]
        c, d = idx[1:, :-1], idx[1:, 1:]
        top = np.stack([a[0], d[0], c[0]], -1)                    # north cap
        bot = np.stack([a[-1], b[-1], d[-1]], -1)                 # south cap
        mid1 = np.stack([a[1:-1], b[1:-1], d[1:-1]], -1).reshape(-1, 3)
        mid2 = np.stack([a[1:-1], d[1:-1], c[1:-1]], -1).reshape(-1, 3)
        faces = np.concatenate([top, mid1, mid2, bot])          # counter-clockwise = outward

        progress("Painting texture", 0.6)
        tex = self._texture(handle, int(texture_size))
        mesh = trimesh.Trimesh(vertices=verts, faces=faces, process=False)
        mesh.visual = trimesh.visual.TextureVisuals(
            uv=uv, material=trimesh.visual.material.PBRMaterial(
                baseColorTexture=tex, metallicFactor=0.0, roughnessFactor=0.7))
        progress("Saving", 0.9)
        mesh.export(out_path, include_normals=True)
        return ExportResult(faces=len(faces), vertices=len(verts), texture_size=int(texture_size))

    def _texture(self, handle, size):
        y, x = np.mgrid[0:size, 0:size] / size
        rng = np.random.default_rng(handle["rng_seed"])
        f = rng.integers(3, 9, size=2)
        pattern = 0.5 + 0.25 * np.sin(2 * math.pi * f[0] * x) * np.sin(2 * math.pi * f[1] * y)
        pattern += 0.15 * np.sin(2 * math.pi * 40 * (x + y))      # fine detail shows texture res
        if handle["avg"] is not None:
            base = np.array(handle["avg"])
            alt = 1 - base
        else:
            base = np.array(colorsys.hsv_to_rgb(handle["hue"], 0.65, 0.9))
            alt = np.array(colorsys.hsv_to_rgb((handle["hue"] + 0.4) % 1, 0.5, 0.6))
        img = base * pattern[..., None] + alt * (1 - pattern[..., None])
        return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))

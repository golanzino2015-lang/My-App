from .base import ExportResult, GenerateParams, Generator
from .demo import DemoGenerator
from .trellis_gen import TrellisGenerator

GENERATORS: dict[str, Generator] = {g.id: g for g in (TrellisGenerator(), DemoGenerator())}

__all__ = ["GENERATORS", "ExportResult", "GenerateParams", "Generator"]

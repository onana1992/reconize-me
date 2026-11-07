"""Error Level Analysis. Recompression en mémoire, qualité 90."""

from __future__ import annotations

import io

import numpy as np
from PIL import Image

QUALITY = 90


def map_of(pixels: np.ndarray, quality: int = QUALITY) -> np.ndarray:
    """Différence absolue après un aller-retour JPEG. Le disque n'est pas touché."""
    source = pixels.astype(np.float32)
    buffer = io.BytesIO()
    Image.fromarray(pixels, "RGB").save(buffer, format="JPEG", quality=quality, subsampling=1)
    buffer.seek(0)
    with Image.open(buffer) as image:
        back = np.asarray(image.convert("RGB"), dtype=np.float32)
    return np.abs(source - back).mean(axis=2)

"""Local Binary Patterns. Voisinage 8, sans interpolation."""

from __future__ import annotations

import numpy as np
from PIL import Image

POINTS = 8
_WORK = 512


def codes(gray: np.ndarray) -> np.ndarray:
    """Code LBP 8-voisin, décalages entiers."""
    work = np.pad(gray.astype(np.int16), 1, mode="edge")
    center = work[1:-1, 1:-1]
    neighbors = (
        work[1:-1, 2:],
        work[:-2, 2:],
        work[:-2, 1:-1],
        work[:-2, :-2],
        work[1:-1, :-2],
        work[2:, :-2],
        work[2:, 1:-1],
        work[2:, 2:],
    )
    out = np.zeros(center.shape, dtype=np.uint16)
    for index, neighbor in enumerate(neighbors):
        out |= (neighbor >= center).astype(np.uint16) << index
    return out


def histogram(gray: np.ndarray) -> np.ndarray:
    bins = 2**POINTS
    counts, _ = np.histogram(codes(gray), bins=bins, range=(0, bins), density=True)
    return counts.astype(np.float64)


def spot_score(gray: np.ndarray) -> float:
    """Étalement de l'histogramme : une trame tachetée, pas un aplat."""
    if gray.size < 256:
        return 0.0
    sample = _shrink(gray)
    hist = histogram(sample)
    if float(hist.max()) <= 0:
        return 0.0
    entropy = float(-(hist[hist > 0] * np.log2(hist[hist > 0])).sum())
    return float(np.clip((entropy - 3.2) / 3.0, 0.0, 1.0))


def _shrink(gray: np.ndarray) -> np.ndarray:
    height, width = gray.shape
    long_side = max(height, width)
    if long_side <= _WORK:
        return gray
    scale = _WORK / long_side
    resized = Image.fromarray(gray.astype(np.uint8), "L").resize(
        (max(1, int(width * scale)), max(1, int(height * scale))),
        Image.Resampling.BILINEAR,
    )
    return np.asarray(resized, dtype=np.uint8)

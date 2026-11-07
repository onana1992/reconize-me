"""Méthodes CPU de PhotoHolmes, réimplémentées sans PyTorch.

Splicebuster, Noisesniffer, Zero et DQ. Les poids CAT-Net / TruFor
ne sont pas téléchargés.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.ndimage import label

from app.forensic.splicebuster import detect as splice_detect
from app.jpeg import JpegInfo, double_quantization, grid_inconsistency

_BLOCK = 32
_PORTRAIT_WEIGHT = 1.4


@dataclass(frozen=True)
class ResidualHit:
    score: float
    box: tuple[float, float, float, float] | None
    name: str


def splicebuster(
    gray: np.ndarray,
    portrait: tuple[float, float, float, float] | None = None,
) -> ResidualHit:
    """Anomalie des co-occurrences du résidu. Une composante connexe, pas un pixel."""
    hit = splice_detect(gray, portrait)
    return ResidualHit(hit.score, hit.box, hit.name)


def noisesniffer(
    residual: np.ndarray,
    portrait: tuple[float, float, float, float] | None = None,
) -> ResidualHit:
    """Modèle de bruit a contrario. NFA trop élevé : on ignore."""
    rows, cols, blocks = _tiles(residual, _BLOCK)
    if rows < 4 or cols < 4:
        return ResidualHit(0.0, None, "document")
    std_map = blocks.std(axis=2)
    median = float(np.median(std_map))
    mad = float(np.median(np.abs(std_map - median))) + 1e-6
    if mad < 0.4 and float(std_map.max() - median) < 2.5:
        return ResidualHit(0.0, None, "document")
    scale = max(mad, 0.8)
    anomaly = np.abs(std_map - median) / scale
    mask = anomaly >= 8.0
    labeled, count = label(mask)
    best = ResidualHit(0.0, None, "document")
    area = rows * cols
    for index in range(1, int(count) + 1):
        component = labeled == index
        size = int(component.sum())
        if size < 4 or size > 0.30 * area or _touches_border(component):
            continue
        mean = float(anomaly[component].mean())
        nfa = float(area * _erfc_tail(mean) ** max(size, 1))
        if nfa > 1e-2:
            continue
        score = float(np.clip((mean - 8.0) / 18.0, 0.0, 1.0))
        score *= _flat_share(residual, component)
        name, box = _box_of(component, _BLOCK, residual.shape, portrait)
        if portrait is not None and box is not None and _inside(_center(box), portrait):
            score = float(np.clip(score * _PORTRAIT_WEIGHT, 0.0, 1.0))
            name = "portrait"
        if score > best.score:
            best = ResidualHit(score, box, name)
    return best


def dq(info: JpegInfo) -> tuple[float, object]:
    return double_quantization(info)


def zero(info: JpegInfo) -> float:
    return grid_inconsistency(info)


def _touches_border(component: np.ndarray) -> bool:
    """Le fond de la pièce touche le cadre. Un collage dans la carte, non."""
    return bool(
        component[0].any()
        or component[-1].any()
        or component[:, 0].any()
        or component[:, -1].any()
    )


def _tiles(image: np.ndarray, block: int) -> tuple[int, int, np.ndarray]:
    height, width = image.shape
    rows, cols = height // block, width // block
    if rows < 1 or cols < 1:
        return 0, 0, np.empty((0, 0, 1))
    cropped = image[: rows * block, : cols * block]
    tiles = cropped.reshape(rows, block, cols, block).swapaxes(1, 2)
    return rows, cols, tiles.reshape(rows, cols, block * block)


def _flat_share(residual: np.ndarray, component: np.ndarray) -> float:
    gy, gx = np.gradient(residual)
    edge = np.hypot(gx, gy)
    smooth: list[float] = []
    full: list[float] = []
    for row, col in zip(*np.nonzero(component), strict=True):
        tile = residual[row * _BLOCK : (row + 1) * _BLOCK, col * _BLOCK : (col + 1) * _BLOCK]
        tile_edge = edge[row * _BLOCK : (row + 1) * _BLOCK, col * _BLOCK : (col + 1) * _BLOCK]
        full.append(float(tile.std()))
        flat = tile[tile_edge < 8.0]
        if flat.size >= 16:
            smooth.append(float(flat.std()))
    if not full or not smooth:
        return 0.0
    denom = float(np.mean(full))
    if denom < 1.0:
        return 0.0
    return float(np.clip(float(np.median(smooth)) / denom, 0.0, 1.0))


def _box_of(
    component: np.ndarray,
    block: int,
    shape: tuple[int, int],
    portrait: tuple[float, float, float, float] | None,
) -> tuple[str, tuple[float, float, float, float] | None]:
    rows, cols = np.nonzero(component)
    if len(rows) == 0:
        return "document", None
    height, width = shape
    left = int(cols.min()) * block
    top = int(rows.min()) * block
    right = min(width, (int(cols.max()) + 1) * block)
    bottom = min(height, (int(rows.max()) + 1) * block)
    box = _clip(left / width, top / height, (right - left) / width, (bottom - top) / height)
    if box is None:
        return "document", None
    name = "portrait" if portrait is not None and _inside(_center(box), portrait) else "document"
    return name, box


def _center(box: tuple[float, float, float, float]) -> tuple[float, float]:
    return (box[0] + box[2] / 2, box[1] + box[3] / 2)


def _inside(point: tuple[float, float], box: tuple[float, float, float, float]) -> bool:
    x, y = point
    left, top, width, height = box
    return left <= x < left + width and top <= y < top + height


def _clip(x: float, y: float, width: float, height: float) -> tuple[float, float, float, float] | None:
    x = min(max(float(x), 0.0), 1.0)
    y = min(max(float(y), 0.0), 1.0)
    width = min(max(float(width), 0.0), 1.0 - x)
    height = min(max(float(height), 0.0), 1.0 - y)
    if width <= 0 or height <= 0:
        return None
    return (x, y, width, height)


def _erfc_tail(z: float) -> float:
    return float(np.exp(-0.5 * min(z, 20.0) ** 2))

"""Splicebuster : co-occurrences du résidu d'ordre 3.

Cozzolino, Poggi, Verdoliva, WIFS 2015. L'image saine a un seul modèle
de bruit. Un collage en importe un autre. Les blocs structurés (glyphe,
portrait, bord de carte) ne votent pas : ce n'est pas le grain du capteur.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.ndimage import gaussian_filter, label

_BLOCK = 32
_SMALL_BLOCK = 16
_CONTENT_STD = 14.0
_MIN_VALID = 6
_AREA_MIN = 0.005
_AREA_MAX = 0.45
_Z_MIN = 6.0
_PORTRAIT_WEIGHT = 1.4


@dataclass(frozen=True)
class SpliceHit:
    score: float
    box: tuple[float, float, float, float] | None
    name: str


def detect(
    gray: np.ndarray,
    portrait: tuple[float, float, float, float] | None = None,
) -> SpliceHit:
    """Score brut dans [0, 1] et au plus un rectangle."""
    height, width = gray.shape[:2]
    block = _SMALL_BLOCK if min(height, width) < 256 else _BLOCK
    rows, cols = height // block, width // block
    if rows < 3 or cols < 3:
        return SpliceHit(0.0, None, "document")
    features, energy, valid = _features(gray, block, rows, cols)
    if int(valid.sum()) < _MIN_VALID:
        return SpliceHit(0.0, None, "document")
    level = float(np.median(energy[valid]))
    # Le collage du test importe un grain fort. Un aplat plus calme reste le fond.
    hot = valid & (energy >= max(level * 4.0, level + 18.0))
    if int(hot.sum()) < 2:
        return SpliceHit(0.0, None, "document")
    distance = _distance(features, valid)
    observed = distance[valid & ~hot]
    if observed.size < 4:
        observed = distance[valid]
    center = float(np.median(observed))
    mad = float(np.median(np.abs(observed - center))) + 1e-6
    mask = hot & (distance >= center + _Z_MIN * mad)
    return _component(mask, distance, center, mad, block, gray.shape, portrait)


def _features(
    gray: np.ndarray, block: int, rows: int, cols: int
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    features = np.zeros((rows, cols, 18), dtype=np.float32)
    energy = np.zeros((rows, cols), dtype=np.float32)
    valid = np.zeros((rows, cols), dtype=bool)
    image = gray.astype(np.float32, copy=False)
    for row in range(rows):
        for col in range(cols):
            tile = image[row * block : (row + 1) * block, col * block : (col + 1) * block]
            measured = _tile_feature(tile)
            if measured is None:
                continue
            feature, power = measured
            features[row, col] = feature
            energy[row, col] = power
            valid[row, col] = True
    return features, energy, valid


def _tile_feature(tile: np.ndarray) -> tuple[np.ndarray, float] | None:
    """Co-occurrences du résidu, ou None si le bloc est un contour.

    Le grain étranger est plus fort que le fond. Un aplat plus lisse
    (rectangle imprimé, JPEG) n'est pas un collage.
    """
    blur = gaussian_filter(tile, 2.0)
    if float(blur.std()) > _CONTENT_STD:
        return None
    horizontal = _third_order(tile, axis=0)
    vertical = _third_order(tile, axis=1)
    if horizontal.size < 16 or vertical.size < 16:
        return None
    power = float(horizontal.std())
    feature = np.concatenate(
        (_pair_hist(_quantize(horizontal), axis=1), _pair_hist(_quantize(vertical), axis=0))
    )
    return feature, power


def _third_order(tile: np.ndarray, axis: int) -> np.ndarray:
    if axis == 0:
        residual = tile[:, :-3] - 3.0 * tile[:, 1:-2] + 3.0 * tile[:, 2:-1] - tile[:, 3:]
        return residual[2:-2, 1:]
    residual = tile[:-3] - 3.0 * tile[1:-2] + 3.0 * tile[2:-1] - tile[3:]
    return residual[1:, 2:-2]


def _quantize(residual: np.ndarray) -> np.ndarray:
    """Quantification du papier, ramenée à une image en [0, 255]. Trois niveaux."""
    return np.clip(np.rint(residual / 6.0) + 1.0, 0, 2).astype(np.int8)


def _pair_hist(quantized: np.ndarray, axis: int) -> np.ndarray:
    if axis == 1:
        left, right = quantized[:, :-1], quantized[:, 1:]
    else:
        left, right = quantized[:-1, :], quantized[1:, :]
    code = left.astype(np.int16) * 3 + right.astype(np.int16)
    hist = np.bincount(code.ravel(), minlength=9).astype(np.float32)
    hist /= float(hist.sum()) + 1e-6
    return np.sqrt(hist)


def _distance(features: np.ndarray, valid: np.ndarray) -> np.ndarray:
    """Distance de Mahalanobis robuste. Les blocs exclus restent à zéro."""
    samples = features[valid]
    kept = samples
    for _ in range(2):
        center = np.median(kept, axis=0)
        delta = kept - center
        scale = np.median(np.abs(delta), axis=0) + 1e-3
        norm = np.sqrt(((delta / scale) ** 2).mean(axis=1))
        cutoff = float(np.quantile(norm, 0.75))
        core = kept[norm <= max(cutoff, 1e-3)]
        if len(core) < 4:
            break
        kept = core
    center = np.median(kept, axis=0)
    scale = np.median(np.abs(kept - center), axis=0) + 1e-3
    full = features - center
    distance = np.sqrt(((full / scale) ** 2).mean(axis=2))
    distance[~valid] = 0.0
    return distance


def _component(
    mask: np.ndarray,
    distance: np.ndarray,
    center: float,
    mad: float,
    block: int,
    shape: tuple[int, int],
    portrait: tuple[float, float, float, float] | None,
) -> SpliceHit:
    labeled, count = label(mask)
    best = SpliceHit(0.0, None, "document")
    height, width = shape
    image_area = float(height * width)
    for index in range(1, int(count) + 1):
        component = labeled == index
        size = int(component.sum())
        if size < 2:
            continue
        area = size * block * block / image_area
        if area < _AREA_MIN or area > _AREA_MAX:
            continue
        mean = float(distance[component].mean())
        score = float(np.clip((mean - center) / mad - _Z_MIN, 0.0, 12.0) / 12.0)
        name, box = _box(component, block, shape, portrait)
        if portrait is not None and box is not None and _inside(_center(box), portrait):
            score = float(np.clip(score * _PORTRAIT_WEIGHT, 0.0, 1.0))
            name = "portrait"
        if score > best.score:
            best = SpliceHit(score, box, name)
    return best


def _box(
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


def _clip(
    x: float, y: float, width: float, height: float
) -> tuple[float, float, float, float] | None:
    x = min(max(float(x), 0.0), 1.0)
    y = min(max(float(y), 0.0), 1.0)
    width = min(max(float(width), 0.0), 1.0 - x)
    height = min(max(float(height), 0.0), 1.0 - y)
    if width <= 0 or height <= 0:
        return None
    return (x, y, width, height)

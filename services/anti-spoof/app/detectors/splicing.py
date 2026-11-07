"""Splicing : copie interne, résidu de bruit, frontière de collage.

Le fond périodique ne compte pas. Le hint portrait pondère, il n'est pas obligatoire.
"""

from __future__ import annotations

from dataclasses import dataclass
from itertools import combinations

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter, label

MAX_SIDE = 1600
_RESIDUAL_BLOCK = 32
_COPY_BLOCK = 16
_COPY_STRIDE = 8
_COPY_MIN_SHIFT = 80.0
# Une copie forme un bloc. Des correspondances éparpillées sur le cadre n'en sont pas.
_COPY_DENSITY = 0.05
_FLAT_EDGE = 8.0
_PORTRAIT_WEIGHT = 1.4


@dataclass(frozen=True)
class Zone:
    name: str
    box: tuple[float, float, float, float]
    score: float


@dataclass(frozen=True)
class SplicingHit:
    score: float
    regions: list[Zone]


def detect(pixels: np.ndarray, portrait: tuple[float, float, float, float] | None = None) -> SplicingHit:
    """Score dans [0, 1] et au plus cinq rectangles, les plus scorés."""
    gray = _gray(_limit_side(pixels))
    copy_score, copy_zone = _internal_copy(gray, portrait)
    residual_score, residual_zone = _noise_residual(gray, portrait)
    boundary_score, boundary_zone = _paste_boundary(gray, residual_zone, portrait)
    score = max(copy_score, residual_score, boundary_score)
    regions = _strongest([copy_zone, residual_zone, boundary_zone])
    if score <= 0:
        return SplicingHit(score=0.0, regions=[])
    return SplicingHit(score=float(score), regions=regions)


def _gray(pixels: np.ndarray) -> np.ndarray:
    rgb = pixels.astype(np.float32)
    return 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]


def _limit_side(pixels: np.ndarray) -> np.ndarray:
    height, width = pixels.shape[:2]
    long_side = max(height, width)
    if long_side <= MAX_SIDE:
        return pixels
    scale = MAX_SIDE / long_side
    resized = Image.fromarray(pixels, "RGB").resize(
        (max(1, int(width * scale)), max(1, int(height * scale))),
        Image.Resampling.BILINEAR,
    )
    return np.asarray(resized, dtype=np.uint8)


def _internal_copy(
    gray: np.ndarray,
    portrait: tuple[float, float, float, float] | None,
) -> tuple[float, Zone | None]:
    """Blocs répétés hors d'un motif régulier. Une carte plate ou un fond périodique vaut 0."""
    if float(gray.std()) < 6.0:
        return 0.0, None
    block = _COPY_BLOCK
    stride = _COPY_STRIDE
    rows, cols, windows = _windows(gray, block, stride)
    if rows < 6 or cols < 6:
        return 0.0, None
    # Moyennes 4×4 : le motif basse fréquence survit au JPEG. Le bruit seul, non.
    reduced = windows.reshape(rows, cols, 4, 4, 4, 4).mean(axis=(3, 5))
    quantized = np.round(reduced / 8.0).astype(np.int16)
    grad_y, grad_x = np.gradient(gray)
    edge = np.hypot(grad_y, grad_x)
    edge_windows = np.lib.stride_tricks.sliding_window_view(edge, (block, block))[::stride, ::stride]
    sharpness = edge_windows.mean(axis=(2, 3))
    buckets: dict[bytes, list[tuple[int, int]]] = {}
    for row in range(rows):
        for col in range(cols):
            if float(sharpness[row, col]) > 5.0:
                continue
            buckets.setdefault(quantized[row, col].tobytes(), []).append((row, col))

    shifts: dict[tuple[int, int], list[tuple[int, int]]] = {}
    for positions in buckets.values():
        if not 2 <= len(positions) <= 8:
            continue
        for first, second in combinations(positions, 2):
            delta_row = second[0] - first[0]
            delta_col = second[1] - first[1]
            if np.hypot(delta_row * stride, delta_col * stride) < _COPY_MIN_SHIFT:
                continue
            if delta_row < 0 or (delta_row == 0 and delta_col < 0):
                delta_row, delta_col = -delta_row, -delta_col
                first = second
            shifts.setdefault((delta_row, delta_col), []).append(first)

    if not shifts:
        return 0.0, None
    (_shift, origins) = max(shifts.items(), key=lambda item: len(item[1]))
    count = len(origins)
    if count < 6 or count > 0.45 * rows * cols:
        return 0.0, None
    origin_rows = [point[0] for point in origins]
    origin_cols = [point[1] for point in origins]
    span = (max(origin_rows) - min(origin_rows) + 1) * (max(origin_cols) - min(origin_cols) + 1)
    if count / span < _COPY_DENSITY:
        return 0.0, None
    score = float(np.clip((count - 6) / 16.0, 0.0, 1.0))
    score = _boost(score, origins, stride, gray.shape, portrait)
    mask = np.zeros((rows, cols), dtype=bool)
    for row, col in origins:
        mask[row, col] = True
    zone = _zone_from_mask(mask, stride, gray.shape, score, portrait)
    return score, zone


def _noise_residual(
    gray: np.ndarray,
    portrait: tuple[float, float, float, float] | None,
) -> tuple[float, Zone | None]:
    """Résidu passe-haut. Une zone connexe, pas un pixel isolé, et pas toute l'image."""
    residual = gray - gaussian_filter(gray, sigma=1.2)
    edge = np.hypot(*np.gradient(gray))
    block = _RESIDUAL_BLOCK
    rows, cols, blocks = _tiles(residual, block)
    if rows < 4 or cols < 4:
        return 0.0, None
    std_map = blocks.std(axis=2)
    median = float(np.median(std_map))
    mad = float(np.median(np.abs(std_map - median)))
    if mad < 0.4 and float(std_map.max() - median) < 2.5:
        return 0.0, None
    scale = max(mad, 0.4)
    anomaly = np.abs(std_map - median) / scale
    mask = anomaly >= 8.0
    labeled, count = label(mask)
    best_score = 0.0
    best_mask: np.ndarray | None = None
    area = rows * cols
    for index in range(1, int(count) + 1):
        component = labeled == index
        size = int(component.sum())
        if size < 4 or size > 0.5 * area:
            continue
        weighted = anomaly[component].mean()
        if portrait is not None:
            centers = _component_centers(component, block, gray.shape)
            if any(_inside(point, portrait) for point in centers):
                weighted *= _PORTRAIT_WEIGHT
        score = float(np.clip((float(weighted) - 8.0) / 18.0, 0.0, 1.0))
        # Un glyphe ou un portrait imprimé est un contour. Le bruit de collage reste hors du contour.
        score *= _flat_share(residual, edge, component, block)
        if score > best_score:
            best_score = score
            best_mask = component
    if best_mask is None or best_score <= 0:
        return 0.0, None
    return best_score, _zone_from_mask(best_mask, block, gray.shape, best_score, portrait)


def _flat_share(residual: np.ndarray, edge: np.ndarray, component: np.ndarray, block: int) -> float:
    """Part du résidu qui survit hors des contours. Un contour imprimé vaut peu."""
    smooth_values: list[float] = []
    full_values: list[float] = []
    for row, col in zip(*np.nonzero(component), strict=True):
        tile = residual[row * block : (row + 1) * block, col * block : (col + 1) * block]
        tile_edge = edge[row * block : (row + 1) * block, col * block : (col + 1) * block]
        full_values.append(float(tile.std()))
        flat = tile[tile_edge < _FLAT_EDGE]
        if flat.size >= 16:
            smooth_values.append(float(flat.std()))
    if not full_values or not smooth_values:
        return 0.0
    full = float(np.mean(full_values))
    if full < 1.0:
        return 0.0
    return float(np.clip(float(np.median(smooth_values)) / full, 0.0, 1.0))


def _paste_boundary(
    gray: np.ndarray,
    residual_zone: Zone | None,
    portrait: tuple[float, float, float, float] | None,
) -> tuple[float, Zone | None]:
    """Contour fermé dans le gradient, qui ne suit pas le bord de la photo."""
    if residual_zone is None:
        return 0.0, None
    magnitude = np.hypot(*np.gradient(gray))
    height, width = gray.shape
    x, y, box_w, box_h = residual_zone.box
    left = int(x * width)
    top = int(y * height)
    right = int((x + box_w) * width)
    bottom = int((y + box_h) * height)
    if right - left < 8 or bottom - top < 8:
        return 0.0, None
    inset = magnitude[top:bottom, left:right]
    perimeter = np.concatenate(
        [inset[0, :], inset[-1, :], inset[:, 0], inset[:, -1]]
    )
    interior = inset[2:-2, 2:-2] if inset.shape[0] > 8 and inset.shape[1] > 8 else inset
    edge = float(perimeter.mean())
    inside = float(interior.mean()) + 1.0
    if edge < inside * 1.4:
        return 0.0, None
    score = float(np.clip((edge / inside - 1.4) / 3.0, 0.0, 1.0))
    if portrait is not None and _inside(((x + box_w / 2), (y + box_h / 2)), portrait):
        score = float(np.clip(score * _PORTRAIT_WEIGHT, 0.0, 1.0))
    name = _zone_name((x + box_w / 2, y + box_h / 2), portrait)
    return score, Zone(name=name, box=residual_zone.box, score=score)


def _windows(gray: np.ndarray, block: int, stride: int) -> tuple[int, int, np.ndarray]:
    height, width = gray.shape
    if height < block or width < block:
        return 0, 0, np.empty((0, 0, 1, 1))
    view = np.lib.stride_tricks.sliding_window_view(gray, (block, block))[::stride, ::stride]
    return view.shape[0], view.shape[1], view


def _tiles(image: np.ndarray, block: int) -> tuple[int, int, np.ndarray]:
    height, width = image.shape
    rows, cols = height // block, width // block
    if rows < 1 or cols < 1:
        return 0, 0, np.empty((0, 0, 1))
    cropped = image[: rows * block, : cols * block]
    tiles = cropped.reshape(rows, block, cols, block).swapaxes(1, 2)
    return rows, cols, tiles.reshape(rows, cols, block * block)


def _boost(
    score: float,
    origins: list[tuple[int, int]],
    block: int,
    shape: tuple[int, int],
    portrait: tuple[float, float, float, float] | None,
) -> float:
    if portrait is None or score <= 0:
        return score
    height, width = shape
    for row, col in origins:
        center = ((col + 0.5) * block / width, (row + 0.5) * block / height)
        if _inside(center, portrait):
            return float(np.clip(score * _PORTRAIT_WEIGHT, 0.0, 1.0))
    return score


def _component_centers(
    component: np.ndarray,
    block: int,
    shape: tuple[int, int],
) -> list[tuple[float, float]]:
    height, width = shape
    rows, cols = np.nonzero(component)
    return [
        ((float(col) + 0.5) * block / width, (float(row) + 0.5) * block / height)
        for row, col in zip(rows, cols, strict=True)
    ]


def _zone_from_mask(
    mask: np.ndarray,
    block: int,
    shape: tuple[int, int],
    score: float,
    portrait: tuple[float, float, float, float] | None,
) -> Zone | None:
    rows, cols = np.nonzero(mask)
    if len(rows) == 0:
        return None
    height, width = shape
    left = int(cols.min()) * block
    top = int(rows.min()) * block
    right = min(width, (int(cols.max()) + 1) * block)
    bottom = min(height, (int(rows.max()) + 1) * block)
    x = left / width
    y = top / height
    box_w = (right - left) / width
    box_h = (bottom - top) / height
    box = _clip_box(x, y, box_w, box_h)
    if box is None:
        return None
    center = (box[0] + box[2] / 2, box[1] + box[3] / 2)
    return Zone(name=_zone_name(center, portrait), box=box, score=float(score))


def _zone_name(
    center: tuple[float, float],
    portrait: tuple[float, float, float, float] | None,
) -> str:
    if portrait is not None and _inside(center, portrait):
        return "portrait"
    return "document"


def _inside(point: tuple[float, float], box: tuple[float, float, float, float]) -> bool:
    x, y = point
    left, top, width, height = box
    return left <= x < left + width and top <= y < top + height


def _clip_box(x: float, y: float, width: float, height: float) -> tuple[float, float, float, float] | None:
    x = min(max(float(x), 0.0), 1.0)
    y = min(max(float(y), 0.0), 1.0)
    width = min(max(float(width), 0.0), 1.0 - x)
    height = min(max(float(height), 0.0), 1.0 - y)
    if width <= 0 or height <= 0:
        return None
    return (x, y, width, height)


def _strongest(zones: list[Zone | None]) -> list[Zone]:
    kept = [zone for zone in zones if zone is not None and zone.score > 0]
    kept.sort(key=lambda zone: zone.score, reverse=True)
    unique: list[Zone] = []
    for zone in kept:
        if any(_same_box(zone.box, other.box) for other in unique):
            continue
        unique.append(zone)
        if len(unique) == 5:
            break
    return unique


def _same_box(left: tuple[float, float, float, float], right: tuple[float, float, float, float]) -> bool:
    return all(abs(a - b) < 0.02 for a, b in zip(left, right, strict=True))

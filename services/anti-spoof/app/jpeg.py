"""Tables JPEG d'origine, grille 8×8, double quantification locale.

Les coefficients DCT du flux ne sont pas exposés par Pillow. DQ s'appuie
sur les fantômes JPEG (qualité par bande) et sur l'origine de grille.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from app.detectors.quality import (
    Measured,
    Zone,
    anomalies,
    measure,
    roundtrip_errors,
    score_of,
    zone_for,
)

_GRID = 8
_TILE = 64


@dataclass(frozen=True)
class JpegInfo:
    tables: tuple[tuple[int, ...], ...]
    measured: Measured
    grid_origin: tuple[int, int]
    shifted_share: float
    ela: np.ndarray


def inspect(original: bytes, pixels: np.ndarray) -> JpegInfo:
    tables = quantization_tables(original)
    errors = roundtrip_errors(pixels)
    measured = measure(pixels, errors)
    origin, shifted = grid_shift(luma(pixels))
    return JpegInfo(
        tables=tables,
        measured=measured,
        grid_origin=origin,
        shifted_share=shifted,
        ela=errors[3],
    )


def quantization_tables(jpeg: bytes) -> tuple[tuple[int, ...], ...]:
    """Tables DQT du buffer d'origine. Vide si le parse échoue."""
    found: list[tuple[int, ...]] = []
    index = 0
    length = len(jpeg)
    while index < length - 4:
        if jpeg[index] != 0xFF:
            index += 1
            continue
        marker = jpeg[index + 1]
        if marker == 0xDA:
            break
        if marker in {0xD8, 0xD9} or 0xD0 <= marker <= 0xD7:
            index += 2
            continue
        size = (jpeg[index + 2] << 8) | jpeg[index + 3]
        if size < 2 or index + 2 + size > length:
            break
        if marker == 0xDB:
            payload = jpeg[index + 4 : index + 2 + size]
            cursor = 0
            while cursor + 65 <= len(payload):
                values = tuple(int(value) for value in payload[cursor + 1 : cursor + 65])
                found.append(values)
                cursor += 65
        index += 2 + size
    return tuple(found)


def grid_shift(gray: np.ndarray) -> tuple[tuple[int, int], float]:
    """Part des carreaux dont l'origine 8×8 n'est pas l'origine dominante."""
    height, width = gray.shape
    if height < _TILE or width < _TILE:
        return (0, 0), 0.0
    step = _TILE if max(height, width) <= 900 else _TILE * 2
    origins: list[tuple[int, int]] = []
    for top in range(0, height - _TILE + 1, step):
        for left in range(0, width - _TILE + 1, step):
            origins.append(_origin(gray[top : top + _TILE, left : left + _TILE]))
    if not origins:
        return (0, 0), 0.0
    counts: dict[tuple[int, int], int] = {}
    for origin in origins:
        counts[origin] = counts.get(origin, 0) + 1
    dominant = max(counts, key=counts.get)
    shifted = 1.0 - counts[dominant] / len(origins)
    if shifted > 0.45:
        return dominant, 0.0
    return dominant, float(shifted)


def double_quantization(info: JpegInfo) -> tuple[float, Zone | None]:
    """Écart local de qualité. Une seule qualité sur toute l'image vaut 0."""
    found = anomalies(info.measured, text_only=False)
    score = score_of(found)
    zone = zone_for(found, info.measured, "document", score)
    if score <= 0 or zone is None:
        return 0.0, None
    return score, zone


def grid_inconsistency(info: JpegInfo) -> float:
    """Zero : une deuxième origine de grille, locale."""
    return float(np.clip((info.shifted_share - 0.08) / 0.22, 0.0, 1.0))


def luma(pixels: np.ndarray) -> np.ndarray:
    rgb = pixels.astype(np.float32)
    return 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]


def _origin(tile: np.ndarray) -> tuple[int, int]:
    best = (0, 0)
    best_energy = -1.0
    for dy in range(_GRID):
        for dx in range(_GRID):
            vertical = float(np.mean(np.abs(np.diff(tile[dy::_GRID, :], axis=0))))
            horizontal = float(np.mean(np.abs(np.diff(tile[:, dx::_GRID], axis=1))))
            energy = vertical + horizontal
            if energy > best_energy:
                best_energy = energy
                best = (dx, dy)
    return best

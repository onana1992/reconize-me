"""Copie papier : trame de points, rosace sur les deux axes, grain plafonné.

Le pic étroit et déphasé de l'écran ne compte pas ici. Une photo d'impression
laisse la rosace sur une partie du cadre : elle compte quand même.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.ndimage import gaussian_filter

from app.detectors.spectrum import dot_residue, grain_of, impulsive, limit_side, peaks_of

_FRACTION = 0.006
_SPREAD = 0.15


@dataclass(frozen=True)
class Mark:
    name: str
    box: tuple[float, float, float, float]
    score: float


@dataclass(frozen=True)
class PrintedHit:
    score: float
    regions: list[Mark]


_GRID_MIN = 8
_GRID_MAX = 16
_GRID_TILE = 128
_GRID_STEP = 64
_GRID_SHARE = 0.20
_ROSETTE_TILE = 96
_ROSETTE_STEP = 64
# Une carte imprimée au milieu d'une feuille dépasse 0,55. Une grille qui glisse, non.
_ROSETTE_SHARE = 0.20


def detect(pixels: np.ndarray) -> PrintedHit:
    view = limit_side(pixels)
    gray = _luma(view)
    residue = gray - gaussian_filter(gray, 2.0)
    lattice = [peak for peak in peaks_of(view) if peak.fraction >= _FRACTION and peak.spread <= _SPREAD]
    # Un pic étroit et en phase est aussi celui d'une dalle. Seuls les points impulsifs comptent.
    halftone = 0.0
    if impulsive(residue) and len(lattice) >= 4:
        halftone = float(np.clip((len(lattice) - 3) / 3.0, 0.0, 1.0))
    score = max(halftone, grain_of(view), _offset_grid(view), _rosette(view))
    if score <= 0:
        return PrintedHit(score=0.0, regions=[])
    return PrintedHit(
        score=float(score),
        regions=[Mark(name="document", box=(0.0, 0.0, 1.0, 1.0), score=float(score))],
    )


def _rosette(pixels: np.ndarray) -> float:
    """Points jet d'encre : la période se retrouve en x, en y et en diagonale."""
    height, width = pixels.shape[:2]
    if height < _ROSETTE_TILE or width < _ROSETTE_TILE:
        return 0.0
    gray = _luma(pixels)
    residue = gray - gaussian_filter(gray, 2.0)
    votes = 0
    total = 0
    for top in range(0, height - _ROSETTE_TILE + 1, _ROSETTE_STEP):
        for left in range(0, width - _ROSETTE_TILE + 1, _ROSETTE_STEP):
            total += 1
            tile = residue[top : top + _ROSETTE_TILE, left : left + _ROSETTE_TILE]
            if dot_residue(tile):
                votes += 1
    # Un seul carreau peut venir d'un dégradé. Une impression en couvre plusieurs.
    if votes < 2:
        return 0.0
    return float(np.clip((votes / total) / _ROSETTE_SHARE, 0.0, 1.0))


def _offset_grid(pixels: np.ndarray) -> float:
    """Points en quinconce : une période sur les deux axes, 8 à 16 px."""
    height, width = pixels.shape[:2]
    if height < _GRID_TILE or width < _GRID_TILE:
        return 0.0
    gray = _luma(pixels)
    votes = 0
    total = 0
    for top in range(0, height - _GRID_TILE + 1, _GRID_STEP):
        for left in range(0, width - _GRID_TILE + 1, _GRID_STEP):
            tile = gray[top : top + _GRID_TILE, left : left + _GRID_TILE]
            total += 1
            horizontal = _axis_period(tile.mean(axis=0))
            vertical = _axis_period(tile.mean(axis=1))
            residue = tile - gaussian_filter(tile, 2.0)
            if horizontal and vertical and abs(horizontal - vertical) <= 3 and impulsive(residue):
                votes += 1
    if votes == 0:
        return 0.0
    return float(np.clip((votes / total) / _GRID_SHARE, 0.0, 1.0))


def _axis_period(profile: np.ndarray) -> int:
    centered = profile.astype(np.float64)
    centered = centered - centered.mean()
    if float(centered.std()) < 1.0:
        return 0
    spectrum = np.fft.rfft(centered * np.hanning(centered.size))
    corr = np.fft.irfft(np.abs(spectrum) ** 2, n=centered.size)
    corr = corr / (corr[0] + 1e-9)
    last = min(_GRID_MAX, corr.size - 2)
    best = 0
    best_value = 0.35
    for period in range(_GRID_MIN, last + 1):
        value = float(corr[period])
        if value < best_value:
            continue
        if value < corr[period - 1] or value < corr[period + 1]:
            continue
        if value - float(corr[period // 2]) < 0.25:
            continue
        best = period
        best_value = value
    return best


def _luma(pixels: np.ndarray) -> np.ndarray:
    rgb = pixels.astype(np.float32)
    return 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]

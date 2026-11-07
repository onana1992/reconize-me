"""Rejeu sur écran : pic spectral étroit, ou grille locale qui se décale.

Une grille colorée fine est renforcée par le déphasage RVB. Un moiré gris,
une seule période plus large, compte aussi. Une photo d'écran dont la période
change d'un endroit à l'autre ne fait pas un pic global : chaque carreau vote.

La texture d'affichage vit dans les hautes fréquences. Un passe-haut enlève
le modelé de la pièce (éclairage, glyphes larges) et laisse le battement
caméra × dalle. Pas de bandeau d'UI, pas de layout téléphone.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.ndimage import gaussian_filter

from app.artifacts import Artifacts
from app.detectors.spectrum import Peak, crop, dot_residue, halftone_residue, impulsive, limit_side, peaks_of

_FRACTION = 0.02
_COLOR_SPREAD = 0.45
_GRAY_SPREAD = 0.25
_CROWD = 4
_CROWD_FRACTION = 0.006
_PRINT_SPREAD = 0.15
_PERIOD_MAX = 64.0
_TILE = 128
_TILE_STEP = 64
_GRATING_MIN = 4
_GRATING_MAX = 12
_GRATING_CORR = 0.35
_GRATING_DIP = 0.25
# Période des glyphes, 16 px et plus, laissée dehors.
# Une part de 0,18 carreaux, comme la photo proche, dépasse 0,55.
_TILE_SHARE = 0.30
_HIGHPASS_SIGMA = 1.6


@dataclass(frozen=True)
class Mark:
    name: str
    box: tuple[float, float, float, float]
    score: float


@dataclass(frozen=True)
class ScreenHit:
    score: float
    regions: list[Mark]


def detect(
    artifacts: Artifacts,
    document: tuple[float, float, float, float] | None = None,
) -> ScreenHit:
    """Spectre dans le rectangle document, ou sur l'image entière."""
    box = document if document is not None else (0.0, 0.0, 1.0, 1.0)
    pixels = artifacts.pixels
    view = limit_side(crop(pixels, document) if document is not None else pixels)
    peaks = peaks_of(view, period_max=_PERIOD_MAX)
    score = _score(peaks)
    if score < 0.8:
        score = max(score, _tiles(view), _tiles(_highpass(view)))
    if score <= 0:
        return ScreenHit(score=0.0, regions=[])
    return ScreenHit(score=score, regions=[Mark(name="document", box=box, score=score)])


def _highpass(pixels: np.ndarray) -> np.ndarray:
    """Ôte le modelé large. Le battement dalle × capteur reste."""
    rgb = pixels.astype(np.float32)
    out = np.empty_like(rgb)
    for channel in range(3):
        plane = rgb[:, :, channel]
        out[:, :, channel] = plane - gaussian_filter(plane, _HIGHPASS_SIGMA)
    return np.clip(out + 128.0, 0, 255).astype(np.uint8)


def _score(peaks: list[Peak]) -> float:
    if not peaks:
        return 0.0
    return max(_colored(peaks), _gray(peaks))


def _colored(peaks: list[Peak]) -> float:
    tops = [peak for peak in peaks if peak.fraction >= _FRACTION and peak.spread >= _COLOR_SPREAD]
    if not tops:
        return 0.0
    top = max(tops, key=lambda peak: peak.fraction)
    base = float(np.clip((top.fraction - _FRACTION) / 0.06, 0.0, 1.0))
    if base <= 0:
        return 0.0
    return float(np.clip(base * (1.0 + 0.35 * top.spread), 0.0, 1.0))


def _gray(peaks: list[Peak]) -> float:
    """Un pic gris dominant. Quatre pics ou plus, c'est une trame d'impression."""
    quiet = [peak for peak in peaks if peak.spread <= _GRAY_SPREAD]
    if sum(peak.fraction >= _CROWD_FRACTION for peak in quiet) >= _CROWD:
        return 0.0
    tops = [peak for peak in quiet if peak.fraction >= _FRACTION]
    if not tops:
        return 0.0
    top = max(tops, key=lambda peak: peak.fraction)
    return float(np.clip((top.fraction - _FRACTION) / 0.04, 0.0, 1.0))


def _tiles(pixels: np.ndarray) -> float:
    """Part des carreaux où une seule grille se répète. Remise à l'échelle."""
    height, width = pixels.shape[:2]
    if height < _TILE or width < _TILE:
        return 0.0
    step = _TILE_STEP if max(height, width) <= 900 else _TILE_STEP * 2
    votes = 0
    total = 0
    for top in range(0, height - _TILE + 1, step):
        for left in range(0, width - _TILE + 1, step):
            tile = pixels[top : top + _TILE, left : left + _TILE]
            total += 1
            # Une cellule pleine (dalle) vote écran. Un point d'encre reste au papier.
            if _screen_cell(tile) or (_grating(tile) and not _lattice(tile) and not _print_lattice(tile)):
                votes += 1
    if votes == 0:
        return 0.0
    return float(np.clip((votes / total) / _TILE_SHARE, 0.0, 1.0))


def _screen_cell(tile: np.ndarray) -> bool:
    """Dalle : la période est en x, en y et en diagonale, et la cellule est pleine."""
    gray = _luma(tile)
    residue = gray - gaussian_filter(gray, 2.0)
    return halftone_residue(residue) and not impulsive(residue)


def _print_lattice(tile: np.ndarray) -> bool:
    """Points d'encre : les deux axes, la diagonale, et un résidu impulsif.

    Un carreau de 128 px mélange souvent la feuille et la carte. On regarde
    aussi des fenêtres de 96 px, celles qui portent la trame.
    """
    gray = _luma(tile)
    residues = (gray - float(gray.mean()), gray - gaussian_filter(gray, 2.0))
    for residue in residues:
        if _dot_windows(residue):
            return True
    return False


def _dot_windows(residue: np.ndarray) -> bool:
    if dot_residue(residue):
        return True
    if residue.shape[0] < 96 or residue.shape[1] < 96:
        return False
    corners = (
        (0, 0),
        (0, residue.shape[1] - 96),
        (residue.shape[0] - 96, 0),
        (residue.shape[0] - 96, residue.shape[1] - 96),
    )
    return any(dot_residue(residue[top : top + 96, left : left + 96]) for top, left in corners)


def _lattice(tile: np.ndarray) -> bool:
    peaks = peaks_of(tile)
    quiet = [peak for peak in peaks if peak.spread <= _PRINT_SPREAD and peak.fraction >= _CROWD_FRACTION]
    return len(quiet) >= _CROWD


def _grating(tile: np.ndarray) -> bool:
    gray = _luma(tile)
    return _profile_grating(gray.mean(axis=0)) or _profile_grating(gray.mean(axis=1))


def _profile_grating(profile: np.ndarray) -> bool:
    centered = profile.astype(np.float64)
    centered = centered - centered.mean()
    if float(centered.std()) < 1.0:
        return False
    spectrum = np.fft.rfft(centered * np.hanning(centered.size))
    corr = np.fft.irfft(np.abs(spectrum) ** 2, n=centered.size)
    corr = corr / (corr[0] + 1e-9)
    last = min(_GRATING_MAX, corr.size - 2)
    for period in range(_GRATING_MIN, last + 1):
        if corr[period] < _GRATING_CORR:
            continue
        if corr[period] < corr[period - 1] or corr[period] < corr[period + 1]:
            continue
        if corr[period] - corr[period // 2] < _GRATING_DIP:
            continue
        last_coarse = min(corr.size - 2, 48)
        if last_coarse > 24:
            coarse = float(np.max(corr[24 : last_coarse + 1]))
            if coarse >= float(corr[period]):
                continue
        return True
    return False


def _luma(pixels: np.ndarray) -> np.ndarray:
    rgb = pixels.astype(np.float32)
    return 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]

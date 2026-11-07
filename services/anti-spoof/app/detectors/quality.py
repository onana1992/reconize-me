"""Carte de qualité locale, en mémoire. Le JPEG d'origine n'est pas ré-écrit.

Chaque bande est la qualité JPEG dont l'aller-retour laisse le moins d'erreur.
Une seule qualité sur toutes les bandes actives vaut un écart nul.
"""

from __future__ import annotations

import io
from dataclasses import dataclass

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter1d

QUALITIES: tuple[int, ...] = (35, 55, 75, 90)
_STRIP = 32


@dataclass(frozen=True)
class Strip:
    y: int
    height: int
    activity: float
    text: bool
    errors: np.ndarray
    resample: float
    left: int
    right: int


@dataclass(frozen=True)
class Measured:
    width: int
    height: int
    strips: tuple[Strip, ...]


@dataclass(frozen=True)
class Zone:
    name: str
    box: tuple[float, float, float, float]
    score: float


def measure(pixels: np.ndarray, errors: np.ndarray | None = None) -> Measured:
    """Erreurs d'aller-retour par bande de 32 px. `pixels` reste en mémoire."""
    height, width = pixels.shape[:2]
    gray = _gray(pixels)
    gradient = np.abs(np.diff(gray, axis=1))
    row = gaussian_filter1d(gradient.mean(axis=1), 2.0)
    median = float(np.median(row))
    mad = float(np.median(np.abs(row - median))) + 1e-6
    text_level = median + 4.0 * mad
    residual = gray[:, 1:-1] - 0.5 * (gray[:, :-2] + gray[:, 2:])
    errors = _errors(pixels) if errors is None else errors
    strips: list[Strip] = []
    for top in range(0, height - _STRIP + 1, _STRIP):
        tile = gradient[top : top + _STRIP]
        activity = float(tile.mean()) if tile.size else 0.0
        column = tile.mean(axis=0) if tile.size else np.empty(0)
        left, right = _span(column, width)
        even = residual[top : top + _STRIP, 0::2]
        odd = residual[top : top + _STRIP, 1::2]
        count = min(even.shape[1], odd.shape[1])
        even_var = float(even[:, :count].var()) + 1e-6
        odd_var = float(odd[:, :count].var()) + 1e-6
        ratio = max(even_var, odd_var) / min(even_var, odd_var)
        strips.append(
            Strip(
                y=top,
                height=_STRIP,
                activity=activity,
                text=bool(row[top : top + _STRIP].max() > text_level),
                errors=errors[:, top : top + _STRIP].mean(axis=(1, 2)),
                resample=float(ratio),
                left=left,
                right=right,
            )
        )
    return Measured(width=width, height=height, strips=tuple(strips))


def anomalies(measured: Measured, text_only: bool) -> list[tuple[Strip, float]]:
    """Bandes dont la qualité s'écarte de la qualité dominante. Écart global : liste vide."""
    voters = [
        strip
        for strip in measured.strips
        if strip.activity >= 1.0
        and float(strip.errors.min()) > 0.05
        and float(strip.errors.max()) > 0.3
        and (strip.text or not text_only)
    ]
    if len(voters) < 3:
        return []
    votes = [int(np.argmin(strip.errors)) for strip in voters]
    dominant = int(np.bincount(votes, minlength=len(QUALITIES)).argmax())
    found: list[tuple[Strip, float]] = []
    for strip in voters:
        best = int(np.argmin(strip.errors))
        if best == dominant:
            continue
        margin = float(strip.errors[dominant] / (strip.errors[best] + 1e-6))
        if margin < 1.2:
            continue
        found.append((strip, margin))
    if not found or len(found) > 0.45 * len(voters):
        return []
    return found


def zone_for(found: list[tuple[Strip, float]], measured: Measured, name: str, score: float) -> Zone | None:
    """Un rectangle sur la bande la plus écartée, et ses voisines immédiates."""
    if not found or score <= 0:
        return None
    anchor = max(found, key=lambda item: item[1])[0]
    group = [strip for strip, _margin in found if abs(strip.y - anchor.y) <= _STRIP]
    top = min(strip.y for strip in group)
    bottom = max(strip.y + strip.height for strip in group)
    left = min(strip.left for strip in group)
    right = max(strip.right for strip in group)
    box = _clip(
        left / measured.width,
        top / measured.height,
        (right - left) / measured.width,
        (bottom - top) / measured.height,
    )
    if box is None:
        return None
    return Zone(name=name, box=box, score=score)


def score_of(found: list[tuple[Strip, float]]) -> float:
    if not found:
        return 0.0
    margin = max(margin for _strip, margin in found)
    return float(np.clip((margin - 1.12) / 0.28, 0.0, 1.0))


def roundtrip_errors(pixels: np.ndarray) -> np.ndarray:
    """Quatre plans d'erreur JPEG. Le dernier (qualité 90) sert d'ELA."""
    return _errors(pixels)


def _errors(pixels: np.ndarray) -> np.ndarray:
    source = pixels.astype(np.float32)
    planes = []
    for quality in QUALITIES:
        buffer = io.BytesIO()
        Image.fromarray(pixels, "RGB").save(buffer, format="JPEG", quality=quality, subsampling=1)
        buffer.seek(0)
        with Image.open(buffer) as image:
            back = np.asarray(image.convert("RGB"), dtype=np.float32)
        planes.append(np.abs(source - back).mean(axis=2))
    return np.stack(planes)


def _gray(pixels: np.ndarray) -> np.ndarray:
    rgb = pixels.astype(np.float32)
    return 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]


def _span(column: np.ndarray, width: int) -> tuple[int, int]:
    if column.size == 0:
        return 0, width
    peak = float(column.max())
    if peak <= 0:
        return 0, width
    hot = np.flatnonzero(column >= peak * 0.25)
    if hot.size == 0:
        return 0, width
    return int(hot[0]), min(width, int(hot[-1]) + 2)


def _clip(x: float, y: float, width: float, height: float) -> tuple[float, float, float, float] | None:
    x = min(max(float(x), 0.0), 1.0)
    y = min(max(float(y), 0.0), 1.0)
    width = min(max(float(width), 0.0), 1.0 - x)
    height = min(max(float(height), 0.0), 1.0 - y)
    if width <= 0 or height <= 0:
        return None
    return (x, y, width, height)

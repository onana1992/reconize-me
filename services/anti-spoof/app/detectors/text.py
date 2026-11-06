"""Altération de texte : ELA local, double quantification, résidu de rééchantillonnage.

Les hints mrz, birth_date, document_number, surname et given_names pondèrent.
Aucune chaîne n'est lue.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from app.detectors.quality import Measured, Strip, Zone, anomalies, score_of, zone_for

_HINTS = {"mrz", "birth_date", "document_number", "surname", "given_names"}
_WEIGHT = 1.4
# Qualité 75 : troisième entrée de QUALITIES.
_ELA = 2


@dataclass(frozen=True)
class TextHit:
    score: float
    regions: list[Zone]


def detect(
    measured: Measured,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None = None,
) -> TextHit:
    """Score dans [0, 1]. Le hint augmente un écart déjà là. Il n'en crée pas."""
    quality_score, quality_zone = _quality(measured, hints)
    ela_score, ela_zone = _ela(measured, hints)
    resample_score, resample_zone = _resample(measured, hints)
    score = max(quality_score, ela_score, resample_score)
    zones = [zone for zone in (quality_zone, ela_zone, resample_zone) if zone is not None and zone.score >= 0.2]
    zones.sort(key=lambda zone: zone.score, reverse=True)
    if score <= 0:
        return TextHit(score=0.0, regions=[])
    return TextHit(score=float(score), regions=zones[:5])


def _quality(
    measured: Measured,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[float, Zone | None]:
    found = anomalies(measured, text_only=True)
    score = score_of(found)
    return _finish(found, measured, score, hints)


def _ela(
    measured: Measured,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[float, Zone | None]:
    """Erreur à qualité fixe, plus haute sur une petite zone que sur le texte voisin."""
    lines = [strip for strip in measured.strips if strip.text and strip.activity >= 1.0]
    if len(lines) < 3:
        return 0.0, None
    levels = np.array([float(strip.errors[_ELA]) for strip in lines])
    center = float(np.median(levels))
    if center <= 0.05:
        return 0.0, None
    hot = [(strip, float(level / center)) for strip, level in zip(lines, levels, strict=True) if level > center * 1.8]
    if not hot or len(hot) > 0.4 * len(lines):
        return 0.0, None
    score = float(np.clip((max(margin for _strip, margin in hot) - 1.8) / 2.0, 0.0, 1.0))
    return _finish(hot, measured, score, hints)


def _resample(
    measured: Measured,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[float, Zone | None]:
    """Déséquilibre pair/impair d'une ligne, absent des lignes voisines."""
    lines = [strip for strip in measured.strips if strip.text and strip.activity >= 1.0]
    if len(lines) < 3:
        return 0.0, None
    center = float(np.median([strip.resample for strip in lines]))
    hot = [
        (strip, strip.resample)
        for strip in lines
        if strip.resample > 2.4 and strip.resample > center * 1.8
    ]
    if not hot or len(hot) > 0.4 * len(lines):
        return 0.0, None
    score = float(np.clip((max(margin for _strip, margin in hot) - 2.4) / 2.5, 0.0, 1.0))
    return _finish(hot, measured, score, hints)


def _finish(
    found: list[tuple[Strip, float]],
    measured: Measured,
    score: float,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[float, Zone | None]:
    if score <= 0 or not found:
        return 0.0, None
    anchor = max(found, key=lambda item: item[1])[0]
    center = ((anchor.left + anchor.right) / 2 / measured.width, (anchor.y + anchor.height / 2) / measured.height)
    name = _name(center, hints)
    if name != "document":
        score = float(np.clip(score * _WEIGHT, 0.0, 1.0))
    zone = zone_for(found, measured, name, score)
    if zone is None:
        return 0.0, None
    return score, zone


def _name(
    center: tuple[float, float],
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> str:
    if not hints:
        return "document"
    x, y = center
    for name, box in hints:
        if name not in _HINTS:
            continue
        left, top, width, height = box
        if left <= x < left + width and top <= y < top + height:
            return name
    return "document"

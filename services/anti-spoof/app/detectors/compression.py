"""Incohérence de compression. Un ré-encodage de tout le fichier reste à 0."""

from __future__ import annotations

from dataclasses import dataclass

from app.detectors.quality import Measured, Zone, anomalies, score_of, zone_for


@dataclass(frozen=True)
class CompressionHit:
    score: float
    regions: list[Zone]


def detect(measured: Measured) -> CompressionHit:
    """Écart local de qualité. La carte uniforme vaut 0."""
    found = anomalies(measured, text_only=False)
    score = score_of(found)
    zone = zone_for(found, measured, "document", score)
    if score <= 0 or zone is None:
        return CompressionHit(score=0.0, regions=[])
    return CompressionHit(score=score, regions=[zone])

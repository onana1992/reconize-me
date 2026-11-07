"""Incohérence de compression. Un ré-encodage de tout le fichier reste à 0."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.ndimage import label

from app.artifacts import Artifacts
from app.detectors.quality import Zone
from app.forensic.photoholmes import dq, zero


@dataclass(frozen=True)
class CompressionHit:
    score: float
    regions: list[Zone]


def detect(artifacts: Artifacts) -> CompressionHit:
    """Écart local de qualité ou de grille. La carte uniforme vaut 0."""
    quality_score, quality_zone = dq(artifacts.jpeg)
    grid_score = zero(artifacts.jpeg)
    ela_score, ela_zone = _ela_hotspot(artifacts, quality_score > 0 or grid_score > 0)
    score = max(quality_score, grid_score, ela_score)
    zone = quality_zone or ela_zone
    if grid_score >= score and score > 0 and zone is None:
        zone = Zone(name="document", box=(0.0, 0.0, 1.0, 1.0), score=score)
    if score <= 0 or zone is None:
        return CompressionHit(score=0.0, regions=[])
    return CompressionHit(score=float(score), regions=[zone])


def _ela_hotspot(artifacts: Artifacts, allowed: bool) -> tuple[float, Zone | None]:
    """Localise. Seul, le score reste 0 : une texture vive n'est pas une double compression."""
    if not allowed:
        return 0.0, None
    ela = artifacts.ela
    block = 32
    height, width = ela.shape
    rows, cols = height // block, width // block
    if rows < 4 or cols < 4:
        return 0.0, None
    tiles = ela[: rows * block, : cols * block].reshape(rows, block, cols, block).swapaxes(1, 2)
    means = tiles.mean(axis=(2, 3))
    center = float(np.median(means))
    if center <= 0.05:
        return 0.0, None
    mask = means > center * 1.8
    if not np.any(mask) or float(mask.mean()) > 0.4:
        return 0.0, None
    labeled, count = label(mask)
    best_score = 0.0
    best = None
    area = rows * cols
    for index in range(1, int(count) + 1):
        component = labeled == index
        size = int(component.sum())
        if size < 2 or size > 0.4 * area:
            continue
        margin = float(means[component].max() / center)
        score = float(np.clip((margin - 1.8) / 2.0, 0.0, 0.54))
        if score > best_score:
            best_score = score
            ys, xs = np.nonzero(component)
            left = int(xs.min()) * block / width
            top = int(ys.min()) * block / height
            box_w = (int(xs.max()) + 1) * block / width - left
            box_h = (int(ys.max()) + 1) * block / height - top
            best = Zone(name="document", box=(left, top, box_w, box_h), score=score)
    return best_score, best

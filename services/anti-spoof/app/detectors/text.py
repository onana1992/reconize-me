"""Altération de texte : glyphes, ELA local, double quantification.

Les hints mrz, birth_date, document_number, surname et given_names pondèrent.
Aucune chaîne n'est lue ni renvoyée.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from app.artifacts import Artifacts
from app.detectors.quality import Measured, Strip, Zone, anomalies, score_of, zone_for
from app.forensic.ocr_boxes import Glyph

_HINTS = {"mrz", "birth_date", "document_number", "surname", "given_names"}
_WEIGHT = 1.4
_ELA = 2


@dataclass(frozen=True)
class TextHit:
    score: float
    regions: list[Zone]


def detect(
    artifacts: Artifacts,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None = None,
) -> TextHit:
    """Score dans [0, 1]. Le hint augmente un écart déjà là. Il n'en crée pas."""
    measured = artifacts.jpeg.measured
    quality_score, quality_zone = _quality(measured, hints)
    ela_score, ela_zone = _ela(measured, hints)
    resample_score, resample_zone = _resample(measured, hints)
    glyph_ela, glyph_ela_zone = _glyph_ela(artifacts.glyphs, hints)
    glyph_align, glyph_align_zone = _glyph_align(artifacts.glyphs, hints)
    glyph_stroke, glyph_stroke_zone = _glyph_stroke(artifacts.glyphs, hints)
    score = max(
        quality_score,
        ela_score,
        resample_score,
        glyph_ela,
        glyph_align,
        glyph_stroke,
    )
    zones = [
        zone
        for zone in (
            quality_zone,
            ela_zone,
            resample_zone,
            glyph_ela_zone,
            glyph_align_zone,
            glyph_stroke_zone,
        )
        if zone is not None and zone.score >= 0.2
    ]
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


def _glyph_ela(
    glyphs: tuple[Glyph, ...],
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[float, Zone | None]:
    lines = _lines(glyphs)
    if len(lines) < 3:
        return 0.0, None
    hottest: list[tuple[list[Glyph], float]] = []
    for line in lines:
        if len(line) < 4:
            continue
        levels = np.array([glyph.ela for glyph in line])
        center = float(np.median(levels))
        if center <= 0.05:
            continue
        hot = [glyph for glyph in line if glyph.ela > center * 1.8]
        if not hot or len(hot) > 0.4 * len(line):
            continue
        hottest.append((hot, max(glyph.ela / center for glyph in hot)))
    if not hottest or len(hottest) > 0.4 * len(lines):
        return 0.0, None
    hot, margin = max(hottest, key=lambda item: item[1])
    score = float(np.clip((margin - 1.8) / 2.0, 0.0, 1.0))
    score, named = _boost(score, hot[0], hints)
    return score, _glyph_zone(hot, named, score)


def _glyph_align(
    glyphs: tuple[Glyph, ...],
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[float, Zone | None]:
    lines = _lines(glyphs)
    best = 0.0
    zone: Zone | None = None
    for line in lines:
        if len(line) < 4:
            continue
        xs = np.array([glyph.cx for glyph in line])
        bottoms = np.array([glyph.box[1] + glyph.box[3] for glyph in line])
        heights = np.array([glyph.box[3] for glyph in line])
        if float(np.std(heights) / (np.median(heights) + 1e-6)) > 0.2:
            continue
        slope, intercept = np.polyfit(xs, bottoms, 1)
        residual = np.abs(bottoms - (slope * xs + intercept))
        sigma = float(np.std(residual)) + 1e-6
        if sigma < 1e-4:
            continue
        index = int(np.argmax(residual))
        if residual[index] < 4.5 * sigma:
            continue
        score = float(np.clip((residual[index] / sigma - 4.5) / 2.0, 0.0, 1.0))
        score, named = _boost(score, line[index], hints)
        if score > best:
            best = score
            zone = _glyph_zone([line[index]], named, score)
    return best, zone


def _glyph_stroke(
    glyphs: tuple[Glyph, ...],
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[float, Zone | None]:
    lines = _lines(glyphs)
    best = 0.0
    zone: Zone | None = None
    for line in lines:
        if len(line) < 8:
            continue
        heights = np.array([glyph.box[3] for glyph in line])
        if float(np.std(heights) / (np.median(heights) + 1e-6)) > 0.2:
            continue
        widths = np.array([glyph.stroke for glyph in line])
        median = float(np.median(widths))
        mad = float(np.median(np.abs(widths - median))) + 1e-6
        # Un MAD microscopique, c'est la fonte. Un glyphe retouché change vraiment l'épaisseur.
        scale = max(3.5 * mad, 0.35 * median)
        index = int(np.argmax(np.abs(widths - median)))
        if abs(widths[index] - median) < scale:
            continue
        score = float(np.clip((abs(widths[index] - median) / scale - 1.0) / 2.0, 0.0, 1.0))
        score, named = _boost(score, line[index], hints)
        if score > best:
            best = score
            zone = _glyph_zone([line[index]], named, score)
    return best, zone


def _lines(glyphs: tuple[Glyph, ...]) -> list[list[Glyph]]:
    if not glyphs:
        return []
    ordered = sorted(glyphs, key=lambda glyph: glyph.cy)
    lines: list[list[Glyph]] = []
    current = [ordered[0]]
    for glyph in ordered[1:]:
        if abs(glyph.cy - current[-1].cy) < 0.04:
            current.append(glyph)
        else:
            lines.append(current)
            current = [glyph]
    lines.append(current)
    return lines


def _boost(
    score: float,
    glyph: Glyph,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[float, str]:
    name = glyph.name if glyph.name in _HINTS else _name((glyph.cx, glyph.cy), hints)
    if name != "document":
        score = float(np.clip(score * _WEIGHT, 0.0, 1.0))
    return score, name


def _glyph_zone(glyphs: list[Glyph], name: str, score: float) -> Zone | None:
    if not glyphs:
        return None
    left = min(glyph.box[0] for glyph in glyphs)
    top = min(glyph.box[1] for glyph in glyphs)
    right = max(glyph.box[0] + glyph.box[2] for glyph in glyphs)
    bottom = max(glyph.box[1] + glyph.box[3] for glyph in glyphs)
    return Zone(name=name, box=(left, top, right - left, bottom - top), score=score)


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

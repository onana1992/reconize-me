"""Ordonnancement. anti-spoof-2 : extracteurs forensiques, calibration forensic-1."""

from __future__ import annotations

import threading

from app.artifacts import Artifacts, build as build_artifacts
from app.calibration import FUSION_VERSION, apply
from app.contract import ALERTS, Alert, Assessment, Hints, Region
from app.detectors.compression import detect as detect_compression
from app.detectors.printed import detect as detect_printed
from app.detectors.screen import detect as detect_screen
from app.detectors.splicing import detect
from app.detectors.text import detect as detect_text
from app.preprocess import Prepared, prepare

ENGINE_VERSION = "anti-spoof-2"
DEADLINE_S = 30.0
_TEXT_HINTS = {"mrz", "birth_date", "document_number", "surname", "given_names"}


class DeadlineExceeded(Exception):
    """La mesure dépasse 30 s. Pas de score à 0 à la place."""


def assess(image: bytes, hints: Hints | None = None) -> Assessment:
    """Prépare l'image, puis mesure les cinq alertes dans le délai.

    Image trop petite ou trop lourde : scores null, détecteurs non appelés.
    Une exception ou un dépassement sort d'ici : pas de score partiel, pas un zéro.
    """
    prepared = prepare(image)
    if not prepared.usable or prepared.pixels is None:
        return _unusable()
    return _bounded(lambda: _score(image, prepared, hints))


def _score(original: bytes, prepared: Prepared, hints: Hints | None) -> Assessment:
    pixels = prepared.pixels
    if pixels is None:
        return _unusable()
    artifacts = build_artifacts(original, pixels, _text_hints(hints))
    return _alerts(artifacts, hints)


def _alerts(artifacts: Artifacts, hints: Hints | None) -> Assessment:
    hit = detect(artifacts, _portrait(hints))
    text = detect_text(artifacts, _text_hints(hints))
    compression = detect_compression(artifacts)
    screen = detect_screen(artifacts, _document(hints))
    printed = detect_printed(artifacts)
    scores = {code: 0.0 for code, _family in ALERTS}
    scores["splicing"] = apply("splicing", hit.score)
    scores["text_alteration"] = apply("text_alteration", text.score)
    scores["compression_inconsistency"] = apply("compression_inconsistency", compression.score)
    scores["screen_replay"] = apply("screen_replay", screen.score)
    scores["printed_copy"] = apply("printed_copy", printed.score)
    regions = {
        "splicing": _regions("splicing", hit.regions),
        "text_alteration": _regions("text_alteration", text.regions),
        "compression_inconsistency": _regions("compression_inconsistency", compression.regions),
        "screen_replay": _regions("screen_replay", screen.regions),
        "printed_copy": _regions("printed_copy", printed.regions),
    }
    _separate_presentation(scores, regions)
    return _measured(scores, regions)


def _separate_presentation(scores: dict[str, float], regions: dict[str, list[Region]]) -> None:
    """Une dalle et une trame ne portent pas les deux scores au-dessus du seuil."""
    screen = scores["screen_replay"]
    printed = scores["printed_copy"]
    if screen < 0.55 or printed < 0.55:
        return
    if screen >= printed:
        scores["printed_copy"] = min(printed, 0.54)
        regions["printed_copy"] = []
    else:
        scores["screen_replay"] = min(screen, 0.54)
        regions["screen_replay"] = []


def _bounded(fn):
    box: dict[str, object] = {}

    def run() -> None:
        try:
            box["value"] = fn()
        except Exception as exc:
            box["error"] = exc

    worker = threading.Thread(target=run, daemon=True)
    worker.start()
    worker.join(DEADLINE_S)
    if worker.is_alive():
        raise DeadlineExceeded()
    if "error" in box:
        raise box["error"]
    return box["value"]


def _regions(code: str, zones: list) -> list[Region]:
    return [
        Region(name=zone.name, box=list(zone.box), score=apply(code, zone.score)) for zone in zones
    ]


def _text_hints(
    hints: Hints | None,
) -> list[tuple[str, tuple[float, float, float, float]]] | None:
    if hints is None or not hints.rois:
        return None
    found = []
    for roi in hints.rois:
        if roi.name not in _TEXT_HINTS:
            continue
        x, y, width, height = roi.box
        found.append((roi.name, (float(x), float(y), float(width), float(height))))
    return found or None


def _document(hints: Hints | None) -> tuple[float, float, float, float] | None:
    if hints is None or not hints.rois:
        return None
    for roi in hints.rois:
        if roi.name == "document":
            x, y, width, height = roi.box
            return (float(x), float(y), float(width), float(height))
    return None


def _portrait(hints: Hints | None) -> tuple[float, float, float, float] | None:
    if hints is None or not hints.rois:
        return None
    for roi in hints.rois:
        if roi.name == "portrait":
            x, y, width, height = roi.box
            return (float(x), float(y), float(width), float(height))
    return None


def _unusable() -> Assessment:
    alerts = [Alert(code=code, family=family, score=None, regions=[]) for code, family in ALERTS]
    return Assessment(
        engine_version=ENGINE_VERSION,
        fusion_version=FUSION_VERSION,
        forensic_usable=False,
        authenticity_score=None,
        tampering_score=None,
        presentation_score=None,
        alerts=alerts,
    )


def _measured(scores: dict[str, float], regions: dict[str, list[Region]]) -> Assessment:
    alerts = [
        Alert(code=code, family=family, score=scores[code], regions=regions.get(code, []))
        for code, family in ALERTS
    ]
    tampering = max(
        scores["splicing"],
        scores["text_alteration"],
        scores["compression_inconsistency"],
    )
    presentation = max(scores["screen_replay"], scores["printed_copy"])
    return Assessment(
        engine_version=ENGINE_VERSION,
        fusion_version=FUSION_VERSION,
        forensic_usable=True,
        authenticity_score=1.0 - max(tampering, presentation),
        tampering_score=tampering,
        presentation_score=presentation,
        alerts=alerts,
    )

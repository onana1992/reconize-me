"""Ordonnancement. anti-spoof-1 : les cinq détecteurs mesurent. La fusion est l'identité."""

from __future__ import annotations

import threading

from app.contract import ALERTS, Alert, Assessment, Hints, Region
from app.detectors.compression import detect as detect_compression
from app.detectors.printed import detect as detect_printed
from app.detectors.quality import measure
from app.detectors.screen import detect as detect_screen
from app.detectors.splicing import detect
from app.detectors.text import detect as detect_text
from app.preprocess import Prepared, prepare

ENGINE_VERSION = "anti-spoof-1"
FUSION_VERSION = "identity"
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
    return _bounded(lambda: _score(prepared, hints))


def _score(prepared: Prepared, hints: Hints | None) -> Assessment:
    pixels = prepared.pixels
    if pixels is None:
        return _unusable()
    hit = detect(pixels, _portrait(hints))
    measured = measure(pixels)
    text = detect_text(measured, _text_hints(hints))
    compression = detect_compression(measured)
    screen = detect_screen(pixels, _document(hints))
    printed = detect_printed(pixels)
    scores = {code: 0.0 for code, _family in ALERTS}
    scores["splicing"] = hit.score
    scores["text_alteration"] = text.score
    scores["compression_inconsistency"] = compression.score
    scores["screen_replay"] = screen.score
    scores["printed_copy"] = printed.score
    regions = {
        "splicing": _regions(hit.regions),
        "text_alteration": _regions(text.regions),
        "compression_inconsistency": _regions(compression.regions),
        "screen_replay": _regions(screen.regions),
        "printed_copy": _regions(printed.regions),
    }
    return _measured(scores, regions)


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


def _regions(zones: list) -> list[Region]:
    return [Region(name=zone.name, box=list(zone.box), score=zone.score) for zone in zones]


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

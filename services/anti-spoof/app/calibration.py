"""Calibration figée. forensic-1.json : logistique ancrée par alerte.

Le score 0 reste 0 (aucune trace). Le score 1 reste 1. Entre les deux,
la courbe est une logistique. Sans fichier, chaque alerte est l'identité.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

_PATH = Path(__file__).with_name("models") / "forensic-1.json"
_MODEL = json.loads(_PATH.read_text(encoding="utf-8")) if _PATH.is_file() else None

FUSION_VERSION = "forensic-1" if _MODEL else "identity"


def apply(code: str, score: float) -> float:
    """Score calibré dans [0, 1]. Une absence de trace reste 0."""
    raw = float(score)
    if raw <= 0.0 or _MODEL is None:
        return 0.0 if raw <= 0.0 else raw
    spec = _MODEL["alerts"].get(code)
    if not spec or spec.get("family") == "identity":
        return float(np.clip(raw, 0.0, 1.0))
    return anchored(float(spec["a"]), float(spec["b"]), raw)


def anchored(a: float, b: float, raw: float) -> float:
    """Logistique qui passe par (0, 0) et (1, 1). b = 0 : identité."""
    x = float(np.clip(raw, 0.0, 1.0))
    if x == 0.0 or b <= 1e-6:
        return x
    if x == 1.0:
        return 1.0
    low = _sigmoid(a)
    high = _sigmoid(a + b)
    span = high - low
    if span <= 1e-6:
        return x
    return float(np.clip((_sigmoid(a + b * x) - low) / span, 0.0, 1.0))


def _sigmoid(value: float) -> float:
    clipped = float(np.clip(value, -40.0, 40.0))
    return 1.0 / (1.0 + float(np.exp(-clipped)))

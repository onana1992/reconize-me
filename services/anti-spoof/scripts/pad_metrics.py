"""ISO/IEC 30107-3 : APCER, BPCER, ACER, EER.

Score haut = présentation d'attaque. Les seuils 0,55 et 0,80 restent ceux de auth-1.
"""

from __future__ import annotations

from typing import Iterable

REVIEW_AT = 0.55
DECLINE_AT = 0.80
TARGET_BPCER = 0.05
TARGET_APCER = 0.10
ALERTS = (
    "splicing",
    "text_alteration",
    "compression_inconsistency",
    "screen_replay",
    "printed_copy",
)


def rates(bona: list[float], attack: list[float], threshold: float) -> tuple[float, float]:
    """APCER, BPCER à un seuil. Listes vides → (nan, nan) via ZeroDivisionError évité : None."""
    if not bona or not attack:
        raise ValueError("bona and attack")
    apcer = sum(score < threshold for score in attack) / len(attack)
    bpcer = sum(score >= threshold for score in bona) / len(bona)
    return float(apcer), float(bpcer)


def acer(apcer: float, bpcer: float) -> float:
    return (apcer + bpcer) / 2.0


def eer(bona: list[float], attack: list[float]) -> float | None:
    """Point où APCER ≈ BPCER, interpolé sur la courbe DET."""
    if not bona or not attack:
        return None
    previous: tuple[float, float, float] | None = None
    best: tuple[float, float] | None = None
    for threshold in _thresholds(bona, attack):
        apcer, bpcer = rates(bona, attack, threshold)
        gap = abs(apcer - bpcer)
        if best is None or gap < best[0]:
            best = (gap, (apcer + bpcer) / 2.0)
        diff = apcer - bpcer
        if previous is not None:
            prev_diff, prev_apcer, prev_bpcer = previous
            if prev_diff == 0:
                return (prev_apcer + prev_bpcer) / 2.0
            if prev_diff < 0 <= diff or prev_diff > 0 >= diff:
                weight = (0.0 - prev_diff) / (diff - prev_diff)
                crossed = prev_apcer + weight * (apcer - prev_apcer)
                return float(crossed)
        previous = (diff, apcer, bpcer)
    return None if best is None else float(best[1])


def apcer_at_bpcer(bona: list[float], attack: list[float], target: float = TARGET_BPCER) -> float | None:
    """Plus petit APCER parmi les seuils dont le BPCER est ≤ target."""
    if not bona or not attack:
        return None
    chosen: float | None = None
    for threshold in _thresholds(bona, attack):
        apcer, bpcer = rates(bona, attack, threshold)
        if bpcer <= target and (chosen is None or apcer < chosen):
            chosen = apcer
    return chosen


def open_for_decline(apcer: float, bpcer: float) -> bool:
    """Critère du contrat §12 au seuil de refus, seuils non déplacés."""
    return apcer <= TARGET_APCER and bpcer <= TARGET_BPCER


def report_alert(bona: Iterable[float], attack: Iterable[float]) -> dict[str, object]:
    bona_fide = [float(score) for score in bona]
    attacks = [float(score) for score in attack]
    row: dict[str, object] = {
        "n_bona_fide": len(bona_fide),
        "n_attack": len(attacks),
        "eer": None,
        "apcer_at_bpcer_0_05": None,
        "review": None,
        "decline": None,
        "open_for_decline": False,
    }
    if not bona_fide or not attacks:
        return row
    row["eer"] = _round(eer(bona_fide, attacks))
    row["apcer_at_bpcer_0_05"] = _round(apcer_at_bpcer(bona_fide, attacks))
    for name, threshold in (("review", REVIEW_AT), ("decline", DECLINE_AT)):
        apcer, bpcer = rates(bona_fide, attacks, threshold)
        row[name] = {
            "threshold": threshold,
            "apcer": _round(apcer),
            "bpcer": _round(bpcer),
            "acer": _round(acer(apcer, bpcer)),
        }
    decline = row["decline"]
    assert isinstance(decline, dict)
    row["open_for_decline"] = open_for_decline(float(decline["apcer"]), float(decline["bpcer"]))
    return row


def _thresholds(bona: list[float], attack: list[float]) -> list[float]:
    values = sorted(set(bona + attack + [0.0, 1.0]))
    extra: list[float] = []
    for left, right in zip(values, values[1:]):
        extra.append((left + right) / 2.0)
    return sorted(set(values + extra))


def _round(value: float | None) -> float | None:
    if value is None:
        return None
    return round(float(value), 4)

"""forensic-1 : la logistique du splicing reste monotone et ancrée en 0 et 1."""

import json
from pathlib import Path

from app.calibration import FUSION_VERSION, anchored, apply

_MODEL = Path(__file__).resolve().parents[1] / "app" / "models" / "forensic-1.json"


def test_fusion_version_follows_the_coefficient_file() -> None:
    assert _MODEL.is_file()
    assert FUSION_VERSION == "forensic-1"
    document = json.loads(_MODEL.read_text(encoding="utf-8"))
    assert document["fusion_version"] == "forensic-1"
    assert document["dataset"]["name"] == "document-splice-v1"
    assert document["dataset"]["holdout"]["auroc"] >= 0.9
    assert document["dataset"]["holdout"]["recall_at_0_55"] >= 0.9
    assert document["dataset"]["holdout"]["fpr_at_0_55"] <= 0.05
    assert document["external_bench"]["used_for_coefficients"] is False


def test_splicing_map_is_anchored_and_increasing() -> None:
    assert apply("splicing", 0.0) == 0.0
    assert apply("splicing", 1.0) == 1.0
    assert apply("screen_replay", 0.4) == 0.4
    previous = 0.0
    for step in range(1, 11):
        score = apply("splicing", step / 10)
        assert score >= previous
        previous = score


def test_zero_slope_is_the_identity() -> None:
    for raw in (0.0, 0.2, 0.55, 1.0):
        assert anchored(0.0, 0.0, raw) == raw

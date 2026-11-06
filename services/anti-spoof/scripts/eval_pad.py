"""Mesure ISO/IEC 30107-3 sur un jeu étiqueté hors git.

Ne déplace pas 0,55 ni 0,80. N'écrit pas decline-codes.

    python scripts/build_dataset.py
    python -m scripts.eval_pad
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.contract import Hints, Roi
from app.pipeline import ENGINE_VERSION, FUSION_VERSION, assess
from scripts.build_dataset import DEFAULT_OUT, VERSION
from scripts.pad_metrics import ALERTS, report_alert

STANDARD = "ISO/IEC 30107-3"


def load_manifest(root: Path) -> dict[str, object]:
    path = root / "manifest.json"
    if not path.is_file():
        raise SystemExit(f"manifest absent: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def score_sample(root: Path, row: dict[str, object], use_hints: bool) -> dict[str, float | None]:
    jpeg = (root / str(row["path"])).read_bytes()
    hints = _hints(row) if use_hints else None
    assessment = assess(jpeg, hints)
    scores: dict[str, float | None] = {code: None for code in ALERTS}
    if not assessment.forensic_usable:
        return scores
    for alert in assessment.alerts:
        scores[alert.code] = alert.score
    return scores


def evaluate(root: Path, use_hints: bool) -> dict[str, object]:
    manifest = load_manifest(root)
    samples = list(manifest["samples"])
    bona: dict[str, list[float]] = {code: [] for code in ALERTS}
    attack: dict[str, list[float]] = {code: [] for code in ALERTS}
    scored: list[dict[str, object]] = []
    skipped = 0
    for row in samples:
        scores = score_sample(root, row, use_hints)
        labels = set(row["labels"])
        usable = all(scores[code] is not None for code in ALERTS)
        if not usable:
            skipped += 1
            continue
        record = {
            "id": row["id"],
            "path": row["path"],
            "labels": list(row["labels"]),
            "scores": scores,
        }
        scored.append(record)
        for code in ALERTS:
            value = float(scores[code])  # type: ignore[arg-type]
            if "authentic" in labels and code not in labels:
                bona[code].append(value)
            if code in labels:
                attack[code].append(value)
    alerts = {code: report_alert(bona[code], attack[code]) for code in ALERTS}
    return {
        "standard": STANDARD,
        "engine_version": ENGINE_VERSION,
        "fusion_version": FUSION_VERSION,
        "dataset": {
            "version": manifest.get("version", VERSION),
            "kind": manifest.get("kind"),
            "root": str(root),
            "n_samples": len(samples),
            "n_scored": len(scored),
            "n_unusable": skipped,
            "hints": use_hints,
        },
        "note": (
            "Seuils auth-1 inchangés. open_for_decline est le critère §12 à 0,80. "
            "Un jeu synthétique ne remplace pas le corridor."
        ),
        "alerts": alerts,
        "open_decline_codes": [code for code, row in alerts.items() if row["open_for_decline"]],
        "samples": scored,
    }


def _hints(row: dict[str, object]) -> Hints | None:
    regions = row.get("regions") or []
    rois: list[Roi] = []
    for region in regions:
        name = region.get("name")
        box = region.get("box")
        if name not in {
            "document",
            "portrait",
            "mrz",
            "birth_date",
            "document_number",
            "surname",
            "given_names",
        }:
            continue
        rois.append(Roi(name=name, box=list(box)))
    return Hints(rois=rois) if rois else None


def _print_report(report: dict[str, object]) -> None:
    dataset = report["dataset"]
    print(
        f"{report['standard']}  engine={report['engine_version']}  "
        f"fusion={report['fusion_version']}  n={dataset['n_scored']}/{dataset['n_samples']}"
    )
    print(f"jeu {dataset['version']} ({dataset['kind']})  hints={dataset['hints']}")
    print(
        f"{'alerte':28} {'n_bf':>5} {'n_at':>5} {'EER':>7} "
        f"{'AP@5%':>7} {'R-AP':>7} {'R-BP':>7} {'D-AP':>7} {'D-BP':>7} refus"
    )
    for code, row in report["alerts"].items():
        review = row["review"] or {}
        decline = row["decline"] or {}
        opened = "oui" if row["open_for_decline"] else "non"
        print(
            f"{code:28} {row['n_bona_fide']:5d} {row['n_attack']:5d} "
            f"{_fmt(row['eer']):>7} {_fmt(row['apcer_at_bpcer_0_05']):>7} "
            f"{_fmt(review.get('apcer')):>7} {_fmt(review.get('bpcer')):>7} "
            f"{_fmt(decline.get('apcer')):>7} {_fmt(decline.get('bpcer')):>7} {opened}"
        )
    opened = report["open_decline_codes"]
    print("decline-codes à ouvrir:", ",".join(opened) if opened else "(aucun)")


def _fmt(value: object) -> str:
    if value is None:
        return "n/a"
    return f"{float(value):.3f}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Rapport APCER/BPCER/EER sur un manifeste.")
    parser.add_argument("--root", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--hints", action="store_true", help="Passe les rectangles du manifeste.")
    parser.add_argument("--json-out", type=Path, default=None)
    args = parser.parse_args()
    root = args.root.resolve()
    report = evaluate(root, args.hints)
    _print_report(report)
    out = args.json_out or (root / "pad-report.json")
    out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()

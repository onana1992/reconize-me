"""Passe le jeu laboratoire dans le moteur. Seuil de revue 0,55.

    python scripts/build_dataset.py --kit
    python -m scripts.test_service
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.pipeline import assess
from scripts.build_dataset import KIT_OUT
from scripts.pad_metrics import ALERTS, REVIEW_AT


def load_manifest(root: Path) -> dict[str, object]:
    path = root / "manifest.json"
    if not path.is_file():
        raise SystemExit(f"manifest absent: {path}\nLancer: python scripts/build_dataset.py --kit")
    return json.loads(path.read_text(encoding="utf-8"))


def expected_ok(row: dict[str, object], scores: dict[str, float]) -> bool:
    if row["primary"] == "authentic":
        return all(scores[code] < REVIEW_AT for code in ALERTS)
    return any(scores[code] >= REVIEW_AT for code in row["labels"] if code in scores)


def main() -> None:
    parser = argparse.ArgumentParser(description="Teste le moteur sur le jeu laboratoire.")
    parser.add_argument("--root", type=Path, default=KIT_OUT)
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = load_manifest(root)
    failed = 0
    print(f"{'fichier':42} {'attendu':10} {'alerte':24} {'score':>6} ok")
    for row in manifest["samples"]:
        jpeg = (root / str(row["path"])).read_bytes()
        assessment = assess(jpeg)
        scores = {alert.code: float(alert.score or 0.0) for alert in assessment.alerts}
        if row["primary"] == "authentic":
            code = max(ALERTS, key=lambda name: scores[name])
        else:
            code = max((name for name in row["labels"] if name in scores), key=lambda name: scores[name])
        ok = expected_ok(row, scores)
        if not ok:
            failed += 1
        expect = "saine" if row["primary"] == "authentic" else row["primary"][:10]
        print(
            f"{row['path']:42} {expect:10} {code:24} {scores[code]:6.3f} "
            f"{'ok' if ok else 'MANQUE'}"
        )
    total = len(manifest["samples"])
    print(f"{total - failed}/{total} conformes au seuil 0,55")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()

"""Envoie tout le jeu au service HTTP et écrit le rapport ISO.

    python -m scripts.report_service
"""

from __future__ import annotations

import argparse
import base64
import json
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from scripts.build_dataset import DEFAULT_OUT
from scripts.pad_metrics import ALERTS, DECLINE_AT, REVIEW_AT, report_alert

TOKEN_FILE = _ROOT / ".env"


def load_token() -> str:
    for raw in TOKEN_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.startswith("ANTI_SPOOF_TOKEN="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise SystemExit("ANTI_SPOOF_TOKEN absent de .env")


def load_manifest(root: Path) -> dict[str, object]:
    path = root / "manifest.json"
    if not path.is_file():
        raise SystemExit(f"manifest absent: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def post_analyze(url: str, token: str, jpeg: bytes) -> dict[str, object]:
    body = {
        "request_id": str(uuid.uuid4()),
        "verification_id": str(uuid.uuid4()),
        "side": "FRONT",
        "media_type": "image/jpeg",
        "image_base64": base64.b64encode(jpeg).decode("ascii"),
    }
    raw = json.dumps(body).encode("utf-8")
    request = Request(
        f"{url.rstrip('/')}/v1/analyze",
        data=raw,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        payload = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code} {url}: {payload[:300]}") from exc
    except URLError as exc:
        raise SystemExit(f"service injoignable {url}: {exc.reason}") from exc


def bench_issue(scores: dict[str, float]) -> str:
    if all(scores[code] < REVIEW_AT for code in ALERTS):
        return "PASS"
    return "REVIEW"


def evaluate(root: Path, url: str, token: str) -> dict[str, object]:
    manifest = load_manifest(root)
    bona: dict[str, list[float]] = {code: [] for code in ALERTS}
    attack: dict[str, list[float]] = {code: [] for code in ALERTS}
    rows: list[dict[str, object]] = []
    misses: list[dict[str, object]] = []
    engine = None
    fusion = None
    for sample in manifest["samples"]:
        jpeg = (root / str(sample["path"])).read_bytes()
        payload = post_analyze(url, token, jpeg)
        engine = payload.get("engine_version")
        fusion = payload.get("fusion_version")
        scores = {code: 0.0 for code in ALERTS}
        if payload.get("forensic_usable") and payload.get("alerts"):
            for alert in payload["alerts"]:
                if alert["code"] in scores and alert.get("score") is not None:
                    scores[alert["code"]] = float(alert["score"])
        labels = list(sample["labels"])
        issue = bench_issue(scores)
        expected = "PASS" if sample["primary"] == "authentic" else "REVIEW"
        ok = issue == expected
        if sample["primary"] == "authentic":
            ok = ok and all(scores[code] < REVIEW_AT for code in ALERTS)
        else:
            ok = any(scores[code] >= REVIEW_AT for code in labels if code in scores)
            expected = "REVIEW"
        record = {
            "id": sample["id"],
            "path": sample["path"],
            "primary": sample["primary"],
            "labels": labels,
            "variant": sample.get("variant"),
            "scores": scores,
            "authenticity_score": payload.get("authenticity_score"),
            "issue": issue,
            "expected": expected,
            "ok": ok,
        }
        rows.append(record)
        if not ok:
            misses.append(record)
        for code in ALERTS:
            value = scores[code]
            if "authentic" in labels and code not in labels:
                bona[code].append(value)
            if code in labels:
                attack[code].append(value)
    alerts = {code: report_alert(bona[code], attack[code]) for code in ALERTS}
    return {
        "standard": "ISO/IEC 30107-3",
        "via": url,
        "at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "engine_version": engine,
        "fusion_version": fusion,
        "dataset": {
            "version": manifest.get("version"),
            "kind": manifest.get("kind"),
            "root": str(root),
            "n_samples": len(manifest["samples"]),
            "n_scored": len(rows),
        },
        "review_at": REVIEW_AT,
        "decline_at": DECLINE_AT,
        "conformes": sum(1 for row in rows if row["ok"]),
        "alerts": alerts,
        "open_decline_codes": [code for code, row in alerts.items() if row["open_for_decline"]],
        "misses": misses,
        "samples": rows,
    }


def write_text(report: dict[str, object], path: Path) -> None:
    lines = [
        "Rapport anti-spoof — jeu laboratoire",
        f"Date UTC : {report['at']}",
        f"Service : {report['via']}",
        f"Moteur : {report['engine_version']}  fusion : {report['fusion_version']}",
        f"Jeu : {report['dataset']['version']} ({report['dataset']['kind']})  "
        f"n={report['dataset']['n_scored']}",
        f"Conformes au seuil 0,55 : {report['conformes']}/{report['dataset']['n_scored']}",
        "",
        "ISO/IEC 30107-3  (R = revue 0,55 ; D = refus 0,80)",
        f"{'alerte':28} {'n_bf':>5} {'n_at':>5} {'EER':>7} {'AP@5%':>7} "
        f"{'R-AP':>7} {'R-BP':>7} {'D-AP':>7} {'D-BP':>7} refus",
    ]
    for code, row in report["alerts"].items():
        review = row["review"] or {}
        decline = row["decline"] or {}
        opened = "oui" if row["open_for_decline"] else "non"
        lines.append(
            f"{code:28} {row['n_bona_fide']:5d} {row['n_attack']:5d} "
            f"{_fmt(row['eer']):>7} {_fmt(row['apcer_at_bpcer_0_05']):>7} "
            f"{_fmt(review.get('apcer')):>7} {_fmt(review.get('bpcer')):>7} "
            f"{_fmt(decline.get('apcer')):>7} {_fmt(decline.get('bpcer')):>7} {opened}"
        )
    opened = report["open_decline_codes"]
    lines.append("decline-codes à ouvrir : " + (",".join(opened) if opened else "(aucun)"))
    lines.append("")
    lines.append("Écarts (attendu 0,55 non tenu)")
    if not report["misses"]:
        lines.append("aucun")
    else:
        for miss in report["misses"]:
            top = max(miss["scores"], key=lambda code: miss["scores"][code])
            lines.append(
                f"- {miss['path']}  primaire={miss['primary']}  "
                f"{top}={miss['scores'][top]:.3f}  issue={miss['issue']}"
            )
    lines.append("")
    lines.append(
        "APCER = part des attaques sous le seuil. BPCER = part des cartes saines au-dessus. "
        "decline-codes reste vide tant que le corridor §12 n'est pas mesuré à part."
    )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _fmt(value: object) -> str:
    if value is None:
        return "n/a"
    return f"{float(value):.3f}"


def main() -> None:
    parser = argparse.ArgumentParser(description="Rapport HTTP sur le jeu étiqueté.")
    parser.add_argument("--root", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--url", default="http://127.0.0.1:8090")
    args = parser.parse_args()
    root = args.root.resolve()
    report = evaluate(root, args.url, load_token())
    json_path = root / "service-report.json"
    text_path = root / "RAPPORT.txt"
    json_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    write_text(report, text_path)
    print(text_path.read_text(encoding="utf-8"))
    print(f"wrote {text_path}")
    print(f"wrote {json_path}")


if __name__ == "__main__":
    main()

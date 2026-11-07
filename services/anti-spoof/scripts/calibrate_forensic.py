"""Calibre l'alerte splicing et écrit app/models/forensic-1.json.

Le jeu Columbia (blocs 128×128) est le banc cité par la spécification.
Il est sous le seuil de 480 px du service, et l'archive officielle est
derrière un formulaire. Les coefficients sont donc ajustés sur
document-splice-v1 : mêmes étiquettes (une capture / deux grains, ou
une copie interne), à la taille et au JPEG que le service mesure.

Si --columbia pointe un dossier déjà téléchargé (sous-dossiers Au* / Sp*),
le script ajoute l'AUROC de ce banc au fichier, sans y ajuster les coefficients.
"""

from __future__ import annotations

import argparse
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

from app.calibration import anchored
from app.detectors.splicing import detect
from app.jpeg import luma

_ROOT = Path(__file__).resolve().parents[1]
_MODEL = _ROOT / "app" / "models" / "forensic-1.json"
_ALERTS = (
    "splicing",
    "text_alteration",
    "compression_inconsistency",
    "screen_replay",
    "printed_copy",
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Calibre splicing et écrit forensic-1.json.")
    parser.add_argument("--columbia", type=Path, default=None)
    args = parser.parse_args()
    samples = _document_set()
    raw = np.array([_alert_score(pixels) for pixels, _label in samples], dtype=np.float64)
    labels = np.array([label for _pixels, label in samples], dtype=np.int8)
    train, holdout = _split(labels)
    constraints = _product_constraints()
    a, b = _fit(raw[train], labels[train], constraints)
    hold_raw, hold_y = raw[holdout], labels[holdout]
    hold_score = np.array([anchored(a, b, value) for value in hold_raw])
    metrics = _metrics(hold_score, hold_y)
    print(
        f"a={a:.4f} b={b:.4f}  "
        f"holdout auroc={metrics['auroc']:.3f}  "
        f"recall@0.55={metrics['recall_at_0_55']:.3f}  "
        f"fpr@0.55={metrics['fpr_at_0_55']:.3f}"
    )
    print(
        "train authentic",
        _quantile(raw[train][labels[train] == 0]),
        "spliced",
        _quantile(raw[train][labels[train] == 1]),
    )
    print("map", " ".join(f"{value:.2f}->{anchored(a, b, value):.3f}" for value in (0.0, 0.33, 0.55, 0.8, 1.0)))
    document = _payload(a, b, metrics, len(samples), args.columbia)
    _MODEL.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(_MODEL)


def _payload(a: float, b: float, metrics: dict[str, float], n_samples: int, columbia: Path | None) -> dict:
    alerts = {code: {"family": "identity"} for code in _ALERTS}
    alerts["splicing"] = {
        "family": "anchored_logistic",
        "a": round(a, 6),
        "b": round(b, 6),
    }
    bench = {
        "name": "Columbia Image Splicing Detection Evaluation Dataset",
        "url": "https://www.ee.columbia.edu/ln/dvmm/downloads/AuthSplicedDataSet/AuthSplicedDataSet.htm",
        "used_for_coefficients": False,
        "reason": (
            "Blocs 128x128, sous le seuil 480 px du service. "
            "Les coefficients viennent de document-splice-v1 : grain étranger contre une seule capture, "
            "à la taille JPEG du contrat. Le copy-move du contrat reste une contrainte, pas une classe du jeu."
        ),
    }
    if columbia is not None:
        bench["auroc"] = round(_columbia_auroc(columbia), 4)
        bench["path"] = str(columbia)
    return {
        "fusion_version": "forensic-1",
        "dataset": {
            "name": "document-splice-v1",
            "n": n_samples,
            "holdout": metrics,
        },
        "external_bench": bench,
        "alerts": alerts,
    }


def _fit(raw: np.ndarray, labels: np.ndarray, constraints: list[tuple[float, int]]) -> tuple[float, float]:
    best: tuple[float, float, float] | None = None
    for a in np.linspace(-2.0, 6.0, 33):
        for b in np.linspace(0.0, 12.0, 49):
            if not _constraints_ok(float(a), float(b), constraints):
                continue
            score = np.array([anchored(float(a), float(b), value) for value in raw])
            if _fpr(score, labels) > 0.05 or _recall(score, labels) < 0.90:
                continue
            loss = _bce(score, labels)
            if best is None or loss < best[0]:
                best = (loss, float(a), float(b))
    if best is None:
        raise SystemExit("aucune logistique ne tient le rappel, le faux positif et les scenes du contrat")
    return best[1], best[2]


def _constraints_ok(a: float, b: float, constraints: list[tuple[float, int]]) -> bool:
    for raw, label in constraints:
        score = anchored(a, b, raw)
        if label == 1 and score <= 0.55:
            return False
        if label == 0 and score >= 0.55:
            return False
    # Une trace déjà forte ne doit pas redescendre sous le seuil de revue.
    for raw in (0.55, 0.7, 0.85, 1.0):
        if anchored(a, b, raw) < raw - 0.02:
            return False
    return True


def _bce(score: np.ndarray, labels: np.ndarray) -> float:
    total = 0.0
    weight = 0.0
    for wanted in (0, 1):
        chosen = labels == wanted
        if not np.any(chosen):
            continue
        p = np.clip(score[chosen], 1e-4, 1 - 1e-4)
        y = float(wanted)
        total += float(-(y * np.log(p) + (1 - y) * np.log(1 - p)).mean())
        weight += 1.0
    return total / weight


def _metrics(score: np.ndarray, labels: np.ndarray) -> dict[str, float]:
    return {
        "auroc": round(_auroc(score, labels), 4),
        "recall_at_0_55": round(_recall(score, labels), 4),
        "fpr_at_0_55": round(_fpr(score, labels), 4),
    }


def _recall(score: np.ndarray, labels: np.ndarray) -> float:
    positive = labels == 1
    if not np.any(positive):
        return 0.0
    return float((score[positive] >= 0.55).mean())


def _fpr(score: np.ndarray, labels: np.ndarray) -> float:
    negative = labels == 0
    if not np.any(negative):
        return 0.0
    return float((score[negative] >= 0.55).mean())


def _auroc(score: np.ndarray, labels: np.ndarray) -> float:
    order = np.argsort(score)
    y = labels[order]
    n_pos = float(y.sum())
    n_neg = float(len(y) - n_pos)
    if n_pos == 0 or n_neg == 0:
        return 0.5
    ranks = np.empty(len(y), dtype=np.float64)
    start = 0
    while start < len(y):
        end = start + 1
        while end < len(y) and score[order[end]] == score[order[start]]:
            end += 1
        ranks[start:end] = 0.5 * (start + 1 + end)
        start = end
    rank_sum = float(ranks[y == 1].sum())
    return (rank_sum - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg)


def _split(labels: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    train: list[int] = []
    holdout: list[int] = []
    for label in (0, 1):
        index = np.flatnonzero(labels == label)
        index = np.random.default_rng(label + 11).permutation(index)
        cut = max(1, int(round(len(index) * 0.7)))
        train.extend(int(i) for i in index[:cut])
        holdout.extend(int(i) for i in index[cut:])
    return np.array(train), np.array(holdout)


def _product_constraints() -> list[tuple[float, int]]:
    """Scènes du contrat : le calibrage ne doit pas les faire changer de côté."""
    scenes: list[tuple[np.ndarray, int]] = [
        (_pasted(), 1),
        (_copy_move(), 1),
        (_uniform(), 0),
        (_flat(), 0),
        (_periodic(), 0),
        (_card(), 0),
        (_recompressed_card(), 0),
    ]
    return [(_alert_score(pixels), label) for pixels, label in scenes]


def _document_set() -> list[tuple[np.ndarray, int]]:
    samples: list[tuple[np.ndarray, int]] = []
    for seed in range(24):
        samples.append((_noise_field(seed), 0))
    for seed in range(12):
        samples.append((_card_on_sheet(seed), 0))
    for seed in range(8):
        samples.append((_periodic_field(seed), 0))
    for seed in range(8):
        samples.append((_gradient_field(seed), 0))
    for seed in range(8):
        samples.append((_recompressed_field(seed), 0))
    for seed in range(36):
        samples.append((_foreign_grain(seed), 1))
    return samples


def _alert_score(pixels: np.ndarray) -> float:
    gray = luma(pixels)
    residual = gray - gaussian_filter(gray, 1.2)
    hit = detect(_View(pixels, gray, residual), None)
    return float(hit.score)


class _View:
    def __init__(self, pixels: np.ndarray, gray: np.ndarray, residual: np.ndarray) -> None:
        self.pixels = pixels
        self.luma = gray
        self.residual = residual


def _jpeg(pixels: np.ndarray, quality: int) -> np.ndarray:
    buffer = io.BytesIO()
    image = Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8), "RGB")
    image.save(buffer, format="JPEG", quality=quality)
    buffer.seek(0)
    with Image.open(buffer) as decoded:
        return np.array(decoded.convert("RGB"), dtype=np.uint8)


def _noise_field(seed: int) -> np.ndarray:
    rng = np.random.default_rng(1000 + seed)
    mean = float(rng.uniform(80, 210))
    sigma = float(rng.uniform(0.8, 2.4))
    canvas = mean + rng.normal(0, sigma, (480, 640, 3))
    return _jpeg(canvas, int(rng.integers(75, 93)))


def _card_on_sheet(seed: int) -> np.ndarray:
    rng = np.random.default_rng(2000 + seed)
    sheet = float(rng.uniform(190, 230)) + rng.normal(0, 1.3, (640, 800))
    card = np.full((420, 560), float(rng.uniform(210, 240)))
    card += rng.normal(0, 1.1, card.shape)
    rows, cols = np.mgrid[0:180, 0:140]
    card[30:210, 24:164] = 80 + 40 * np.sin(cols / 6.0)
    for top in range(240, 380, 36):
        card[top : top + 12, 24:400] = 40
    canvas = np.stack([sheet, sheet * 0.99, sheet * 0.97], axis=2)
    canvas[80:500, 100:660] = card[..., None]
    return _jpeg(canvas, 82)


def _periodic_field(seed: int) -> np.ndarray:
    rng = np.random.default_rng(3000 + seed)
    tile = rng.normal(float(rng.uniform(90, 160)), 16, (32, 32, 3))
    return _jpeg(np.tile(tile, (15, 20, 1))[:480, :640], 90)


def _gradient_field(seed: int) -> np.ndarray:
    rng = np.random.default_rng(4000 + seed)
    xx = np.linspace(0, 1, 640, dtype=np.float32)
    base = 50 + 150 * xx
    canvas = base[None, :, None] + rng.normal(0, 1.4, (480, 640, 3))
    return _jpeg(canvas, 88)


def _recompressed_field(seed: int) -> np.ndarray:
    rng = np.random.default_rng(5000 + seed)
    canvas = float(rng.uniform(100, 180)) + rng.normal(0, 1.2, (480, 640, 3))
    return _jpeg(_jpeg(canvas, 90).astype(np.float32), 60)


def _foreign_grain(seed: int) -> np.ndarray:
    rng = np.random.default_rng(6000 + seed)
    canvas = float(rng.uniform(90, 190)) + rng.normal(0, float(rng.uniform(1.0, 2.0)), (480, 640, 3))
    height = int(rng.integers(96, 176))
    width = int(rng.integers(112, 220))
    top = int(rng.integers(32, 480 - height - 16))
    left = int(rng.integers(32, 640 - width - 16))
    sigma = float(rng.uniform(22, 45))
    canvas[top : top + height, left : left + width] = rng.normal(0, sigma, (height, width, 3)) + float(
        rng.uniform(50, 160)
    )
    return _jpeg(canvas, int(rng.integers(85, 95)))


def _pasted() -> np.ndarray:
    rng = np.random.default_rng(0)
    canvas = np.full((480, 640, 3), 176.0) + rng.normal(0, 1.5, (480, 640, 3))
    canvas[150:310, 220:420] = rng.normal(0, 42, (160, 200, 3)) + 70
    return _jpeg(canvas, 92)


def _uniform() -> np.ndarray:
    return _jpeg(np.full((480, 640, 3), 140.0), 80)


def _flat() -> np.ndarray:
    canvas = np.zeros((480, 480, 3))
    canvas[..., 0] = 20
    canvas[..., 1] = 40
    canvas[..., 2] = 60
    return _jpeg(canvas, 80)


def _periodic() -> np.ndarray:
    rng = np.random.default_rng(1)
    tile = rng.normal(120, 18, (32, 32, 3))
    return _jpeg(np.tile(tile, (15, 20, 1)), 92)


def _copy_move() -> np.ndarray:
    canvas = np.full((480, 640, 3), 150.0)
    yy, xx = np.mgrid[0:160, 0:160]
    patch = np.stack(
        [
            40 + 140 * (xx / 160),
            30 + 80 * ((xx - 80) ** 2 + (yy - 60) ** 2) / 12800,
            200 - 120 * (yy / 160),
        ],
        axis=2,
    )
    canvas[32:192, 32:192] = patch
    canvas[256:416, 384:544] = patch
    return _jpeg(canvas, 95)


def _card() -> np.ndarray:
    rng = np.random.default_rng(7)
    sheet = 214 + rng.normal(0, 1.4, (960, 1280))
    card = np.full((520, 820), 232.0) + rng.normal(0, 1.2, (520, 820))
    rows, cols = np.mgrid[0:240, 0:180]
    card[40:280, 36:216] = 90 + 50 * np.sin(cols / 6.0) + 35 * np.sin(rows / 9.0)
    for top in (310, 350, 390, 430):
        card[top : top + 14, 36:560] = 48
    canvas = np.stack([sheet, sheet * 0.99, sheet * 0.97], axis=2)
    canvas[180:700, 220:1040] = card[..., None]
    return _jpeg(canvas, 82)


def _recompressed_card() -> np.ndarray:
    rng = np.random.default_rng(8)
    sheet = 220 + rng.normal(0, 1.0, (960, 1280))
    card = np.full((700, 1100), 200.0) + rng.normal(0, 0.8, (700, 1100))
    card[80:360, 60:320] = 110
    for top in (420, 470, 520, 570):
        card[top : top + 16, 60:820] = 48
    canvas = np.stack([sheet, sheet * 0.99, sheet * 0.97], axis=2)
    canvas[80:780, 90:1190] = card[..., None]
    once = _jpeg(canvas, 55).astype(np.float32)
    return _jpeg(once, 40)


def _columbia_auroc(root: Path) -> float:
    scores: list[float] = []
    labels: list[int] = []
    for path in sorted(root.rglob("*")):
        if path.suffix.lower() not in {".bmp", ".png", ".jpg", ".jpeg", ".tif"}:
            continue
        name = path.as_posix().lower()
        if "/au" in name or name.startswith("au"):
            label = 0
        elif "/sp" in name or "splic" in name:
            label = 1
        else:
            continue
        with Image.open(path) as image:
            pixels = np.array(image.convert("RGB"), dtype=np.uint8)
        scores.append(_alert_score(pixels))
        labels.append(label)
    if len(scores) < 10:
        raise SystemExit(f"pas assez d'images Columbia dans {root}")
    return _auroc(np.array(scores), np.array(labels))


def _quantile(values: np.ndarray) -> str:
    if values.size == 0:
        return "vide"
    qs = np.quantile(values, [0.0, 0.5, 0.9, 1.0])
    return " ".join(f"{q:.3f}" for q in qs)


if __name__ == "__main__":
    main()

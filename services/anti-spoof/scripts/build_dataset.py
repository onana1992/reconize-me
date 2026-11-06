"""Jeu synthétique des cinq alertes. Aucune pièce réelle.

Les images vont hors git (data/anti-spoof/). Elles portent les traces
pour lesquelles les détecteurs sont écrits : collage, glyphes repris,
bande recompressée, grille d'écran, trame de points.

Usage, depuis services/anti-spoof :

    python scripts/build_dataset.py
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

VERSION = "synth-v1"
WIDTH = 640
HEIGHT = 480
ATTACKS = (
    "splicing",
    "text_alteration",
    "compression_inconsistency",
    "screen_replay",
    "printed_copy",
)
REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_OUT = REPO_ROOT / "data" / "anti-spoof" / VERSION
KIT_OUT = REPO_ROOT / "data" / "anti-spoof" / "lab-kit"


@dataclass(frozen=True)
class Sample:
    jpeg: bytes
    primary: str
    labels: tuple[str, ...]
    variant: str
    regions: tuple[dict[str, object], ...]
    notes: str


def _save(pixels: np.ndarray, quality: int, subsampling: int | None = None) -> bytes:
    buffer = __import__("io").BytesIO()
    kwargs: dict[str, object] = {"format": "JPEG", "quality": quality}
    if subsampling is not None:
        kwargs["subsampling"] = subsampling
    Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8), "RGB").save(buffer, **kwargs)
    return buffer.getvalue()


def _decode(raw: bytes) -> np.ndarray:
    with Image.open(__import__("io").BytesIO(raw)) as image:
        return np.array(image.convert("RGB"), dtype=np.uint8, copy=True)


def _box(left: int, top: int, right: int, bottom: int) -> tuple[float, float, float, float]:
    return (left / WIDTH, top / HEIGHT, (right - left) / WIDTH, (bottom - top) / HEIGHT)


def _roi(name: str, box: tuple[float, float, float, float]) -> dict[str, object]:
    return {"name": name, "box": [round(value, 4) for value in box]}


def _glyphs(rng: np.random.Generator, image: np.ndarray, rows: list[int], ink: int) -> None:
    for row_y in rows:
        x = 36
        while x < image.shape[1] - 36:
            width = int(rng.integers(7, 16))
            height = int(rng.integers(16, 24))
            image[row_y : row_y + height, x : x + width] = ink
            x += width + int(rng.integers(5, 12))


def _specimen(rng: np.random.Generator) -> np.ndarray:
    """Carte laboratoire : bandeau, portrait vide, lignes de glyphes. Pas d'identité."""
    canvas = np.full((HEIGHT, WIDTH, 3), 232, np.uint8)
    canvas[:56, :] = (28, 72, 140)
    canvas[400:, :] = (28, 72, 140)
    canvas[72:248, 36:196] = (168, 174, 186)
    canvas[72:248, 36:196] = np.clip(
        canvas[72:248, 36:196].astype(np.float32) + rng.normal(0, 6, (176, 160, 3)),
        0,
        255,
    ).astype(np.uint8)
    _glyphs(rng, canvas, [80, 140, 200, 260, 320], 32)
    return canvas


def authentic_uniform(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    tone = int(rng.integers(120, 180))
    quality = int(rng.choice([75, 80, 88, 92]))
    return Sample(
        jpeg=_save(np.full((HEIGHT, WIDTH, 3), tone, np.float32), quality),
        primary="authentic",
        labels=("authentic",),
        variant="uniform",
        regions=(),
        notes="Carte uniforme, une seule qualité JPEG.",
    )


def authentic_glyphs(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    quality = int(rng.choice([80, 88, 92]))
    return Sample(
        jpeg=_save(_specimen(rng), quality),
        primary="authentic",
        labels=("authentic",),
        variant="glyphs",
        regions=(_roi("portrait", _box(36, 72, 196, 248)),),
        notes="Spécimen d'une seule qualité. Recompression globale absente.",
    )


def authentic_recompress(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    first = int(rng.choice([88, 92, 95]))
    second = int(rng.choice([55, 60, 70]))
    return Sample(
        jpeg=_save(_decode(_save(_specimen(rng), first)), second),
        primary="authentic",
        labels=("authentic",),
        variant="recompress",
        regions=(_roi("portrait", _box(36, 72, 196, 248)),),
        notes="Téléphone : tout le fichier ré-enregistré. compression_inconsistency doit rester à 0.",
    )


def authentic_guilloche(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    tile = rng.normal(120, 18, (32, 32, 3))
    canvas = np.tile(tile, (HEIGHT // 32, WIDTH // 32, 1))[:HEIGHT, :WIDTH]
    return Sample(
        jpeg=_save(canvas, 90),
        primary="authentic",
        labels=("authentic",),
        variant="guilloche",
        regions=(),
        notes="Fond périodique. Ce n'est pas une copie interne.",
    )


def splicing_paste(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    canvas = np.full((HEIGHT, WIDTH, 3), float(rng.integers(150, 190)), np.float32)
    canvas += rng.normal(0, 1.5, canvas.shape)
    ph, pw = int(rng.integers(140, 200)), int(rng.integers(160, 220))
    top = int(rng.integers(80, HEIGHT - ph - 20))
    left = int(rng.integers(80, WIDTH - pw - 20))
    canvas[top : top + ph, left : left + pw] = rng.normal(0, 42, (ph, pw, 3)) + 70
    box = _box(left, top, left + pw, top + ph)
    return Sample(
        jpeg=_save(canvas, 92),
        primary="splicing",
        labels=("splicing",),
        variant="paste",
        regions=(_roi("portrait", box),),
        notes="Texture collée depuis un autre bruit.",
    )


def splicing_clone(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    canvas = np.full((HEIGHT, WIDTH, 3), 150.0, np.float32)
    size = 160
    yy, xx = np.mgrid[0:size, 0:size]
    patch = np.stack(
        [
            40 + 140 * (xx / size),
            30 + 80 * ((xx - 80) ** 2 + (yy - 60) ** 2) / 12800,
            200 - 120 * (yy / size),
        ],
        axis=2,
    )
    ax, ay = 32, 32
    bx = int(rng.integers(360, 440))
    by = int(rng.integers(240, 300))
    canvas[ay : ay + size, ax : ax + size] = patch
    canvas[by : by + size, bx : bx + size] = patch
    return Sample(
        jpeg=_save(canvas, 95),
        primary="splicing",
        labels=("splicing",),
        variant="clone",
        regions=(
            _roi("portrait", _box(ax, ay, ax + size, ay + size)),
            _roi("document", _box(bx, by, bx + size, by + size)),
        ),
        notes="Le même motif est dupliqué dans l'image.",
    )


def text_retouch(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    canvas = np.full((HEIGHT, WIDTH, 3), 232, np.uint8)
    _glyphs(rng, canvas, [70, 150, 230, 310, 390], 25)
    soft = _decode(_save(canvas, 40, subsampling=1))
    top = int(rng.choice([176, 208, 240]))
    bottom = top + 64
    left, right = 32, 608
    fresh = np.full_like(canvas, 232)
    _glyphs(np.random.default_rng(seed + 50), fresh, [top + 14], 18)
    band = _decode(_save(fresh[top:bottom, left:right], 75, subsampling=1))
    soft[top : top + band.shape[0], left : left + band.shape[1]] = band
    box = _box(left, top, right, bottom)
    return Sample(
        jpeg=_save(soft, 93, subsampling=1),
        primary="text_alteration",
        labels=("text_alteration", "compression_inconsistency"),
        variant="retouch",
        regions=(_roi("birth_date", box),),
        notes="Glyphes repris puis bande ré-encodée à part. Les deux alertes montent.",
    )


def compression_band(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    rows, cols = np.mgrid[0:HEIGHT, 0:WIDTH]
    wave = 140 + 35 * np.sin(cols / 3.0) + 25 * np.sin(rows / 5.0)
    canvas = np.stack([wave, wave * 0.95, wave * 1.05], axis=2)
    canvas = np.clip(canvas + rng.normal(0, 8, canvas.shape), 0, 255).astype(np.uint8)
    soft = _decode(_save(canvas, 40, subsampling=1))
    top = int(rng.choice([160, 192, 224]))
    bottom = top + 96
    left, right = 48, 592
    band = _decode(_save(soft[top:bottom, left:right], 90, subsampling=1))
    soft[top : top + band.shape[0], left : left + band.shape[1]] = band
    box = _box(left, top, right, bottom)
    return Sample(
        jpeg=_save(soft, 93, subsampling=1),
        primary="compression_inconsistency",
        labels=("compression_inconsistency",),
        variant="local_band",
        regions=(_roi("document", box),),
        notes="Bande ré-encodée à une autre qualité. Pas une recompression globale.",
    )


def screen_rgb(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    period = float(rng.choice([6, 8, 10]))
    phase = 2 * np.pi * np.arange(WIDTH) / period
    image = np.zeros((HEIGHT, WIDTH, 3), np.float32)
    image[:, :, 0] = 128 + 90 * np.sin(phase)
    image[:, :, 1] = 128 + 90 * np.sin(phase + 2 * np.pi / 3)
    image[:, :, 2] = 128 + 90 * np.sin(phase + 4 * np.pi / 3)
    return Sample(
        jpeg=_save(image, 92),
        primary="screen_replay",
        labels=("screen_replay",),
        variant="rgb_grid",
        regions=(_roi("document", (0.0, 0.0, 1.0, 1.0)),),
        notes="Grille RVB de sous-pixels. printed_copy doit rester en dessous.",
    )


def screen_moire(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    period = float(rng.choice([40, 48, 56]))
    image = np.full((HEIGHT, WIDTH, 3), 18, np.float32)
    top, bottom, left, right = 40, 440, 50, 590
    rows = np.arange(top, bottom)
    wave = 140 + 40 * np.sin(2 * np.pi * rows / period)
    image[top:bottom, left:right, :] = wave[:, None, None]
    image[180:340, 160:480, :] = np.clip(image[180:340, 160:480, :] + 25, 0, 255)
    return Sample(
        jpeg=_save(image, 92),
        primary="screen_replay",
        labels=("screen_replay",),
        variant="monitor_moire",
        regions=(_roi("document", _box(left, top, right, bottom)),),
        notes="Moiré de moniteur, bandes grises.",
    )


def screen_shift(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    rows, cols = np.mgrid[0:HEIGHT, 0:WIDTH]
    base = float(rng.choice([5, 6, 7]))
    period = base + 3 * (rows / HEIGHT) + 1.2 * np.sin(2 * np.pi * cols / 180)
    phase = 2 * np.pi * cols / period + 1.4 * np.sin(2 * np.pi * rows / 70)
    wave = 128 + 28 * np.sin(phase)
    return Sample(
        jpeg=_save(np.stack([wave, wave, wave], axis=-1), 92),
        primary="screen_replay",
        labels=("screen_replay",),
        variant="shifting_grating",
        regions=(_roi("document", (0.0, 0.0, 1.0, 1.0)),),
        notes="Période qui glisse. Le spectre global ne forme pas un pic unique.",
    )


def printed_dots(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    step = int(rng.choice([10, 12, 14]))
    size = int(rng.choice([2, 3]))
    paper = int(rng.integers(220, 240))
    image = np.full((HEIGHT, WIDTH, 3), paper, np.float32)
    for y in range(0, HEIGHT, step):
        for x in range(0, WIDTH, step):
            image[y : y + size, x : x + size] = 25
    return Sample(
        jpeg=_save(image, 92),
        primary="printed_copy",
        labels=("printed_copy",),
        variant="halftone",
        regions=(_roi("document", (0.0, 0.0, 1.0, 1.0)),),
        notes="Trame de points type jet d'encre. screen_replay doit rester en dessous.",
    )


def printed_offset(seed: int) -> Sample:
    rng = np.random.default_rng(seed)
    field = gaussian_filter(rng.normal(0, 1, (HEIGHT, WIDTH)), 8)
    field = 200 + 40 * field / (field.std() + 1e-6)
    image = np.stack([field, field * 0.98, field * 1.02], axis=2)
    step = 11
    for y in range(0, HEIGHT, step):
        for x in range(2 if y // step % 2 else 0, WIDTH, step):
            image[y : y + 2, x : x + 2] = 40
    return Sample(
        jpeg=_save(image, 90),
        primary="printed_copy",
        labels=("printed_copy",),
        variant="offset",
        regions=(_roi("document", (0.0, 0.0, 1.0, 1.0)),),
        notes="Points décalés plus grain de papier.",
    )


_BUILDERS: dict[str, tuple[tuple[object, ...], tuple[str, ...]]] = {
    "authentic": (
        (authentic_uniform, authentic_glyphs, authentic_recompress, authentic_guilloche),
        ("authentic",),
    ),
    "splicing": ((splicing_paste, splicing_clone), ("splicing",)),
    "text_alteration": ((text_retouch,), ("text_alteration", "compression_inconsistency")),
    "compression_inconsistency": ((compression_band,), ("compression_inconsistency",)),
    "screen_replay": ((screen_rgb, screen_moire, screen_shift), ("screen_replay",)),
    "printed_copy": ((printed_dots, printed_offset), ("printed_copy",)),
}


def make_class(label: str, index: int) -> Sample:
    builders, _expected = _BUILDERS[label]
    builder = builders[index % len(builders)]
    return builder(10_000 + index * 17 + sum(ord(char) for char in label))


def records_for(label: str, count: int) -> list[Sample]:
    return [make_class(label, index) for index in range(count)]


def write_dataset(out: Path, per_attack: int, authentic: int) -> dict[str, object]:
    out.mkdir(parents=True, exist_ok=True)
    samples: list[dict[str, object]] = []
    plan = [("authentic", authentic), *[(label, per_attack) for label in ATTACKS]]
    for label, count in plan:
        folder = out / label
        folder.mkdir(parents=True, exist_ok=True)
        for index, sample in enumerate(records_for(label, count)):
            name = f"{index:03d}_{sample.variant}.jpg"
            path = folder / name
            path.write_bytes(sample.jpeg)
            relative = f"{label}/{name}"
            samples.append(
                {
                    "id": f"{label}_{index:03d}",
                    "path": relative.replace("\\", "/"),
                    "primary": sample.primary,
                    "labels": list(sample.labels),
                    "variant": sample.variant,
                    "regions": list(sample.regions),
                    "notes": sample.notes,
                    "bytes": path.stat().st_size,
                    "split": "eval",
                }
            )
    manifest = {
        "version": VERSION,
        "kind": "synthetic",
        "engine_target": "anti-spoof-1",
        "width": WIDTH,
        "height": HEIGHT,
        "media_type": "image/jpeg",
        "note": (
            "Jeu laboratoire. Aucune pièce d'identité réelle. "
            "Hors git. Ne remplace pas le corridor annoté du contrat §12."
        ),
        "labels": ["authentic", *ATTACKS],
        "counts": {label: sum(1 for row in samples if row["primary"] == label) for label, _count in plan},
        "samples": samples,
    }
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    lines = [
        "id,path,primary,labels,variant,split",
        *[
            f"{row['id']},{row['path']},{row['primary']},{'|'.join(row['labels'])},{row['variant']},{row['split']}"
            for row in samples
        ],
    ]
    (out / "manifest.csv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return manifest


def write_kit(out: Path) -> dict[str, object]:
    """Un JPEG par variante, pour Swagger et python -m scripts.test_service."""
    out.mkdir(parents=True, exist_ok=True)
    samples: list[dict[str, object]] = []
    index = 0
    for label, (builders, _labels) in _BUILDERS.items():
        folder = out / label
        folder.mkdir(parents=True, exist_ok=True)
        for builder in builders:
            sample = builder(20_000 + index * 13 + sum(ord(char) for char in label))
            name = f"{index:02d}_{sample.variant}.jpg"
            path = folder / name
            path.write_bytes(sample.jpeg)
            samples.append(
                {
                    "id": f"{label}_{sample.variant}",
                    "path": f"{label}/{name}".replace("\\", "/"),
                    "primary": sample.primary,
                    "labels": list(sample.labels),
                    "variant": sample.variant,
                    "regions": list(sample.regions),
                    "notes": sample.notes,
                    "bytes": path.stat().st_size,
                    "split": "kit",
                    "expect_review": sample.primary != "authentic",
                }
            )
            index += 1
    manifest = {
        "version": "lab-kit-1",
        "kind": "synthetic-kit",
        "engine_target": "anti-spoof-1",
        "note": (
            "Jeu court pour tester le service. Aucune pièce réelle. Hors git. "
            "Une carte authentique : toutes les alertes < 0,55. "
            "Une attaque : l'alerte principale >= 0,55."
        ),
        "labels": ["authentic", *ATTACKS],
        "counts": {label: sum(1 for row in samples if row["primary"] == label) for label in ["authentic", *ATTACKS]},
        "samples": samples,
    }
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (out / "manifest.csv").write_text(
        "id,path,primary,labels,variant,expect_review\n"
        + "\n".join(
            f"{row['id']},{row['path']},{row['primary']},{'|'.join(row['labels'])},{row['variant']},{str(row['expect_review']).lower()}"
            for row in samples
        )
        + "\n",
        encoding="utf-8",
    )
    (out / "LISEZMOI.txt").write_text(
        "\n".join(
            [
                "Jeu laboratoire anti-spoof. Pas de pièce d'identité réelle.",
                "",
                "Swagger Python (http://127.0.0.1:8090/docs) : Authorize avec ANTI_SPOOF_TOKEN,",
                "puis POST /v1/analyze-upload. Swagger API (http://127.0.0.1:8080) : clé ky_live_.",
                "",
                "Attendu :",
                "- authentic_* : toutes les alertes sous 0,55, decision PASS.",
                "- splicing_*, text_*, compression_*, screen_*, printed_* : l'alerte du dossier",
                "  au-dessus de 0,55, decision REVIEW (decline-codes vide).",
                "",
                "Mesure automatique : python -m scripts.test_service",
                "Jeu long ISO : python scripts/build_dataset.py puis python -m scripts.eval_pad",
                "",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="Génère le jeu synthétique anti-spoof.")
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--per-attack", type=int, default=16)
    parser.add_argument("--authentic", type=int, default=24)
    parser.add_argument("--kit", action="store_true", help="Un JPEG par variante, pour Swagger.")
    args = parser.parse_args()
    if args.kit:
        dest = (args.out or KIT_OUT).resolve()
        manifest = write_kit(dest)
    else:
        if args.per_attack < 1 or args.authentic < 1:
            raise SystemExit("counts must be >= 1")
        dest = (args.out or DEFAULT_OUT).resolve()
        manifest = write_dataset(dest, args.per_attack, args.authentic)
    counts = manifest["counts"]
    print(f"wrote {dest}")
    print(" ".join(f"{label}={count}" for label, count in counts.items()))


if __name__ == "__main__":
    main()

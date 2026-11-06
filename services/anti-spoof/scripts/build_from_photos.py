"""Jeu synthétique posé sur des photos physiques. Hors git.

Les JPEG d'origine restent hors du dépôt. Chaque photo saine est copiée
telle quelle, puis les cinq traces d'attaque sont plantées dessus.

Usage, depuis services/anti-spoof :

    python scripts/build_from_photos.py --from C:\\Users\\Personnel\\Desktop\\echentillon\\original
    python -m scripts.eval_pad --root ..\\..\\data\\anti-spoof\\from-original-v1
    python -m scripts.report_service --root ..\\..\\data\\anti-spoof\\from-original-v1
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from scripts.build_dataset import ATTACKS, Sample, _decode, _roi, _save

VERSION = "from-original-v1"
REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_OUT = REPO_ROOT / "data" / "anti-spoof" / VERSION
SUFFIXES = {".jpg", ".jpeg"}
# Recaptures déjà identifiées dans ce dossier. Pas une base authentique.
SCREEN_MARKERS = ("9.11.14",)


def _box(
    width: int, height: int, left: int, top: int, right: int, bottom: int
) -> tuple[float, float, float, float]:
    return (left / width, top / height, (right - left) / width, (bottom - top) / height)


def load_photo(path: Path) -> np.ndarray:
    with Image.open(path) as image:
        oriented = ImageOps.exif_transpose(image)
        if oriented is None:
            oriented = image
        pixels = np.array(oriented.convert("RGB"), dtype=np.uint8, copy=True)
        if oriented is not image:
            oriented.close()
    return pixels


def list_photos(folder: Path) -> list[Path]:
    if not folder.is_dir():
        raise SystemExit(f"dossier absent: {folder}")
    return sorted(
        path for path in folder.iterdir() if path.is_file() and path.suffix.lower() in SUFFIXES
    )


def is_screen(path: Path) -> bool:
    name = path.name.lower()
    return any(marker.lower() in name for marker in SCREEN_MARKERS)


def authentic_of(jpeg: bytes, source: str) -> Sample:
    return Sample(
        jpeg=jpeg,
        primary="authentic",
        labels=("authentic",),
        variant="photo",
        regions=(),
        notes=f"Photo physique copiée sans ré-encodage. source={source}",
    )


def screen_real_of(jpeg: bytes, source: str) -> Sample:
    return Sample(
        jpeg=jpeg,
        primary="screen_replay",
        labels=("screen_replay",),
        variant="real_monitor",
        regions=(_roi("document", (0.0, 0.0, 1.0, 1.0)),),
        notes=f"Recapture écran du dossier source, sans overlay. source={source}",
    )


def splicing_on(pixels: np.ndarray, rng: np.random.Generator) -> Sample:
    canvas = pixels.astype(np.float32)
    height, width = canvas.shape[:2]
    size = min(160, height // 3, width // 3)
    yy, xx = np.mgrid[0:size, 0:size]
    patch = np.stack(
        [
            40 + 140 * (xx / size),
            30 + 80 * ((xx - 80) ** 2 + (yy - 60) ** 2) / 12800,
            200 - 120 * (yy / size),
        ],
        axis=2,
    )
    ax = int(rng.integers(16, max(17, width // 8)))
    ay = int(rng.integers(16, max(17, height // 8)))
    bx = min(width - size - 8, ax + max(200, width // 3))
    by = min(height - size - 8, ay + max(160, height // 4))
    canvas[ay : ay + size, ax : ax + size] = patch
    canvas[by : by + size, bx : bx + size] = patch
    return Sample(
        jpeg=_save(canvas, 92),
        primary="splicing",
        labels=("splicing",),
        variant="clone",
        regions=(
            _roi("portrait", _box(width, height, ax, ay, ax + size, ay + size)),
            _roi("document", _box(width, height, bx, by, bx + size, by + size)),
        ),
        notes="Même patch de gradient collé deux fois, à plus de 80 px.",
    )


def text_on(pixels: np.ndarray, rng: np.random.Generator, seed: int) -> Sample:
    height, width = pixels.shape[:2]
    soft = _decode(_save(pixels, 40, subsampling=1))
    band_h = 64
    top = int(rng.choice([height // 3, height // 2, (2 * height) // 5]))
    top = max(32, min((top // 32) * 32, height - band_h - 32))
    bottom = top + band_h
    left, right = max(24, width // 16), min(width - 24, width - width // 16)
    ink = int(rng.integers(12, 28))
    paper = int(np.median(soft[top:bottom, left:right]))
    fresh = np.full_like(soft, paper)
    x = left + 8
    row_y = top + 14
    glyphs = np.random.default_rng(seed + 50)
    while x < right - 20:
        gw = int(glyphs.integers(7, 16))
        gh = int(glyphs.integers(16, 24))
        fresh[row_y : row_y + gh, x : x + gw] = ink
        x += gw + int(glyphs.integers(5, 12))
    band = _decode(_save(fresh[top:bottom, left:right], 75, subsampling=1))
    soft[top : top + band.shape[0], left : left + band.shape[1]] = band
    return Sample(
        jpeg=_save(soft, 93, subsampling=1),
        primary="text_alteration",
        labels=("text_alteration", "compression_inconsistency"),
        variant="retouch",
        regions=(_roi("birth_date", _box(width, height, left, top, right, bottom)),),
        notes="Bande de glyphes reprise, ré-encodée à part.",
    )


def compression_on(pixels: np.ndarray, rng: np.random.Generator) -> Sample:
    height, width = pixels.shape[:2]
    soft = _decode(_save(pixels, 40, subsampling=1))
    band_h = 96
    top = int(rng.choice([height // 3, height // 2]))
    top = max(32, min((top // 32) * 32, height - band_h - 32))
    bottom = top + band_h
    left, right = max(32, width // 14), min(width - 32, width - width // 14)
    band = _decode(_save(soft[top:bottom, left:right], 90, subsampling=1))
    soft[top : top + band.shape[0], left : left + band.shape[1]] = band
    return Sample(
        jpeg=_save(soft, 93, subsampling=1),
        primary="compression_inconsistency",
        labels=("compression_inconsistency",),
        variant="local_band",
        regions=(_roi("document", _box(width, height, left, top, right, bottom)),),
        notes="Bande ré-encodée à une autre qualité, sur la photo.",
    )


def screen_on(pixels: np.ndarray, rng: np.random.Generator) -> Sample:
    height, width = pixels.shape[:2]
    period = float(rng.choice([6, 8, 10]))
    phase = 2 * np.pi * np.arange(width) / period
    grid = np.zeros((height, width, 3), np.float32)
    grid[:, :, 0] = 128 + 90 * np.sin(phase)
    grid[:, :, 1] = 128 + 90 * np.sin(phase + 2 * np.pi / 3)
    grid[:, :, 2] = 128 + 90 * np.sin(phase + 4 * np.pi / 3)
    mix = 0.62
    blended = (1.0 - mix) * pixels.astype(np.float32) + mix * grid
    return Sample(
        jpeg=_save(blended, 92),
        primary="screen_replay",
        labels=("screen_replay",),
        variant="rgb_grid",
        regions=(_roi("document", (0.0, 0.0, 1.0, 1.0)),),
        notes="Grille RVB de sous-pixels superposée à la photo.",
    )


def printed_on(pixels: np.ndarray, rng: np.random.Generator) -> Sample:
    image = pixels.astype(np.float32)
    height, width = image.shape[:2]
    step = int(rng.choice([10, 12, 14]))
    size = int(rng.choice([2, 3]))
    for y in range(0, height, step):
        for x in range(0, width, step):
            image[y : y + size, x : x + size] = 25
    return Sample(
        jpeg=_save(image, 92),
        primary="printed_copy",
        labels=("printed_copy",),
        variant="halftone",
        regions=(_roi("document", (0.0, 0.0, 1.0, 1.0)),),
        notes="Trame de points posée sur la photo.",
    )


def _record(
    folder: Path,
    label: str,
    index: int,
    sample: Sample,
    source: str,
) -> dict[str, object]:
    dest = folder / label
    dest.mkdir(parents=True, exist_ok=True)
    name = f"{index:03d}_{sample.variant}.jpg"
    path = dest / name
    path.write_bytes(sample.jpeg)
    return {
        "id": f"{label}_{index:03d}",
        "path": f"{label}/{name}",
        "primary": sample.primary,
        "labels": list(sample.labels),
        "variant": sample.variant,
        "regions": list(sample.regions),
        "notes": sample.notes,
        "source": source,
        "bytes": path.stat().st_size,
        "split": "eval",
    }


def write_from_photos(source: Path, out: Path) -> dict[str, object]:
    photos = list_photos(source)
    if not photos:
        raise SystemExit(f"aucun JPEG dans {source}")
    physical = [path for path in photos if not is_screen(path)]
    recaptures = [path for path in photos if is_screen(path)]
    if not physical:
        raise SystemExit(f"aucune photo physique dans {source}")
    out.mkdir(parents=True, exist_ok=True)
    samples: list[dict[str, object]] = []
    for index, path in enumerate(physical):
        source_name = path.name
        jpeg = path.read_bytes()
        pixels = load_photo(path)
        rng = np.random.default_rng(40_000 + index * 31)
        samples.append(_record(out, "authentic", index, authentic_of(jpeg, source_name), source_name))
        planted = (
            splicing_on(pixels, rng),
            text_on(pixels, rng, 40_000 + index),
            compression_on(pixels, rng),
            screen_on(pixels, rng),
            printed_on(pixels, rng),
        )
        for sample in planted:
            samples.append(_record(out, sample.primary, index, sample, source_name))
    for index, path in enumerate(recaptures):
        samples.append(
            _record(
                out,
                "screen_replay",
                len(physical) + index,
                screen_real_of(path.read_bytes(), path.name),
                path.name,
            )
        )
    counts = {
        "authentic": sum(1 for row in samples if row["primary"] == "authentic"),
        **{label: sum(1 for row in samples if row["primary"] == label) for label in ATTACKS},
    }
    manifest = {
        "version": VERSION,
        "kind": "synthetic-on-photo",
        "engine_target": "anti-spoof-1",
        "media_type": "image/jpeg",
        "source_dir": str(source.resolve()),
        "n_physical": len(physical),
        "n_screen_recapture": len(recaptures),
        "note": (
            "Attaques synthétiques posées sur des photos physiques. "
            "Hors git. Ne remplace pas le corridor annoté du contrat §12."
        ),
        "labels": ["authentic", *ATTACKS],
        "counts": counts,
        "samples": samples,
    }
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (out / "manifest.csv").write_text(
        "id,path,primary,labels,variant,source,split\n"
        + "\n".join(
            f"{row['id']},{row['path']},{row['primary']},{'|'.join(row['labels'])},"
            f"{row['variant']},{row['source']},{row['split']}"
            for row in samples
        )
        + "\n",
        encoding="utf-8",
    )
    (out / "LISEZMOI.txt").write_text(
        "\n".join(
            [
                "Jeu anti-spoof : photos physiques + attaques plantées.",
                "Les JPEG restent hors git (data/). Ne pas committer.",
                "",
                "authentic_* : copie de la photo, sans overlay.",
                "splicing_* / text_* / compression_* / screen_* / printed_* :",
                "  traces synthétiques posées sur la même photo.",
                "",
                "Mesure in-process : python -m scripts.eval_pad --root <ce dossier>",
                "Mesure HTTP     : python -m scripts.report_service --root <ce dossier>",
                "",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="Attaques synthétiques sur un dossier de photos.")
    parser.add_argument("--from", dest="source", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()
    dest = (args.out or DEFAULT_OUT).resolve()
    manifest = write_from_photos(args.source.resolve(), dest)
    counts = manifest["counts"]
    print(f"wrote {dest}")
    print(" ".join(f"{label}={count}" for label, count in counts.items()))


if __name__ == "__main__":
    main()

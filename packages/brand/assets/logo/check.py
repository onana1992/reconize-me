"""Contrôle des exports de la marque.

Deux vérifications, l'une chiffrée et l'autre visuelle :

1. les aplats opaques de chaque fichier doivent tomber sur une valeur de
   `tokens.css`, et le reste des pixels ne doit être que de l'antialiasing
   entre deux tokens — un vert à un point près trahit un export mal normalisé ;
2. `preview.png` compose tous les exports sur un damier et sur un bandeau
   sombre, seule façon de voir une frange blanche ou un contrepoinçon resté
   opaque.

    python packages/brand/assets/logo/check.py
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent

TOKENS = {
    (15, 122, 77): "--rm-accent",
    (255, 255, 255): "--rm-surface",
    (17, 26, 21): "--rm-text",
    (244, 247, 245): "--rm-bg",
}

AUDIT = [
    "mark-accent-512.png",
    "mark-accent-64.png",
    "mark-accent-32.png",
    "mark-inverse-512.png",
    "mark-mono-512.png",
    "lockup-accent.png",
    "lockup-inverse.png",
    "lockup-stacked.png",
    "avatar-512.png",
    "og-1200x630.png",
]

# Fichier, coin haut-gauche, largeur de rendu. Le bandeau sombre couvre y 300-470.
SHEET = [
    ("mark-accent-512.png", 40, 40, 220),
    ("mark-mono-512.png", 300, 40, 220),
    ("mark-accent-64.png", 560, 40, 64),
    ("mark-accent-32.png", 560, 130, 32),
    ("lockup-accent.png", 660, 90, 700),
    ("lockup-inverse.png", 40, 330, 620),
    ("mark-inverse-512.png", 700, 320, 130),
    ("lockup-stacked.png", 40, 510, 300),
    ("avatar-512.png", 400, 510, 220),
    ("og-1200x630.png", 40, 860, 700),
]


def _off_segment(colours, tolerance=3.0):
    """Écart de chaque couleur au plus proche mélange de deux tokens.

    L'antialiasing d'un dessin plat vit sur les segments qui joignent les
    couleurs de la charte. Ce qui s'en écarte est une couleur inventée.
    """
    palette = np.asarray(list(TOKENS), dtype=np.float64)
    best = np.full(len(colours), np.inf)
    for i in range(len(palette)):
        for j in range(i + 1, len(palette)):
            segment = palette[j] - palette[i]
            offset = colours - palette[i]
            ratio = np.clip((offset * segment).sum(1) / (segment**2).sum(), 0.0, 1.0)[:, None]
            best = np.minimum(best, np.linalg.norm(offset - ratio * segment, axis=1))
    return best > tolerance


def audit():
    failures = 0
    for name in AUDIT:
        image = Image.open(ROOT / name).convert("RGBA")
        pixels = np.asarray(image).reshape(-1, 4)
        opaque = pixels[pixels[:, 3] > 250][:, :3]
        values, counts = np.unique(opaque, axis=0, return_counts=True)

        flat = tuple(int(channel) for channel in values[counts.argmax()])
        token = TOKENS.get(flat)
        stray = counts[_off_segment(values.astype(np.float64))].sum() / len(opaque)
        verdict = "ok" if token and stray < 0.005 else "ECHEC"
        failures += verdict == "ECHEC"

        print(
            f"{verdict:5}  {name:22} {image.width:>4}x{image.height:<4}"
            f"  aplat #{flat[0]:02X}{flat[1]:02X}{flat[2]:02X} {token or 'hors charte'}"
            f"  hors segments {stray:.2%}"
        )
    return failures


def sheet():
    canvas = Image.new("RGB", (1400, 1180), (255, 255, 255))
    draw = ImageDraw.Draw(canvas)
    for y in range(0, 1180, 16):
        for x in range(0, 1400, 16):
            if (x // 16 + y // 16) % 2:
                draw.rectangle((x, y, x + 16, y + 16), fill=(214, 214, 214))
    draw.rectangle((0, 300, 1400, 470), fill=(8, 77, 50))

    for name, x, y, width in SHEET:
        export = Image.open(ROOT / name).convert("RGBA")
        height = round(width * export.height / export.width)
        scaled = export.resize((width, height), Image.LANCZOS)
        canvas.paste(scaled, (x, y), scaled)

    canvas.save(ROOT / "preview.png", optimize=True)
    print("planche : preview.png")


if __name__ == "__main__":
    failures = audit()
    sheet()
    raise SystemExit(1 if failures else 0)

"""Exports raster de la marque Recogniz-Me.

`source/` contient les masters raster du sceau. Ce script en produit les
exports livrables : couleurs ramenées aux valeurs exactes des tokens,
silhouette du sceau redessinée géométriquement, fond rendu transparent,
tailles dérivées.

    python packages/brand/assets/logo/build.py
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "source"

# Recopie de packages/brand/tokens.css. Toute divergence est un bug.
ACCENT = (15, 122, 77)  # --rm-accent  #0F7A4D
WHITE = (255, 255, 255)  # --rm-surface #FFFFFF
INK = (17, 26, 21)  # --rm-text    #111A15
PAGE = (244, 247, 245)  # --rm-bg      #F4F7F5

SUPERSAMPLE = 4


def _distances(pixels, centers):
    return np.stack([((pixels - c) ** 2).sum(axis=1) for c in centers], axis=1)


def _flat_colours(pixels, targets):
    """Aplats réellement produits par le modèle génératif, un par couleur cible.

    On prend la couleur *modale* de chaque groupe et non sa moyenne : la moyenne
    inclut les pixels d'antialiasing et tire l'aplat de plusieurs points, ce qui
    décale ensuite toute la correction.
    """
    groups = np.argmin(_distances(pixels, np.asarray(targets, dtype=np.float64)), axis=1)
    packed = (pixels[:, 0].astype(np.int64) << 16) | (pixels[:, 1].astype(np.int64) << 8) | pixels[:, 2].astype(np.int64)

    colours = []
    for index in range(len(targets)):
        values, counts = np.unique(packed[groups == index], return_counts=True)
        dominant = int(values[counts.argmax()])
        colours.append(((dominant >> 16) & 255, (dominant >> 8) & 255, dominant & 255))
    return np.asarray(colours, dtype=np.float64)


def snap(image, palette, deadzone=0.05):
    """Ramène une image quasi-plate sur une palette exacte sans casser l'antialiasing.

    Chaque pixel est projeté sur le segment qui joint ses deux couleurs source
    les plus proches, puis ré-interpolé entre les deux couleurs cibles. La zone
    morte aux deux extrémités absorbe le bruit du modèle : sans elle, un aplat
    qui varie de trois points ressort à côté de la valeur du token.
    """
    targets = np.asarray(palette, dtype=np.float64)
    raw = np.asarray(image.convert("RGB")).reshape(-1, 3)
    sources = _flat_colours(raw[::7], targets)
    flat = raw.astype(np.float64)

    order = np.argsort(_distances(flat, sources), axis=1)
    near, far = order[:, 0], order[:, 1]
    origin, segment = sources[near], sources[far] - sources[near]
    ratio = ((flat - origin) * segment).sum(axis=1) / np.maximum((segment**2).sum(axis=1), 1e-9)
    ratio = np.clip((ratio - deadzone) / (1 - 2 * deadzone), 0.0, 1.0)[:, None]

    mixed = targets[near] * (1 - ratio) + targets[far] * ratio
    pixels = np.round(mixed).astype(np.uint8).reshape(image.height, image.width, 3)
    return Image.fromarray(pixels, "RGB")


def coverage(image, background, ink):
    """Part d'encre par pixel, pour un dessin d'une seule couleur sur fond plat."""
    flat = np.asarray(image.convert("RGB"), dtype=np.float64)
    origin = np.asarray(background, dtype=np.float64)
    segment = np.asarray(ink, dtype=np.float64) - origin
    ratio = ((flat - origin) * segment).sum(axis=2) / (segment**2).sum()
    return np.clip(ratio, 0.0, 1.0)


def alpha_layer(ratio, floor=0.06):
    """Couche alpha tirée d'une couverture d'encre.

    Le plancher évacue le bruit de quantification du fond — un blanc à 254 sur
    255 produit une couverture non nulle sur toute l'image — puis la plage
    restante est réétalée pour que l'encre pleine reste parfaitement opaque.
    """
    cleaned = np.clip((ratio - floor) / (1 - floor), 0.0, 1.0)
    return Image.fromarray(np.round(cleaned * 255).astype(np.uint8), "L")


def ink_mask(image, background, tolerance=60):
    """Pixels qui s'écartent franchement du fond."""
    flat = np.asarray(image.convert("RGB"), dtype=np.int16)
    return np.abs(flat - np.asarray(background, dtype=np.int16)).max(axis=2) > tolerance


def first_block(mask, axis):
    """Boîte du premier bloc de dessin. Le sceau précède toujours le nom.

    `axis="x"` balaie de gauche à droite (lockup horizontal), `axis="y"` de haut
    en bas (lockup empilé).
    """
    occupied = mask.any(axis=0 if axis == "x" else 1)
    filled = np.nonzero(occupied)[0]
    start = end = filled[0]
    while end + 1 < len(occupied) and occupied[end + 1]:
        end += 1

    block = mask[:, start : end + 1] if axis == "x" else mask[start : end + 1, :]
    across = np.nonzero(block.any(axis=1 if axis == "x" else 0))[0]
    if axis == "x":
        return (start, across[0], end + 1, across[-1] + 1)
    return (across[0], start, across[-1] + 1, end + 1)


def corner_ratio(mask):
    """Rayon de l'arrondi rapporté au côté, mesuré sur les lignes extrêmes du sceau.

    Sur un rectangle arrondi, la première et la dernière ligne dessinées
    s'étendent exactement de `r` à `côté - r`.
    """
    rows = np.nonzero(mask.any(axis=1))[0]
    columns = np.nonzero(mask.any(axis=0))[0]
    side = columns[-1] - columns[0] + 1

    insets = []
    for row in (mask[rows[0]], mask[rows[-1]]):
        filled = np.nonzero(row)[0]
        insets += [filled[0] - columns[0], columns[-1] - filled[-1]]
    return float(np.clip(np.mean(insets) / side, 0.18, 0.34))


def rounded_mask(size, radius):
    """Silhouette du sceau, redessinée : la source générative a des angles mous."""
    width, height = size
    canvas = Image.new("L", (width * SUPERSAMPLE, height * SUPERSAMPLE), 0)
    ImageDraw.Draw(canvas).rounded_rectangle(
        (0, 0, width * SUPERSAMPLE - 1, height * SUPERSAMPLE - 1),
        radius=radius * SUPERSAMPLE,
        fill=255,
    )
    return canvas.resize(size, Image.LANCZOS)


def save(image, name):
    image.save(ROOT / name, optimize=True)
    print(f"  {name}  {image.size[0]}x{image.size[1]}")


def export_seal(tile, ratio, palette, name, sizes):
    """Le sceau, silhouette redessinée à chaque taille pour des angles nets.

    La palette est réappliquée après chaque réduction : LANCZOS dépasse aux
    transitions et fabrique des verts qui ne sont dans aucun token.
    """
    for size in sizes:
        seal = snap(tile.resize((size, size), Image.LANCZOS), palette).convert("RGBA")
        seal.putalpha(rounded_mask((size, size), ratio * size))
        save(seal, f"{name}-{size}.png")


def seal_from(source, background, palette, axis="x"):
    """Isole le sceau d'une source, couleurs corrigées et arrondi mesuré.

    La découpe est serrée sur le sceau : le masque de silhouette doit couvrir
    exactement l'image, sinon il rogne le vert ou laisse un liseré de fond.
    """
    flat = snap(Image.open(SOURCE / source).convert("RGB"), palette)
    tile = flat.crop(first_block(ink_mask(flat, background), axis))
    return tile, corner_ratio(ink_mask(tile, background))


def lockup_from(source, background, palette, glyph, axis="x"):
    """Lockup détouré : sceau redessiné, nom réduit à une couche d'encre.

    Le nom est traité comme une couverture d'encre plutôt que découpé, sinon les
    contrepoinçons des lettres restent opaques. La palette est appliquée avant
    tout : un fond à 254 laisserait une couverture résiduelle sur toute l'image.
    """
    flat = snap(Image.open(SOURCE / source).convert("RGB"), palette)
    box = first_block(ink_mask(flat, background), axis)

    alpha = coverage(flat, background, glyph)
    alpha[box[1] : box[3], box[0] : box[2]] = 0.0
    wordmark = Image.new("RGBA", flat.size, glyph + (0,))
    wordmark.putalpha(alpha_layer(alpha))

    tile = flat.crop(box).convert("RGBA")
    ratio = corner_ratio(ink_mask(tile.convert("RGB"), background))
    tile.putalpha(rounded_mask(tile.size, ratio * min(tile.size)))

    wordmark.alpha_composite(tile, (box[0], box[1]))
    return wordmark.crop(wordmark.getbbox())


def main():
    print("Sceau")
    tile, ratio = seal_from("logo-mark-accent.png", WHITE, (WHITE, ACCENT))
    export_seal(tile, ratio, (WHITE, ACCENT), "mark-accent", (512, 256, 128, 64, 40, 32))
    save(
        Image.open(ROOT / "mark-accent-40.png"),
        "logo-40.png",  # nom attendu par packages/brand/email/base.html
    )

    inverse, inverse_ratio = seal_from("logo-lockup-inverse.png", ACCENT, (ACCENT, WHITE))
    export_seal(inverse, inverse_ratio, (ACCENT, WHITE), "mark-inverse", (512, 128))

    raw_mono = snap(Image.open(SOURCE / "logo-mark-mono.png").convert("RGB"), (WHITE, INK))
    crop = raw_mono.crop(first_block(ink_mask(raw_mono, WHITE), "x"))
    mono = Image.new("RGBA", crop.size, INK + (0,))
    mono.putalpha(alpha_layer(coverage(crop, WHITE, INK)))
    for size in (512, 128):
        save(mono.resize((size, size), Image.LANCZOS), f"mark-mono-{size}.png")

    print("Lockups")
    horizontal = lockup_from("logo-lockup-accent.png", WHITE, (WHITE, ACCENT, INK), INK)
    save(horizontal, "lockup-accent.png")
    save(lockup_from("logo-lockup-inverse.png", ACCENT, (ACCENT, WHITE), WHITE), "lockup-inverse.png")
    save(
        lockup_from("logo-lockup-stacked.png", WHITE, (WHITE, ACCENT, INK), INK, axis="y"),
        "lockup-stacked.png",
    )

    print("Réseaux sociaux")
    avatar = Image.open(SOURCE / "logo-avatar.png").convert("RGB")
    for size in (1024, 512):
        save(snap(avatar.resize((size, size), Image.LANCZOS), (ACCENT, WHITE)), f"avatar-{size}.png")

    banner = Image.new("RGB", (1200, 630), PAGE)
    width = round(1200 * 0.55)
    lockup = horizontal.resize((width, round(width * horizontal.height / horizontal.width)), Image.LANCZOS)
    banner.paste(lockup, ((1200 - lockup.width) // 2, (630 - lockup.height) // 2), lockup)
    save(snap(banner, (PAGE, ACCENT, INK, WHITE)), "og-1200x630.png")


if __name__ == "__main__":
    main()

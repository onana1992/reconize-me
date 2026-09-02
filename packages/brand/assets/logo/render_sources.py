"""Dessine les sources raster du sceau à partir de la géométrie SVG (viewBox 32).

    python packages/brand/assets/logo/render_sources.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "source"

ACCENT = (15, 122, 77)
WHITE = (255, 255, 255)
INK = (17, 26, 21)
PAGE = (244, 247, 245)

STEM_X, STEM_TOP, STEM_BOT = 10.7, 9.55, 22.15
BOWL_RIGHT, BOWL_BOT = 17.15, 17.15
CHECK = [(12.35, 17.05), (15.45, 21.35), (22.05, 11.95)]
RING_R, RING_W, TILE_RX, GLYPH_W = 10.5, 1.5, 9.0, 2.6
FONT = Path(r"C:\Windows\Fonts\seguisb.ttf")
SS = 4


def mix(fg, bg, a):
    return tuple(round(a * f + (1 - a) * b) for f, b in zip(fg, bg))


def _draw(size, tile, glyph, ring, outline=False, bleed=False):
    s = size / 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w = max(2, round(GLYPH_W * s))
    rad = w / 2

    if bleed:
        d.rectangle((0, 0, size - 1, size - 1), fill=tile)
    elif outline:
        inset = round(0.75 * s)
        d.rounded_rectangle(
            (inset, inset, size - 1 - inset, size - 1 - inset),
            radius=TILE_RX * s,
            outline=glyph,
            width=max(1, round(1.5 * s)),
        )
    else:
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=TILE_RX * s, fill=tile)

    box = ((16 - RING_R) * s, (16 - RING_R) * s, (16 + RING_R) * s, (16 + RING_R) * s)
    d.ellipse(box, outline=ring, width=max(1, round(RING_W * s)))

    def cap(x, y):
        d.ellipse((x - rad, y - rad, x + rad, y + rad), fill=glyph)

    def polyline(points):
        scaled = [(p[0] * s, p[1] * s) for p in points]
        d.line(scaled, fill=glyph, width=w, joint="curve")
        cap(*scaled[0])
        cap(*scaled[-1])

    polyline([(STEM_X, STEM_BOT), (STEM_X, STEM_TOP)])
    cx, cy = STEM_X * s, (STEM_TOP + BOWL_BOT) / 2 * s
    rx, ry = (BOWL_RIGHT - STEM_X) * s, (BOWL_BOT - STEM_TOP) / 2 * s
    d.arc((cx - rx, cy - ry, cx + rx, cy + ry), start=270, end=90, fill=glyph, width=w)
    cap(STEM_X * s, STEM_TOP * s)
    cap(STEM_X * s, BOWL_BOT * s)
    polyline(CHECK)
    return img


def mark(size, tile, glyph, ring, outline=False, bleed=False):
    big = _draw(size * SS, tile, glyph, ring, outline=outline, bleed=bleed)
    return big.resize((size, size), Image.LANCZOS)


def paste_on(canvas, glyph, xy):
    canvas.paste(glyph, xy, glyph)


def word(text, size, color):
    font = ImageFont.truetype(str(FONT), size)
    dummy = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    box = dummy.textbbox((0, 0), text, font=font)
    img = Image.new("RGBA", (box[2] - box[0] + 4, box[3] - box[1] + 4), (0, 0, 0, 0))
    ImageDraw.Draw(img).text((2 - box[0], 2 - box[1]), text, font=font, fill=color)
    return img


def save_rgb(image, name, background):
    flat = Image.new("RGB", image.size, background)
    flat.paste(image, mask=image.split()[-1] if image.mode == "RGBA" else None)
    path = SOURCE / name
    flat.save(path, optimize=True)
    print(f"  {name}  {flat.size[0]}x{flat.size[1]}")


def main():
    SOURCE.mkdir(exist_ok=True)
    print("Sources")

    accent_ring = mix(WHITE, ACCENT, 0.35)
    inverse_ring = mix(ACCENT, WHITE, 0.35)
    mono_ring = mix(INK, WHITE, 0.35)

    seal = mark(640, ACCENT, WHITE, accent_ring)
    canvas = Image.new("RGBA", (1024, 1024), WHITE + (255,))
    paste_on(canvas, seal, ((1024 - 640) // 2, (1024 - 640) // 2))
    save_rgb(canvas, "logo-mark-accent.png", WHITE)

    mono = mark(640, None, INK, mono_ring, outline=True)
    canvas = Image.new("RGBA", (1024, 1024), WHITE + (255,))
    paste_on(canvas, mono, ((1024 - 640) // 2, (1024 - 640) // 2))
    save_rgb(canvas, "logo-mark-mono.png", WHITE)

    avatar = mark(1024, ACCENT, WHITE, accent_ring, bleed=True)
    save_rgb(avatar.convert("RGBA"), "logo-avatar.png", ACCENT)

    seal_h = 280
    gap = 48
    name = word("Recogniz-Me", 118, INK)
    seal_sm = mark(seal_h, ACCENT, WHITE, accent_ring)
    width, height = 1536, 1024
    lock = Image.new("RGBA", (width, height), WHITE + (255,))
    total = seal_h + gap + name.size[0]
    x = (width - total) // 2
    y_seal = (height - seal_h) // 2
    y_name = (height - name.size[1]) // 2
    paste_on(lock, seal_sm, (x, y_seal))
    paste_on(lock, name, (x + seal_h + gap, y_name))
    save_rgb(lock, "logo-lockup-accent.png", WHITE)

    name_w = word("Recogniz-Me", 118, WHITE)
    inv_seal = mark(seal_h, WHITE, ACCENT, inverse_ring)
    inv = Image.new("RGBA", (width, height), ACCENT + (255,))
    paste_on(inv, inv_seal, (x, y_seal))
    paste_on(inv, name_w, (x + seal_h + gap, y_name))
    save_rgb(inv, "logo-lockup-inverse.png", ACCENT)

    stacked_seal = mark(300, ACCENT, WHITE, accent_ring)
    stacked_name = word("Recogniz-Me", 92, INK)
    stacked_gap = 36
    st = Image.new("RGBA", (1024, 1024), WHITE + (255,))
    total_h = 300 + stacked_gap + stacked_name.size[1]
    sy = (1024 - total_h) // 2
    paste_on(st, stacked_seal, ((1024 - 300) // 2, sy))
    paste_on(st, stacked_name, ((1024 - stacked_name.size[0]) // 2, sy + 300 + stacked_gap))
    save_rgb(st, "logo-lockup-stacked.png", WHITE)

    banner = Image.new("RGBA", (1536, 1024), PAGE + (255,))
    banner_lock = lock.resize((900, round(900 * lock.size[1] / lock.size[0])), Image.LANCZOS)
    paste_on(banner, banner_lock, ((1536 - banner_lock.size[0]) // 2, (1024 - banner_lock.size[1]) // 2))
    save_rgb(banner, "logo-og-banner.png", PAGE)


if __name__ == "__main__":
    main()

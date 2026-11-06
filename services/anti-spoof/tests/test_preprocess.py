"""Prétraitement : EXIF en mémoire, seuils, JPEG non ré-écrit."""

import io

from PIL import Image

from app.preprocess import MAX_PIXELS, prepare


def _jpeg(width: int, height: int, orientation: int | None = None) -> bytes:
    image = Image.new("RGB", (width, height), (8, 16, 32))
    buffer = io.BytesIO()
    if orientation is None:
        image.save(buffer, format="JPEG", quality=80)
    else:
        exif = image.getexif()
        exif[274] = orientation
        image.save(buffer, format="JPEG", quality=80, exif=exif)
    return buffer.getvalue()


def test_exif_orientation_swaps_sides_without_rewriting_bytes() -> None:
    raw = _jpeg(600, 480, orientation=6)
    snapshot = bytes(raw)
    prepared = prepare(raw)
    assert raw == snapshot
    assert raw.startswith(b"\xff\xd8")
    assert (prepared.width, prepared.height) == (480, 600)
    assert prepared.usable is True


def test_side_under_480_is_not_usable() -> None:
    prepared = prepare(_jpeg(479, 480))
    assert prepared.usable is False
    assert prepared.width == 479


def test_pixel_ceiling_is_12_megapixels() -> None:
    assert 4000 * 3000 == MAX_PIXELS
    assert prepare(_jpeg(480, 480)).usable is True
    wide = _jpeg(5000, 2500)
    prepared = prepare(wide)
    assert prepared.width * prepared.height > MAX_PIXELS
    assert prepared.usable is False

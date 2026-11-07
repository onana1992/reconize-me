"""Artefacts partagés : ELA, tables JPEG, recompression globale."""

import io

import numpy as np
from PIL import Image

from app.forensic.ela import map_of
from app.jpeg import inspect, quantization_tables


def _jpeg(pixels: np.ndarray, quality: int) -> bytes:
    buffer = io.BytesIO()
    Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8), "RGB").save(
        buffer, format="JPEG", quality=quality
    )
    return buffer.getvalue()


def test_quantization_tables_are_read_from_the_original_bytes() -> None:
    raw = _jpeg(np.full((480, 640, 3), 140, np.uint8), 80)
    tables = quantization_tables(raw)
    assert tables
    assert len(tables[0]) == 64


def test_global_recompress_has_no_grid_shift() -> None:
    first = _jpeg(np.full((480, 640, 3), 180, np.uint8), 90)
    with Image.open(io.BytesIO(first)) as image:
        pixels = np.array(image.convert("RGB"), dtype=np.uint8)
    second = _jpeg(pixels, 60)
    with Image.open(io.BytesIO(second)) as image:
        pixels = np.array(image.convert("RGB"), dtype=np.uint8)
    info = inspect(second, pixels)
    assert info.shifted_share == 0.0


def test_ela_is_computed_in_memory() -> None:
    pixels = np.full((64, 64, 3), 200, np.uint8)
    ela = map_of(pixels)
    assert ela.shape == (64, 64)
    assert float(ela.mean()) >= 0.0

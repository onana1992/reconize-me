"""Artefacts calculés une fois par appel. Les détecteurs les lisent, ils ne les recréent pas."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.ndimage import gaussian_filter

from app.detectors.spectrum import MAX_SIDE, limit_side
from app.forensic.ocr_boxes import Glyph, detect as glyph_boxes
from app.jpeg import JpegInfo, inspect, luma


@dataclass(frozen=True)
class Artifacts:
    original: bytes
    pixels: np.ndarray
    luma: np.ndarray
    residual: np.ndarray
    ela: np.ndarray
    jpeg: JpegInfo
    glyphs: tuple[Glyph, ...]


def build(
    original: bytes,
    pixels: np.ndarray,
    text_hints: list[tuple[str, tuple[float, float, float, float]]] | None = None,
) -> Artifacts:
    view = limit_side(pixels) if max(pixels.shape[:2]) > MAX_SIDE else pixels
    gray = luma(view)
    residual = gray - gaussian_filter(gray, 1.2)
    jpeg = inspect(original, view)
    ela = jpeg.ela
    glyphs = glyph_boxes(view, ela, text_hints)
    return Artifacts(
        original=original,
        pixels=view,
        luma=gray,
        residual=residual,
        ela=ela,
        jpeg=jpeg,
        glyphs=glyphs,
    )

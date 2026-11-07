"""Boîtes de glyphes. Le texte Tesseract est jeté immédiatement."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from scipy.ndimage import binary_opening, label

_TEXT_HINTS = {"mrz", "birth_date", "document_number", "surname", "given_names"}
_OCR_TIMEOUT_S = 0.3


@dataclass(frozen=True)
class Glyph:
    box: tuple[float, float, float, float]
    confidence: float
    cx: float
    cy: float
    stroke: float
    ela: float
    name: str


def detect(
    pixels: np.ndarray,
    ela: np.ndarray,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None = None,
) -> tuple[Glyph, ...]:
    """Tesseract si le binaire est là, sinon contours. Aucune chaîne n'est gardée."""
    found = _tesseract(pixels, ela, hints)
    if found:
        return found
    return _contours(pixels, ela, hints)


def _tesseract(
    pixels: np.ndarray,
    ela: np.ndarray,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[Glyph, ...]:
    try:
        import pytesseract
    except ImportError:
        return ()
    crops = _crops(pixels, hints)
    glyphs: list[Glyph] = []
    height, width = pixels.shape[:2]
    for name, image, origin in crops:
        try:
            data = pytesseract.image_to_data(
                image,
                output_type=pytesseract.Output.DICT,
                timeout=_OCR_TIMEOUT_S,
                config="--psm 6",
            )
        except Exception:
            continue
        count = len(data.get("left", []))
        for index in range(count):
            text = data.get("text", [""])[index]
            if isinstance(text, str):
                data["text"][index] = ""
            try:
                conf = float(data["conf"][index])
            except (TypeError, ValueError, KeyError):
                continue
            if conf < 30:
                continue
            left = int(data["left"][index]) + origin[0]
            top = int(data["top"][index]) + origin[1]
            box_w = int(data["width"][index])
            box_h = int(data["height"][index])
            glyph = _from_rect(left, top, box_w, box_h, width, height, ela, conf / 100.0, name)
            if glyph is not None:
                glyphs.append(glyph)
    return tuple(glyphs)


def _contours(
    pixels: np.ndarray,
    ela: np.ndarray,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> tuple[Glyph, ...]:
    gray = 0.299 * pixels[:, :, 0] + 0.587 * pixels[:, :, 1] + 0.114 * pixels[:, :, 2]
    height, width = gray.shape
    ink = binary_opening(gray < 80, iterations=1)
    labeled, count = label(ink)
    if count > 250:
        return ()
    glyphs: list[Glyph] = []
    min_area = 24
    max_area = 0.08 * height * width
    for index in range(1, int(count) + 1):
        ys, xs = np.nonzero(labeled == index)
        area = int(ys.size)
        if area < min_area or area > max_area:
            continue
        left, right = int(xs.min()), int(xs.max()) + 1
        top, bottom = int(ys.min()), int(ys.max()) + 1
        box_w, box_h = right - left, bottom - top
        if box_h < 8 or box_w < 3 or box_h > 80 or box_w > 80:
            continue
        if box_w > 4 * box_h:
            continue
        if area / (box_w * box_h) < 0.45:
            continue
        cx = (left + right) / 2 / width
        cy = (top + bottom) / 2 / height
        name = _name((cx, cy), hints)
        glyph = _from_rect(left, top, box_w, box_h, width, height, ela, 0.6, name)
        if glyph is not None:
            glyphs.append(glyph)
    return tuple(glyphs)


def _crops(
    pixels: np.ndarray,
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> list[tuple[str, np.ndarray, tuple[int, int]]]:
    height, width = pixels.shape[:2]
    if not hints:
        return [("document", pixels, (0, 0))]
    crops: list[tuple[str, np.ndarray, tuple[int, int]]] = []
    for name, box in hints:
        if name not in _TEXT_HINTS:
            continue
        x, y, box_w, box_h = box
        left = max(0, int(x * width) - 4)
        top = max(0, int(y * height) - 4)
        right = min(width, int((x + box_w) * width) + 4)
        bottom = min(height, int((y + box_h) * height) + 4)
        if right - left < 8 or bottom - top < 8:
            continue
        crops.append((name, pixels[top:bottom, left:right], (left, top)))
    return crops or [("document", pixels, (0, 0))]


def _from_rect(
    left: int,
    top: int,
    box_w: int,
    box_h: int,
    width: int,
    height: int,
    ela: np.ndarray,
    confidence: float,
    name: str,
) -> Glyph | None:
    right = min(width, left + box_w)
    bottom = min(height, top + box_h)
    if right <= left or bottom <= top:
        return None
    patch = ela[top:bottom, left:right]
    stroke = min(box_w, box_h) / 2.0
    return Glyph(
        box=(left / width, top / height, (right - left) / width, (bottom - top) / height),
        confidence=float(confidence),
        cx=(left + right) / 2 / width,
        cy=(top + bottom) / 2 / height,
        stroke=float(stroke),
        ela=float(patch.mean()) if patch.size else 0.0,
        name=name,
    )


def _name(
    center: tuple[float, float],
    hints: list[tuple[str, tuple[float, float, float, float]]] | None,
) -> str:
    if not hints:
        return "document"
    x, y = center
    for name, box in hints:
        if name not in _TEXT_HINTS:
            continue
        left, top, width, height = box
        if left <= x < left + width and top <= y < top + height:
            return name
    return "document"

"""Orientation EXIF en mémoire. Le JPEG d'origine n'est pas ré-écrit."""

from __future__ import annotations

import io
from dataclasses import dataclass

import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

# 8 Mio. Le contrat dit « 8 Mo » pour le corps HTTP.
MAX_BODY_BYTES = 8 * 1024 * 1024
MIN_SIDE = 480
MAX_PIXELS = 12_000_000

# Orientations EXIF qui échangent largeur et hauteur.
_SWAP_AXES = {5, 6, 7, 8}


class InvalidImage(Exception):
    """Les octets ne sont pas un JPEG décodable."""


class PayloadTooLarge(Exception):
    """Le corps dépasse 8 Mio."""


@dataclass(frozen=True)
class Prepared:
    width: int
    height: int
    usable: bool
    pixels: np.ndarray | None = None


def fits(width: int, height: int) -> bool:
    return min(width, height) >= MIN_SIDE and width * height <= MAX_PIXELS


def prepare(original: bytes) -> Prepared:
    """Décode le JPEG et redresse l'orientation. `original` n'est pas modifié.

    Une image trop petite ou au-dessus de 12 mégapixels reste un 200 :
    `usable` est faux et les détecteurs ne sont pas appelés.
    """
    if not original:
        raise InvalidImage()
    try:
        with Image.open(io.BytesIO(original)) as image:
            if image.format != "JPEG":
                raise InvalidImage()
            width, height = _oriented_size(image)
            if not fits(width, height):
                return Prepared(width=width, height=height, usable=False)
            oriented = ImageOps.exif_transpose(image)
            if oriented is None:
                raise InvalidImage()
            pixels = np.array(oriented.convert("RGB"), dtype=np.uint8, copy=True)
            width, height = pixels.shape[1], pixels.shape[0]
            if oriented is not image:
                oriented.close()
    except InvalidImage:
        raise
    except (UnidentifiedImageError, OSError, ValueError):
        raise InvalidImage() from None
    return Prepared(width=width, height=height, usable=fits(width, height), pixels=pixels)


def _oriented_size(image: Image.Image) -> tuple[int, int]:
    width, height = image.size
    try:
        orientation = int(image.getexif().get(274, 1))
    except (TypeError, ValueError):
        return width, height
    if orientation in _SWAP_AXES:
        return height, width
    return width, height

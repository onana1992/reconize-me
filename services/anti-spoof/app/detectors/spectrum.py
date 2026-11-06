"""Spectre commun à l'écran et au papier. Le JPEG d'origine n'est pas ré-écrit."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

MAX_SIDE = 1600


@dataclass(frozen=True)
class Peak:
    fraction: float
    spread: float


def limit_side(pixels: np.ndarray) -> np.ndarray:
    height, width = pixels.shape[:2]
    long_side = max(height, width)
    if long_side <= MAX_SIDE:
        return pixels
    scale = MAX_SIDE / long_side
    resized = Image.fromarray(pixels, "RGB").resize(
        (max(1, int(width * scale)), max(1, int(height * scale))),
        Image.Resampling.BILINEAR,
    )
    return np.asarray(resized, dtype=np.uint8)


def crop(pixels: np.ndarray, box: tuple[float, float, float, float]) -> np.ndarray:
    height, width = pixels.shape[:2]
    x, y, box_w, box_h = box
    left = min(width - 1, max(0, int(x * width)))
    top = min(height - 1, max(0, int(y * height)))
    right = min(width, max(left + 1, int((x + box_w) * width)))
    bottom = min(height, max(top + 1, int((y + box_h) * height)))
    return pixels[top:bottom, left:right]


def peaks_of(pixels: np.ndarray, period_max: float = 28.0) -> list[Peak]:
    """Pics étroits du spectre, du plus fort au plus faible. Au plus six.

    ``period_max`` est la plus grande période admise, en pixels. Le papier
    reste à 28. L'écran regarde aussi le moiré plus large.
    """
    rgb = pixels.astype(np.float32)
    if rgb.shape[0] < 64 or rgb.shape[1] < 64:
        return []
    gray = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    height, width = gray.shape
    window = np.outer(np.hanning(height), np.hanning(width))
    channels = [np.fft.fft2((rgb[:, :, index] - rgb[:, :, index].mean()) * window) for index in range(3)]
    mag = np.abs(np.fft.fftshift(np.fft.fft2((gray - gray.mean()) * window)))
    cy, cx = height // 2, width // 2
    yy, xx = np.ogrid[:height, :width]
    radius = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
    short = min(height, width)
    band = (radius >= short / period_max) & (radius <= short / 4)
    if not np.any(band):
        return []
    energy = float(mag[band].sum()) + 1e-6
    floor = float(np.percentile(mag[band], 95)) + 1e-6
    work = mag.copy()
    work[~band] = 0
    found: list[Peak] = []
    for _ in range(6):
        index = int(np.argmax(work))
        value = float(work.flat[index])
        if value < floor * 3:
            break
        y, x = np.unravel_index(index, work.shape)
        ky = (int(y) - cy) % height
        kx = (int(x) - cx) % width
        angles = np.angle([channel[ky, kx] for channel in channels])
        spread = float(1.0 - np.abs(np.exp(1j * angles).mean()))
        found.append(Peak(fraction=value / energy, spread=spread))
        y = int(y)
        x = int(x)
        work[max(0, y - 5) : y + 6, max(0, x - 5) : x + 6] = 0
        sy = int(np.clip(2 * cy - y, 0, height - 1))
        sx = int(np.clip(2 * cx - x, 0, width - 1))
        work[max(0, sy - 5) : sy + 6, max(0, sx - 5) : sx + 6] = 0
    return found


def grain_of(pixels: np.ndarray) -> float:
    """Variance lisse, hors contours. Seul, le score plafonne sous 0,80.

    Un glyphe est un contour. Il ne compte pas comme une fibre de papier.
    """
    if pixels.shape[0] < 64 or pixels.shape[1] < 64:
        return 0.0
    rgb = pixels.astype(np.float32)
    gray = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    mid = gaussian_filter(gray, 4) - gaussian_filter(gray, 20)
    gy, gx = np.gradient(gray)
    edge = gaussian_filter(np.hypot(gx, gy), 1.5)
    smooth = edge < 8.0
    if float(smooth.mean()) < 0.5:
        return 0.0
    local = gaussian_filter(mid * mid, 12) - gaussian_filter(mid, 12) ** 2
    value = float(np.sqrt(np.median(np.clip(local, 0.0, None)[smooth])))
    if not np.isfinite(value):
        return 0.0
    return float(np.clip((value - 22.0) / 10.0, 0.0, 0.75))

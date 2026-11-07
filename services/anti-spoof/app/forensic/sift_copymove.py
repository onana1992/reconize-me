"""Copy-move SIFT + FLANN + RANSAC. Le fond périodique ne compte pas."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

_MIN_SHIFT = 40.0
_PORTRAIT_WEIGHT = 1.4


@dataclass(frozen=True)
class CopyHit:
    score: float
    box: tuple[float, float, float, float] | None
    name: str


def detect(
    pixels: np.ndarray,
    portrait: tuple[float, float, float, float] | None = None,
) -> CopyHit:
    try:
        import cv2
    except ImportError:
        return CopyHit(0.0, None, "document")
    gray = _luma(pixels).astype(np.uint8)
    height, width = gray.shape
    if float(gray.std()) < 6.0:
        return CopyHit(0.0, None, "document")
    work = gray
    long_side = max(height, width)
    if long_side > 640:
        scale = 640 / long_side
        work = cv2.resize(
            gray,
            (max(1, int(width * scale)), max(1, int(height * scale))),
            interpolation=cv2.INTER_AREA,
        )
    height, width = work.shape
    sift = cv2.SIFT_create(nfeatures=400)
    try:
        keypoints, descriptors = sift.detectAndCompute(work, None)
    except cv2.error:
        return CopyHit(0.0, None, "document")
    if descriptors is None or len(keypoints) < 16:
        return CopyHit(0.0, None, "document")
    matcher = cv2.BFMatcher(cv2.NORM_L2)
    try:
        pairs = matcher.knnMatch(descriptors, descriptors, k=3)
    except cv2.error:
        return CopyHit(0.0, None, "document")
    src: list[tuple[float, float]] = []
    dst: list[tuple[float, float]] = []
    for match in pairs:
        if len(match) < 3:
            continue
        _same, first, second = match[0], match[1], match[2]
        if first.distance >= 0.75 * second.distance:
            continue
        if first.trainIdx == first.queryIdx:
            continue
        p1 = keypoints[first.queryIdx].pt
        p2 = keypoints[first.trainIdx].pt
        if np.hypot(p1[0] - p2[0], p1[1] - p2[1]) < _MIN_SHIFT:
            continue
        src.append((float(p1[0]), float(p1[1])))
        dst.append((float(p2[0]), float(p2[1])))
    if len(src) < 8:
        return CopyHit(0.0, None, "document")
    vectors = np.array(dst) - np.array(src)
    clustered = _cluster(vectors, np.array(src), np.array(dst))
    if clustered is None:
        return CopyHit(0.0, None, "document")
    left, right = clustered
    if _periodic(left, right, (width, height)):
        return CopyHit(0.0, None, "document")
    matrix, inliers = _homography(left, right)
    if matrix is None or inliers is None or int(inliers.sum()) < 8:
        return CopyHit(0.0, None, "document")
    kept = left[inliers.ravel() == 1]
    moved = right[inliers.ravel() == 1]
    if _self_similar(kept, moved) or _nearby_tile(kept, moved):
        return CopyHit(0.0, None, "document")
    xs, ys = kept[:, 0] / width, kept[:, 1] / height
    span_x = float(xs.max() - xs.min())
    span_y = float(ys.max() - ys.min())
    shift = np.median(moved - kept, axis=0)
    if span_x > 2.5 * max(span_y, 1e-6) and abs(float(shift[0])) < 30:
        return CopyHit(0.0, None, "document")
    if span_x > 0.5 and span_y < 0.18:
        return CopyHit(0.0, None, "document")
    score = float(np.clip((len(kept) - 8) / 24.0, 0.0, 1.0))
    box = _clip(float(xs.min()), float(ys.min()), span_x + 0.04, span_y + 0.04)
    name = "document"
    if portrait is not None and box is not None and _inside(_center(box), portrait):
        score = float(np.clip(score * _PORTRAIT_WEIGHT, 0.0, 1.0))
        name = "portrait"
    return CopyHit(score, box, name)


def _cluster(
    vectors: np.ndarray,
    src: np.ndarray,
    dst: np.ndarray,
) -> tuple[np.ndarray, np.ndarray] | None:
    rounded = np.round(vectors / 8.0) * 8.0
    buckets: dict[tuple[int, int], list[int]] = {}
    for index, vector in enumerate(rounded):
        key = (int(vector[0]), int(vector[1]))
        if key[0] < 0 or (key[0] == 0 and key[1] < 0):
            key = (-key[0], -key[1])
        buckets.setdefault(key, []).append(index)
    if not buckets:
        return None
    best = max(buckets.values(), key=len)
    if len(best) < 8:
        return None
    return src[best], dst[best]


def _homography(src: np.ndarray, dst: np.ndarray) -> tuple[np.ndarray | None, np.ndarray | None]:
    try:
        import cv2
    except ImportError:
        return None, None
    if len(src) < 8:
        return None, None
    matrix, mask = cv2.findHomography(src, dst, cv2.RANSAC, 5.0)
    return matrix, mask


def _periodic(src: np.ndarray, dst: np.ndarray, size: tuple[int, int]) -> bool:
    """Un guillochis aligne des matches sur une grille, locale ou globale."""
    points = np.vstack([src, dst])
    width, height = size
    span_x = (points[:, 0].max() - points[:, 0].min()) / max(width, 1)
    span_y = (points[:, 1].max() - points[:, 1].min()) / max(height, 1)
    if _regular_lattice(points[:, 0]) and _regular_lattice(points[:, 1]):
        return True
    if span_x < 0.55 or span_y < 0.55:
        return False
    return _regular_lattice(points[:, 0]) or _regular_lattice(points[:, 1])


def _nearby_tile(src: np.ndarray, dst: np.ndarray) -> bool:
    """Translation plus petite que la zone : le motif se répète sur place."""
    shift = np.median(dst - src, axis=0)
    width = float(src[:, 0].max() - src[:, 0].min()) + 8.0
    height = float(src[:, 1].max() - src[:, 1].min()) + 8.0
    return abs(float(shift[0])) <= width * 1.05 and abs(float(shift[1])) <= height * 1.05


def _self_similar(src: np.ndarray, dst: np.ndarray) -> bool:
    """La copie retombe dans la source : motif périodique, pas un collage."""
    left, right = float(src[:, 0].min()), float(src[:, 0].max())
    top, bottom = float(src[:, 1].min()), float(src[:, 1].max())
    pad_x = 0.1 * (right - left + 1)
    pad_y = 0.1 * (bottom - top + 1)
    inside = (
        (dst[:, 0] >= left - pad_x)
        & (dst[:, 0] <= right + pad_x)
        & (dst[:, 1] >= top - pad_y)
        & (dst[:, 1] <= bottom + pad_y)
    )
    return float(inside.mean()) > 0.5


def _regular_lattice(axis: np.ndarray) -> bool:
    values = np.sort(axis)
    gaps = np.diff(values)
    gaps = gaps[gaps > 2]
    if gaps.size < 6:
        return False
    step = float(np.median(gaps))
    if step < 4:
        return False
    return float(np.mean(np.abs(gaps - step) < step * 0.35)) > 0.55


def _luma(pixels: np.ndarray) -> np.ndarray:
    rgb = pixels.astype(np.float32)
    return 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]


def _center(box: tuple[float, float, float, float]) -> tuple[float, float]:
    return (box[0] + box[2] / 2, box[1] + box[3] / 2)


def _inside(point: tuple[float, float], box: tuple[float, float, float, float]) -> bool:
    x, y = point
    left, top, width, height = box
    return left <= x < left + width and top <= y < top + height


def _clip(x: float, y: float, width: float, height: float) -> tuple[float, float, float, float] | None:
    x = min(max(float(x), 0.0), 1.0)
    y = min(max(float(y), 0.0), 1.0)
    width = min(max(float(width), 0.0), 1.0 - x)
    height = min(max(float(height), 0.0), 1.0 - y)
    if width <= 0 or height <= 0:
        return None
    return (x, y, width, height)

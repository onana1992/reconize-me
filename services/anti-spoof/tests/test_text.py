"""Altération de texte : bande de glyphes ré-encodée à part, hint, écart global."""

import base64
import io

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from tests.test_contract import AUTH, body
from tests.test_splicing import _alert, _iou, _uniform

client = TestClient(app)


def _save(pixels: np.ndarray, quality: int) -> bytes:
    buffer = io.BytesIO()
    Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8), "RGB").save(
        buffer,
        format="JPEG",
        quality=quality,
        subsampling=1,
    )
    return buffer.getvalue()


def _decode(raw: bytes) -> np.ndarray:
    with Image.open(io.BytesIO(raw)) as image:
        return np.array(image.convert("RGB"), dtype=np.uint8, copy=True)


def _glyphs(rng: np.random.Generator, image: np.ndarray, rows: list[int], ink: int) -> None:
    for row_y in rows:
        x = 36
        while x < image.shape[1] - 36:
            width = int(rng.integers(7, 16))
            height = int(rng.integers(16, 24))
            image[row_y : row_y + height, x : x + width] = ink
            x += width + int(rng.integers(5, 12))


def _retouched() -> tuple[bytes, tuple[float, float, float, float]]:
    rng = np.random.default_rng(4)
    canvas = np.full((480, 640, 3), 232, np.uint8)
    _glyphs(rng, canvas, [70, 150, 230, 310, 390], 25)
    soft = _decode(_save(canvas, 40))
    top, bottom, left, right = 208, 272, 32, 608
    fresh = np.full_like(canvas, 232)
    _glyphs(np.random.default_rng(9), fresh, [222], 18)
    band = _decode(_save(fresh[top:bottom, left:right], 75))
    soft[top : top + band.shape[0], left : left + band.shape[1]] = band
    box = (left / 640, top / 480, (right - left) / 640, (bottom - top) / 480)
    return _save(soft, 93), box


def _global() -> bytes:
    rng = np.random.default_rng(4)
    canvas = np.full((480, 640, 3), 232, np.uint8)
    _glyphs(rng, canvas, [70, 150, 230, 310, 390], 25)
    return _save(_decode(_save(canvas, 90)), 60)


def _post(raw: bytes, hints: dict | None = None):
    encoded = base64.b64encode(raw).decode("ascii")
    payload = body(image_base64=encoded) if hints is None else body(image_base64=encoded, hints=hints)
    return client.post("/v1/analyze", json=payload, headers=AUTH)


def test_retouched_glyphs_raise_text_and_compression() -> None:
    raw, box = _retouched()
    response = _post(raw)
    assert response.status_code == 200
    payload = response.json()
    assert payload["engine_version"] == "anti-spoof-2"
    text = _alert(payload, "text_alteration")
    compression = _alert(payload, "compression_inconsistency")
    assert text["score"] > 0.55
    assert compression["score"] > 0.55
    assert _alert(payload, "screen_replay")["score"] == 0.0
    assert _alert(payload, "printed_copy")["score"] == 0.0
    assert text["regions"]
    assert max(_iou(region["box"], box) for region in text["regions"]) > 0.3


def test_birth_date_hint_does_not_lower_the_score() -> None:
    raw, box = _retouched()
    plain = _post(raw)
    hinted = _post(raw, {"rois": [{"name": "birth_date", "box": list(box)}]})
    assert hinted.status_code == 200
    assert _alert(hinted.json(), "text_alteration")["score"] >= _alert(plain.json(), "text_alteration")["score"]


def test_hint_outside_the_frame_is_rejected() -> None:
    raw, _box = _retouched()
    response = _post(raw, {"rois": [{"name": "birth_date", "box": [0.0, 0.85, 0.2, 0.2]}]})
    assert response.status_code == 400
    assert response.json() == {"error": "invalid_request"}


def test_global_recompress_stays_at_zero() -> None:
    response = _post(_global())
    assert response.status_code == 200
    payload = response.json()
    assert _alert(payload, "text_alteration")["score"] == 0.0
    assert _alert(payload, "compression_inconsistency")["score"] == 0.0


def test_mixed_blob_sizes_are_not_text() -> None:
    """Portrait + ligne de texte : les boîtes n'ont pas la même taille, ce n'est pas une retouche."""
    rng = np.random.default_rng(6)
    canvas = np.full((480, 640, 3), 228, np.uint8)
    canvas[70:250, 40:200] = (120, 130, 145)
    _glyphs(rng, canvas, [300, 360, 420], 30)
    response = _post(_save(canvas, 82))
    assert response.status_code == 200
    assert _alert(response.json(), "text_alteration")["score"] < 0.55


def test_uniform_card_is_not_text() -> None:
    response = _post(_uniform())
    assert response.status_code == 200
    payload = response.json()
    assert _alert(payload, "text_alteration")["score"] == 0.0
    assert _alert(payload, "compression_inconsistency")["score"] == 0.0


def test_hint_does_not_create_a_detection() -> None:
    response = _post(_uniform(), {"rois": [{"name": "surname", "box": [0.1, 0.1, 0.4, 0.2]}]})
    assert response.status_code == 200
    assert _alert(response.json(), "text_alteration")["score"] == 0.0


def test_text_exception_returns_no_partial_score(monkeypatch: pytest.MonkeyPatch) -> None:
    def boom(_measured: object, _hints: object = None) -> None:
        raise RuntimeError("text-secret")

    monkeypatch.setattr("app.pipeline.detect_text", boom)
    raw, _box = _retouched()
    response = _post(raw)
    assert response.status_code == 500
    assert response.json() == {"error": "internal"}
    assert "text-secret" not in response.text

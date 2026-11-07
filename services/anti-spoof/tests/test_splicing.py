"""Splicing : texture collée, carte uniforme, copie, fond périodique, panne."""

import base64
import io

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from tests.test_contract import AUTH, SMALL_JPEG, body

client = TestClient(app)


def _save(pixels: np.ndarray, quality: int = 92) -> bytes:
    buffer = io.BytesIO()
    Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8), "RGB").save(
        buffer,
        format="JPEG",
        quality=quality,
    )
    return buffer.getvalue()


def _pasted() -> tuple[bytes, tuple[float, float, float, float]]:
    rng = np.random.default_rng(0)
    canvas = np.full((480, 640, 3), 176.0, dtype=np.float32)
    canvas += rng.normal(0, 1.5, canvas.shape)
    paste = rng.normal(0, 42, (160, 200, 3)) + 70
    top, left = 150, 220
    canvas[top : top + 160, left : left + 200] = paste
    box = (left / 640, top / 480, 200 / 640, 160 / 480)
    return _save(canvas), box


def _uniform() -> bytes:
    return _save(np.full((480, 640, 3), 140.0, dtype=np.float32), quality=80)


def _alert(payload: dict, code: str) -> dict:
    return next(alert for alert in payload["alerts"] if alert["code"] == code)


def _iou(left: list[float], right: tuple[float, float, float, float]) -> float:
    ax, ay, aw, ah = left
    bx, by, bw, bh = right
    x1, y1 = max(ax, bx), max(ay, by)
    x2, y2 = min(ax + aw, bx + bw), min(ay + ah, by + bh)
    inter = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    union = aw * ah + bw * bh - inter
    return inter / union if union else 0.0


def test_pasted_texture_raises_splicing_only() -> None:
    raw, box = _pasted()
    encoded = base64.b64encode(raw).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    payload = response.json()
    assert payload["engine_version"] == "anti-spoof-2"
    splicing = _alert(payload, "splicing")
    screen = _alert(payload, "screen_replay")
    printed = _alert(payload, "printed_copy")
    assert splicing["score"] > screen["score"]
    assert splicing["score"] > printed["score"]
    assert screen["score"] == 0.0
    assert printed["score"] == 0.0
    assert _alert(payload, "text_alteration")["score"] == 0.0
    assert _alert(payload, "compression_inconsistency")["score"] == 0.0
    assert splicing["score"] > 0.55
    assert splicing["regions"]
    assert max(_iou(region["box"], box) for region in splicing["regions"]) > 0.3


def _photographed_card() -> bytes:
    """Carte imprimée posée sur une feuille : contours et aplats, pas un collage."""
    rng = np.random.default_rng(7)
    sheet = 214 + rng.normal(0, 1.4, (960, 1280))
    card = np.full((520, 820), 232.0, dtype=np.float32)
    card += rng.normal(0, 1.2, card.shape)
    rows, cols = np.mgrid[0:240, 0:180]
    card[40:280, 36:216] = 90 + 50 * np.sin(cols / 6.0) + 35 * np.sin(rows / 9.0)
    for top in (310, 350, 390, 430):
        card[top : top + 14, 36:560] = 48
    canvas = np.stack([sheet, sheet * 0.99, sheet * 0.97], axis=2)
    top, left = 180, 220
    canvas[top : top + card.shape[0], left : left + card.shape[1]] = card[..., None]
    return _save(canvas, quality=82)


def test_recompressed_card_on_table_is_not_spliced() -> None:
    """JPEG serré, deux textures : Splicebuster ne doit pas y voir un collage."""
    rng = np.random.default_rng(8)
    sheet = 220 + rng.normal(0, 1.0, (960, 1280))
    card = np.full((700, 1100), 200.0, dtype=np.float32)
    card += rng.normal(0, 0.8, card.shape)
    card[80:360, 60:320] = 110
    for top in (420, 470, 520, 570):
        card[top : top + 16, 60:820] = 48
    canvas = np.stack([sheet, sheet * 0.99, sheet * 0.97], axis=2)
    canvas[80:780, 90:1190] = card[..., None]
    first = _save(canvas, quality=55)
    with Image.open(io.BytesIO(first)) as image:
        pixels = np.array(image.convert("RGB"), dtype=np.float32)
    encoded = base64.b64encode(_save(pixels, quality=40)).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    payload = response.json()
    assert _alert(payload, "splicing")["score"] < 0.55
    assert _alert(payload, "printed_copy")["score"] < 0.55


def test_photographed_card_is_not_spliced() -> None:
    encoded = base64.b64encode(_photographed_card()).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    assert _alert(response.json(), "splicing")["score"] < 0.55


def test_uniform_card_stays_under_review() -> None:
    encoded = base64.b64encode(_uniform()).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    splicing = _alert(response.json(), "splicing")
    assert splicing["score"] < 0.55
    assert splicing["regions"] == []


def test_portrait_hint_does_not_lower_the_score() -> None:
    raw, box = _pasted()
    encoded = base64.b64encode(raw).decode("ascii")
    plain = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    hinted = client.post(
        "/v1/analyze",
        json=body(
            image_base64=encoded,
            hints={"rois": [{"name": "portrait", "box": list(box)}]},
        ),
        headers=AUTH,
    )
    assert hinted.status_code == 200
    assert _alert(hinted.json(), "splicing")["score"] >= _alert(plain.json(), "splicing")["score"]


def test_detector_exception_returns_no_partial_score(monkeypatch: pytest.MonkeyPatch) -> None:
    def boom(_pixels: np.ndarray, _portrait: object = None) -> None:
        raise RuntimeError("splicing-secret")

    monkeypatch.setattr("app.pipeline.detect", boom)
    raw, _box = _pasted()
    encoded = base64.b64encode(raw).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 500
    assert response.json() == {"error": "internal"}
    assert "splicing-secret" not in response.text
    assert "splicing" not in response.text


def test_unusable_image_does_not_call_the_detector(monkeypatch: pytest.MonkeyPatch) -> None:
    def boom(_pixels: np.ndarray, _portrait: object = None) -> None:
        raise RuntimeError("should-not-run")

    monkeypatch.setattr("app.pipeline.detect", boom)
    encoded = base64.b64encode(SMALL_JPEG).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    assert response.json()["forensic_usable"] is False


def test_periodic_texture_is_not_a_copy() -> None:
    rng = np.random.default_rng(1)
    tile = rng.normal(120, 18, (32, 32, 3))
    canvas = np.tile(tile, (15, 20, 1))
    encoded = base64.b64encode(_save(canvas)).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    assert _alert(response.json(), "splicing")["score"] < 0.55


def test_unaligned_internal_copy_scores() -> None:
    canvas = np.full((480, 640, 3), 150.0, dtype=np.float32)
    yy, xx = np.mgrid[0:160, 0:160]
    patch = np.stack(
        [
            40 + 140 * (xx / 160),
            30 + 80 * ((xx - 80) ** 2 + (yy - 60) ** 2) / 12800,
            200 - 120 * (yy / 160),
        ],
        axis=2,
    )
    canvas[32:192, 32:192] = patch
    canvas[247:407, 371:531] = patch
    encoded = base64.b64encode(_save(canvas, quality=95)).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    assert _alert(response.json(), "splicing")["score"] > 0.55


def test_internal_copy_of_a_patch_scores() -> None:
    canvas = np.full((480, 640, 3), 150.0, dtype=np.float32)
    yy, xx = np.mgrid[0:160, 0:160]
    patch = np.stack(
        [
            40 + 140 * (xx / 160),
            30 + 80 * ((xx - 80) ** 2 + (yy - 60) ** 2) / 12800,
            200 - 120 * (yy / 160),
        ],
        axis=2,
    )
    canvas[32:192, 32:192] = patch
    canvas[256:416, 384:544] = patch
    encoded = base64.b64encode(_save(canvas, quality=95)).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    splicing = _alert(response.json(), "splicing")
    assert splicing["score"] > 0.55
    assert _alert(response.json(), "screen_replay")["score"] < 0.55
    assert _alert(response.json(), "printed_copy")["score"] == 0.0

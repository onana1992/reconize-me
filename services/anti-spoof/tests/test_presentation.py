"""Écran, papier, version mesurée, délai."""

import base64
import io
import time

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image
from scipy.ndimage import gaussian_filter

from app.main import app
from app.pipeline import DEADLINE_S
from tests.test_contract import AUTH, JPEG, body
from tests.test_splicing import _alert

client = TestClient(app)


def _save(pixels: np.ndarray, quality: int = 92) -> bytes:
    buffer = io.BytesIO()
    Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8), "RGB").save(buffer, format="JPEG", quality=quality)
    return buffer.getvalue()


def _rgb_grid() -> bytes:
    height, width = 480, 640
    phase = 2 * np.pi * np.arange(width) / 8
    image = np.zeros((height, width, 3), np.float32)
    image[:, :, 0] = 128 + 90 * np.sin(phase)
    image[:, :, 1] = 128 + 90 * np.sin(phase + 2 * np.pi / 3)
    image[:, :, 2] = 128 + 90 * np.sin(phase + 4 * np.pi / 3)
    return _save(image)


def _dots() -> bytes:
    image = np.full((480, 640, 3), 230, np.float32)
    for y in range(0, 480, 12):
        for x in range(0, 640, 12):
            image[y : y + 3, x : x + 3] = 25
    return _save(image)


def _fiber() -> bytes:
    rng = np.random.default_rng(2)
    field = gaussian_filter(rng.normal(0, 1, (480, 640)), 8)
    field = 150 + 80 * field / (field.std() + 1e-6)
    return _save(np.stack([field, field, field], axis=2))


def _monitor_moire() -> bytes:
    """Bandes grises de période 48 px, cadre sombre, carte plus claire au centre."""
    height, width = 480, 640
    image = np.full((height, width, 3), 18, np.float32)
    top, bottom, left, right = 40, 440, 50, 590
    rows = np.arange(top, bottom)
    wave = 140 + 40 * np.sin(2 * np.pi * rows / 48)
    image[top:bottom, left:right, :] = wave[:, None, None]
    image[180:340, 160:480, :] = np.clip(image[180:340, 160:480, :] + 25, 0, 255)
    return _save(image)


def _shifting_grating() -> bytes:
    """Grille grise dont la période glisse. Le spectre global ne forme pas un pic."""
    height, width = 480, 640
    rows, cols = np.mgrid[0:height, 0:width]
    period = 6 + 3 * (rows / height) + 1.2 * np.sin(2 * np.pi * cols / 180)
    phase = 2 * np.pi * cols / period + 1.4 * np.sin(2 * np.pi * rows / 70)
    wave = 128 + 28 * np.sin(phase)
    return _save(np.stack([wave, wave, wave], axis=-1))


def _partial_grid() -> bytes:
    image = np.full((480, 640, 3), 160, np.float32)
    phase = 2 * np.pi * np.arange(320) / 8
    image[:, 320:, 0] = 128 + 90 * np.sin(phase)
    image[:, 320:, 1] = 128 + 90 * np.sin(phase + 2 * np.pi / 3)
    image[:, 320:, 2] = 128 + 90 * np.sin(phase + 4 * np.pi / 3)
    return _save(image)


def _post(raw: bytes, hints: dict | None = None):
    encoded = base64.b64encode(raw).decode("ascii")
    payload = body(image_base64=encoded) if hints is None else body(image_base64=encoded, hints=hints)
    return client.post("/v1/analyze", json=payload, headers=AUTH)


def test_deadline_is_thirty_seconds() -> None:
    assert DEADLINE_S == 30.0


def test_fine_rgb_grid_raises_screen_only() -> None:
    response = _post(_rgb_grid())
    assert response.status_code == 200
    payload = response.json()
    assert payload["engine_version"] == "anti-spoof-1"
    screen = _alert(payload, "screen_replay")
    printed = _alert(payload, "printed_copy")
    assert screen["score"] > 0.55
    assert screen["score"] > printed["score"]
    assert printed["score"] < 0.55


def test_gray_monitor_moire_raises_screen_only() -> None:
    response = _post(_monitor_moire())
    assert response.status_code == 200
    payload = response.json()
    screen = _alert(payload, "screen_replay")
    printed = _alert(payload, "printed_copy")
    assert screen["score"] > 0.55
    assert screen["score"] > printed["score"]
    assert printed["score"] < 0.55


def test_shifting_gray_grating_raises_screen_only() -> None:
    response = _post(_shifting_grating())
    assert response.status_code == 200
    payload = response.json()
    screen = _alert(payload, "screen_replay")
    printed = _alert(payload, "printed_copy")
    assert screen["score"] > 0.55
    assert screen["score"] > printed["score"]
    assert printed["score"] < 0.55


def _photographed(card: np.ndarray) -> bytes:
    """Carte posée sur une feuille, puis photographiée : flou léger et JPEG."""
    rng = np.random.default_rng(3)
    sheet = gaussian_filter(rng.normal(0, 1, (960, 1280)), 3.0)
    sheet = 232 + 4 * sheet / (sheet.std() + 1e-6)
    canvas = np.stack([sheet, sheet * 0.99, sheet * 0.97], axis=2)
    top, left = 220, 280
    canvas[top : top + card.shape[0], left : left + card.shape[1]] = card
    blurred = gaussian_filter(canvas, 0.6)
    return _save(blurred, quality=82)


def _printed_card(step: int) -> np.ndarray:
    card = np.full((420, 680, 3), 214, np.float32)
    card[40:250, 36:210] = (168, 176, 188)
    for top in (280, 320, 360):
        card[top : top + 18, 36:420] = 70
    for y in range(0, card.shape[0], step):
        shift = step // 2 if (y // step) % 2 == 0 else 0
        for x in range(shift, card.shape[1], step):
            card[y : y + 2, x : x + 2] = np.minimum(card[y : y + 2, x : x + 2], 30)
    return card


def test_photographed_print_raises_printed_only() -> None:
    response = _post(_photographed(_printed_card(10)))
    assert response.status_code == 200
    payload = response.json()
    screen = _alert(payload, "screen_replay")["score"]
    printed = _alert(payload, "printed_copy")["score"]
    assert printed > 0.55
    assert printed > screen
    assert screen < 0.55


def _shown_on_screen(period: int, colored: bool) -> bytes:
    """Pièce affichée sur une dalle, puis photographiée. La cellule est pleine."""
    card = np.full((720, 1100, 3), 230, np.float32)
    card[80:400, 60:360] = (150, 160, 175)
    for top in (440, 500, 560, 620):
        card[top : top + 22, 60:780] = 55
    rows, cols = np.mgrid[0:card.shape[0], 0:card.shape[1]]
    if colored:
        phase = 2 * np.pi * cols / period
        card[:, :, 0] += 28 * np.sin(phase)
        card[:, :, 1] += 28 * np.sin(phase + 2 * np.pi / 3)
        card[:, :, 2] += 28 * np.sin(phase + 4 * np.pi / 3)
        row = 0.72 + 0.28 * (np.sin(2 * np.pi * rows / period) > 0)
        card *= row[..., None]
    else:
        cell = ((cols % period) < period // 2) & ((rows % period) < period // 2)
        card = np.where(cell[..., None], card * 0.78, card)
    rng = np.random.default_rng(5)
    card = card + rng.normal(0, 1.5, card.shape)
    return _save(gaussian_filter(card, 0.4), quality=80)


def test_gray_pixel_cell_raises_screen_only() -> None:
    response = _post(_shown_on_screen(7, colored=False))
    assert response.status_code == 200
    payload = response.json()
    screen = _alert(payload, "screen_replay")["score"]
    printed = _alert(payload, "printed_copy")["score"]
    assert screen > 0.55
    assert screen > printed
    assert printed < 0.55


def test_colored_pixel_cell_raises_screen_only() -> None:
    response = _post(_shown_on_screen(8, colored=True))
    assert response.status_code == 200
    payload = response.json()
    screen = _alert(payload, "screen_replay")["score"]
    printed = _alert(payload, "printed_copy")["score"]
    assert screen > 0.55
    assert screen > printed
    assert printed < 0.55


def test_plain_card_on_paper_is_not_printed() -> None:
    card = np.full((420, 680, 3), 214, np.float32)
    card[40:250, 36:210] = (168, 176, 188)
    for top in (280, 320, 360):
        card[top : top + 18, 36:420] = 70
    response = _post(_photographed(card))
    assert response.status_code == 200
    assert _alert(response.json(), "printed_copy")["score"] < 0.55


def test_dot_screen_raises_printed_only() -> None:
    response = _post(_dots())
    assert response.status_code == 200
    payload = response.json()
    screen = _alert(payload, "screen_replay")
    printed = _alert(payload, "printed_copy")
    assert printed["score"] > 0.55
    assert printed["score"] > screen["score"]
    assert screen["score"] < 0.55


def test_grain_alone_stays_under_the_decline_line() -> None:
    response = _post(_fiber())
    assert response.status_code == 200
    payload = response.json()
    printed = _alert(payload, "printed_copy")["score"]
    assert 0.0 < printed < 0.80
    assert _alert(payload, "screen_replay")["score"] < 0.55


def _faint_recapture() -> bytes:
    """Pièce + grille RVB atténuée : recapture optique, sans bandeau d'UI."""
    rng = np.random.default_rng(4)
    height, width = 480, 640
    image = np.full((height, width, 3), 186, np.float32)
    image[70:220, 40:190] = (168, 174, 186)
    image += rng.normal(0, 4, image.shape)
    cols = np.arange(width)
    grid = np.zeros((height, width, 3), np.float32)
    grid[:, :, 0] = 128 + 90 * np.sin(2 * np.pi * cols / 8)
    grid[:, :, 1] = 128 + 90 * np.sin(2 * np.pi * cols / 8 + 2 * np.pi / 3)
    grid[:, :, 2] = 128 + 90 * np.sin(2 * np.pi * cols / 8 + 4 * np.pi / 3)
    blended = 0.72 * image + 0.28 * grid
    return _save(np.clip(blended, 0, 255))


def test_faint_optical_recapture_raises_screen() -> None:
    response = _post(_faint_recapture())
    assert response.status_code == 200
    payload = response.json()
    screen = _alert(payload, "screen_replay")["score"]
    printed = _alert(payload, "printed_copy")["score"]
    assert screen > 0.55
    assert printed < 0.55


def test_tiled_wallpaper_is_not_screen_replay() -> None:
    rng = np.random.default_rng(1)
    tile = rng.normal(120, 18, (32, 32, 3))
    canvas = np.tile(tile, (15, 20, 1))[:480, :640]
    response = _post(_save(canvas))
    assert response.status_code == 200
    assert _alert(response.json(), "screen_replay")["score"] < 0.55


def test_uniform_card_stays_under_review_on_the_measured_engine() -> None:
    encoded = base64.b64encode(JPEG).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    payload = response.json()
    assert payload["engine_version"] == "anti-spoof-1"
    assert payload["fusion_version"] == "identity"
    assert all(alert["score"] < 0.55 for alert in payload["alerts"])


def test_document_hint_limits_the_screen_spectrum() -> None:
    raw = _partial_grid()
    on_grid = _post(raw, {"rois": [{"name": "document", "box": [0.5, 0.0, 0.5, 1.0]}]})
    on_flat = _post(raw, {"rois": [{"name": "document", "box": [0.0, 0.0, 0.45, 1.0]}]})
    assert on_grid.status_code == 200
    assert on_flat.status_code == 200
    grid_score = _alert(on_grid.json(), "screen_replay")["score"]
    flat_score = _alert(on_flat.json(), "screen_replay")["score"]
    assert grid_score > 0.55
    assert flat_score < 0.55
    assert grid_score > flat_score


def test_overrun_returns_no_zero_score(monkeypatch: pytest.MonkeyPatch) -> None:
    def slow(_pixels: np.ndarray, _portrait: object = None) -> None:
        time.sleep(0.6)

    monkeypatch.setattr("app.pipeline.DEADLINE_S", 0.15)
    monkeypatch.setattr("app.pipeline.detect", slow)
    encoded = base64.b64encode(JPEG).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 500
    assert response.json() == {"error": "internal"}
    assert "0.000" not in response.text

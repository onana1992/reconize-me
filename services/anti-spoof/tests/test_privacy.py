"""Aucune chaîne OCR dans la réponse, les journaux ou les artefacts."""

import base64
import io
import json

import numpy as np
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw, ImageFont

from app.artifacts import build
from app.forensic.ocr_boxes import Glyph
from app.main import app
from tests.test_contract import AUTH, body

client = TestClient(app)

SECRET = "DUPONT"


def _card_with_name() -> bytes:
    image = Image.new("RGB", (640, 480), (232, 232, 232))
    draw = ImageDraw.Draw(image)
    try:
        font = ImageFont.truetype("arial.ttf", 36)
    except OSError:
        font = ImageFont.load_default()
    draw.text((80, 200), SECRET, fill=(20, 20, 20), font=font)
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", quality=90)
    return buffer.getvalue()


def test_glyph_has_no_text_field() -> None:
    assert "text" not in Glyph.__dataclass_fields__


def test_response_and_logs_do_not_carry_ocr_strings(capsys) -> None:
    raw = _card_with_name()
    encoded = base64.b64encode(raw).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    payload = response.json()
    dumped = json.dumps(payload)
    assert SECRET not in dumped
    assert SECRET.lower() not in dumped.lower()
    captured = capsys.readouterr()
    assert SECRET not in captured.out
    assert SECRET not in captured.err
    record = json.loads(captured.out.strip())
    assert isinstance(record.get("ocr_boxes"), int)


def test_artifacts_glyphs_have_no_readable_string() -> None:
    raw = _card_with_name()
    pixels = np.array(Image.open(io.BytesIO(raw)).convert("RGB"), dtype=np.uint8)
    artifacts = build(raw, pixels)
    for glyph in artifacts.glyphs:
        assert not hasattr(glyph, "text")
        dumped = json.dumps(glyph.__dict__)
        assert SECRET not in dumped

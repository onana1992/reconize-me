"""Contrat HTTP : forme, jeton, doc, prétraitement."""

import base64
import io
import json
import os

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import PORT, app, create_app, load_env_file
from app.preprocess import MAX_BODY_BYTES
from app.serialize import dumps


def _jpeg(width: int, height: int) -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (width, height), (20, 40, 60)).save(buffer, format="JPEG", quality=80)
    return buffer.getvalue()


JPEG = _jpeg(480, 480)
JPEG_B64 = base64.b64encode(JPEG).decode("ascii")
SMALL_JPEG = _jpeg(200, 200)
AUTH = {"Authorization": "Bearer test-token"}
REQUEST_ID = "6f1c0e2a-1b4d-4e0a-9c3a-8a1d2f4b6c70"
VERIFICATION_ID = "3b9c1a44-7e2d-4f1a-9a55-0c1e8d2a4b11"

client = TestClient(app)


def body(**extra: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "request_id": REQUEST_ID,
        "verification_id": VERIFICATION_ID,
        "side": "FRONT",
        "media_type": "image/jpeg",
        "image_base64": JPEG_B64,
    }
    payload.update(extra)
    return payload


def test_scores_serialize_with_three_decimals() -> None:
    assert dumps({"score": 0, "authenticity_score": 1, "forensic_usable": True}) == (
        '{"score":0.000,"authenticity_score":1.000,"forensic_usable":true}'
    )
    assert dumps({"score": None}) == '{"score":null}'


def test_health_does_not_need_a_token() -> None:
    response = client.get("/health", headers={"Authorization": "Bearer wrong"})
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "engine_version": "anti-spoof-2"}


def test_analyze_without_token_is_empty_401() -> None:
    response = client.post("/v1/analyze", json=body())
    assert response.status_code == 401
    assert response.content == b""


def test_analyze_with_wrong_token_is_empty_401() -> None:
    response = client.post(
        "/v1/analyze",
        json=body(),
        headers={"Authorization": "Bearer wrong"},
    )
    assert response.status_code == 401
    assert response.content == b""


def test_unknown_field_is_invalid_request() -> None:
    response = client.post("/v1/analyze", json=body(comment="note"), headers=AUTH)
    assert response.status_code == 400
    assert response.json() == {"error": "invalid_request"}
    assert "note" not in response.text


def test_bad_hint_or_box_is_invalid_request() -> None:
    hints = {"rois": [{"name": "address", "box": [0.1, 0.1, 0.2, 0.2]}]}
    named = client.post("/v1/analyze", json=body(hints=hints), headers=AUTH)
    assert named.status_code == 400
    assert named.json() == {"error": "invalid_request"}

    outside = {"rois": [{"name": "portrait", "box": [1.2, 0, 0.1, 0.1]}]}
    boxed = client.post("/v1/analyze", json=body(hints=outside), headers=AUTH)
    assert boxed.status_code == 400

    empty = client.post("/v1/analyze", json=body(image_base64=""), headers=AUTH)
    assert empty.status_code == 400
    assert empty.json() == {"error": "invalid_request"}


def test_valid_jpeg_returns_the_contract_form() -> None:
    hints = {
        "document_code": "FRANCE_CNI",
        "rois": [{"name": "portrait", "box": [0.0, 0.0, 1.0, 1.0]}],
    }
    response = client.post("/v1/analyze", json=body(hints=hints), headers=AUTH)
    assert response.status_code == 200
    payload = response.json()
    assert payload["engine_version"] == "anti-spoof-2"
    assert payload["fusion_version"] == "forensic-1"
    assert payload["forensic_usable"] is True
    assert payload["authenticity_score"] == 1.0
    assert payload["tampering_score"] == 0.0
    assert payload["presentation_score"] == 0.0
    assert [alert["code"] for alert in payload["alerts"]] == [
        "splicing",
        "text_alteration",
        "compression_inconsistency",
        "screen_replay",
        "printed_copy",
    ]
    assert [alert["family"] for alert in payload["alerts"]] == [
        "digital_forgery",
        "digital_forgery",
        "digital_forgery",
        "presentation_spoof",
        "presentation_spoof",
    ]
    for alert in payload["alerts"]:
        assert alert["score"] == 0.0
        assert alert["regions"] == []
    raw = response.text
    assert '"authenticity_score":1.000' in raw
    assert raw.count('"score":0.000') == 5
    for word in ("PASS", "REVIEW", "DECLINED"):
        assert word not in raw


def test_upload_uses_the_same_form() -> None:
    response = client.post(
        "/v1/analyze-upload",
        files={"file": ("carte.jpg", JPEG, "image/jpeg")},
        headers=AUTH,
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["engine_version"] == "anti-spoof-2"
    assert payload["fusion_version"] == "forensic-1"
    assert [alert["score"] for alert in payload["alerts"]] == [0.0, 0.0, 0.0, 0.0, 0.0]


def test_upload_without_token_is_empty_401() -> None:
    response = client.post(
        "/v1/analyze-upload",
        files={"file": ("carte.jpg", JPEG, "image/jpeg")},
    )
    assert response.status_code == 401
    assert response.content == b""


def test_journal_has_scores_and_not_the_image(capsys: pytest.CaptureFixture[str]) -> None:
    response = client.post("/v1/analyze", json=body(), headers=AUTH)
    assert response.status_code == 200
    captured = capsys.readouterr().out
    assert JPEG_B64 not in captured
    assert "test-token" not in captured
    record = json.loads(captured.strip())
    assert record["request_id"] == REQUEST_ID
    assert record["verification_id"] == VERIFICATION_ID
    assert record["side"] == "FRONT"
    assert record["engine_version"] == "anti-spoof-2"
    assert record["forensic_usable"] is True
    assert record["splicing"] == 0.0
    assert record["printed_copy"] == 0.0


def test_upload_journal_omits_the_filename(capsys: pytest.CaptureFixture[str]) -> None:
    response = client.post(
        "/v1/analyze-upload",
        files={"file": ("piece-dupont.jpg", JPEG, "image/jpeg")},
        headers=AUTH,
    )
    assert response.status_code == 200
    captured = capsys.readouterr().out
    assert "dupont" not in captured
    assert "piece-dupont" not in captured
    record = json.loads(captured.strip())
    assert "request_id" not in record
    assert record["screen_replay"] == 0.0


def test_exception_does_not_return_a_partial_score(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    def boom(_image: bytes) -> None:
        raise RuntimeError("jpeg-bytes-secret")

    monkeypatch.setattr("app.main.assess", boom)
    response = client.post("/v1/analyze", json=body(), headers=AUTH)
    assert response.status_code == 500
    assert response.json() == {"error": "internal"}
    assert "jpeg-bytes-secret" not in response.text
    assert "splicing" not in response.text
    captured = capsys.readouterr()
    assert "jpeg-bytes-secret" not in captured.out
    assert "jpeg-bytes-secret" not in captured.err


def test_docs_list_the_upload() -> None:
    page = client.get("/docs")
    assert page.status_code == 200
    assert "swagger" in page.text.lower()
    schema = client.get("/openapi.json")
    assert schema.status_code == 200
    document = schema.json()
    assert "/v1/analyze" in document["paths"]
    assert "/v1/analyze-upload" in document["paths"]
    assert "/health" in document["paths"]
    assert "422" not in document["paths"]["/v1/analyze"]["post"]["responses"]
    security = document["paths"]["/v1/analyze"]["post"]["security"]
    assert security == [{"bearer": []}]
    assert "security" not in document["paths"]["/health"]["get"]


def test_docs_off_hides_docs_and_upload(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ANTI_SPOOF_DOCS", "false")
    hidden = TestClient(create_app())
    assert hidden.get("/docs").status_code == 404
    assert hidden.get("/openapi.json").status_code == 404
    assert hidden.get("/redoc").status_code == 404
    upload = hidden.post(
        "/v1/analyze-upload",
        files={"file": ("carte.jpg", JPEG, "image/jpeg")},
        headers=AUTH,
    )
    assert upload.status_code == 404
    analyzed = hidden.post("/v1/analyze", json=body(), headers=AUTH)
    assert analyzed.status_code == 200
    assert analyzed.json()["engine_version"] == "anti-spoof-2"


def test_missing_token_refuses_to_start(monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]) -> None:
    monkeypatch.delenv("ANTI_SPOOF_TOKEN", raising=False)
    with pytest.raises(SystemExit) as caught:
        create_app()
    assert caught.value.code == 1
    assert "ANTI_SPOOF_TOKEN" in capsys.readouterr().err

    monkeypatch.setenv("ANTI_SPOOF_TOKEN", "   ")
    with pytest.raises(SystemExit) as blank:
        create_app()
    assert blank.value.code == 1


def test_port_is_8090() -> None:
    assert PORT == 8090


def test_random_bytes_are_invalid_image() -> None:
    encoded = base64.b64encode(b"pas-un-jpeg").decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 400
    assert response.json() == {"error": "invalid_image"}
    assert "pas-un-jpeg" not in response.text

    upload = client.post(
        "/v1/analyze-upload",
        files={"file": ("note.txt", b"pas-un-jpeg", "text/plain")},
        headers=AUTH,
    )
    assert upload.status_code == 400
    assert upload.json() == {"error": "invalid_image"}


def test_small_jpeg_is_not_measurable() -> None:
    encoded = base64.b64encode(SMALL_JPEG).decode("ascii")
    response = client.post("/v1/analyze", json=body(image_base64=encoded), headers=AUTH)
    assert response.status_code == 200
    payload = response.json()
    assert payload["forensic_usable"] is False
    assert payload["engine_version"] == "anti-spoof-2"
    assert payload["authenticity_score"] is None
    assert payload["tampering_score"] is None
    assert payload["presentation_score"] is None
    assert [alert["score"] for alert in payload["alerts"]] == [None, None, None, None, None]
    assert [alert["regions"] for alert in payload["alerts"]] == [[], [], [], [], []]
    assert response.text.count('"score":null') == 5


def test_body_over_8_mib_is_payload_too_large() -> None:
    huge = b"x" * (MAX_BODY_BYTES + 1)
    refused = client.post(
        "/v1/analyze",
        content=huge,
        headers={**AUTH, "Content-Type": "application/json"},
    )
    assert refused.status_code == 413
    assert refused.json() == {"error": "payload_too_large"}

    anonymous = client.post(
        "/v1/analyze",
        content=huge,
        headers={"Content-Type": "application/json"},
    )
    assert anonymous.status_code == 401
    assert anonymous.content == b""


def test_env_file_fills_missing_keys_only(tmp_path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ANTI_SPOOF_TOKEN", "already")
    monkeypatch.delenv("ANTI_SPOOF_DOCS", raising=False)
    env_file = tmp_path / ".env"
    env_file.write_text(
        "\n".join(
            [
                "# local",
                "ANTI_SPOOF_TOKEN=from-file",
                'ANTI_SPOOF_DOCS="true"',
                "export IGNORED",
            ]
        ),
        encoding="utf-8",
    )
    load_env_file(env_file)
    assert os.environ["ANTI_SPOOF_TOKEN"] == "already"
    assert os.environ["ANTI_SPOOF_DOCS"] == "true"

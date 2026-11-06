"""Forme JSON du contrat. Un champ inconnu rejette l'appel."""

from __future__ import annotations

import base64
import math
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

AlertCode = Literal[
    "splicing",
    "text_alteration",
    "compression_inconsistency",
    "screen_replay",
    "printed_copy",
]
Family = Literal["digital_forgery", "presentation_spoof"]
RoiName = Literal[
    "document",
    "portrait",
    "mrz",
    "birth_date",
    "document_number",
    "surname",
    "given_names",
]

# Ordre fixe de la réponse. La famille suit le code.
ALERTS: tuple[tuple[AlertCode, Family], ...] = (
    ("splicing", "digital_forgery"),
    ("text_alteration", "digital_forgery"),
    ("compression_inconsistency", "digital_forgery"),
    ("screen_replay", "presentation_spoof"),
    ("printed_copy", "presentation_spoof"),
)


class Roi(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: RoiName
    box: list[float] = Field(min_length=4, max_length=4)

    @field_validator("box")
    @classmethod
    def box_inside_frame(cls, box: list[float]) -> list[float]:
        x, y, width, height = box
        if not all(math.isfinite(value) for value in box):
            raise ValueError("box")
        if width <= 0 or height <= 0 or x < 0 or y < 0:
            raise ValueError("box")
        if x + width > 1 or y + height > 1:
            raise ValueError("box")
        return box


class Hints(BaseModel):
    model_config = ConfigDict(extra="forbid")

    document_code: str | None = Field(
        default=None,
        description="Code du registre Document IA, ou absent.",
    )
    rois: list[Roi] | None = Field(
        default=None,
        description="Rectangles normalisés. Aucun texte de la pièce.",
    )


class AnalyzeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    request_id: UUID = Field(description="Identifie cet appel.")
    verification_id: UUID = Field(description="Identifie le dossier. Le service ne le relie à rien.")
    side: Literal["FRONT", "BACK"]
    media_type: Literal["image/jpeg"] = Field(description="image/jpeg seulement.")
    image_base64: str = Field(description="JPEG d'origine, en base64. Pas la copie de lecture.")
    hints: Hints | None = None

    @field_validator("image_base64")
    @classmethod
    def image_is_base64(cls, value: str) -> str:
        try:
            raw = base64.b64decode(value, validate=True)
        except Exception as exc:
            raise ValueError("image_base64") from exc
        if not raw:
            raise ValueError("image_base64")
        return value


class Region(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: RoiName
    box: list[float] = Field(min_length=4, max_length=4)
    score: float = Field(description="Score de la boîte, trois décimales.")


class Alert(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: AlertCode
    family: Family
    score: float | None = Field(description="Trois décimales, ou null si l'image n'est pas mesurable.")
    regions: list[Region]


class Assessment(BaseModel):
    model_config = ConfigDict(extra="forbid")

    engine_version: str = Field(description="anti-spoof-1 : les cinq détecteurs mesurent.")
    fusion_version: str = Field(description="identity : score brut, sans coefficient appris.")
    forensic_usable: bool
    authenticity_score: float | None
    tampering_score: float | None
    presentation_score: float | None
    alerts: list[Alert] = Field(min_length=5, max_length=5)

    @model_validator(mode="after")
    def alerts_follow_the_contract(self) -> Assessment:
        got = [(alert.code, alert.family) for alert in self.alerts]
        if got != list(ALERTS):
            raise ValueError("alerts")
        return self


class ErrorBody(BaseModel):
    error: Literal["invalid_request", "invalid_image", "payload_too_large", "internal"]

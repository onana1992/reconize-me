"""JSON du contrat. Les scores s'écrivent avec trois décimales."""

from __future__ import annotations

import json
from typing import Any

from fastapi.responses import JSONResponse

from app.contract import ALERTS

SCORE_KEYS = frozenset(
    {
        "authenticity_score",
        "tampering_score",
        "presentation_score",
        "score",
        *(code for code, _family in ALERTS),
    }
)


def dumps(value: Any) -> str:
    return _dump(value, None)


def _dump(value: Any, key: str | None) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, dict):
        parts = [
            json.dumps(str(name), ensure_ascii=False) + ":" + _dump(item, str(name))
            for name, item in value.items()
        ]
        return "{" + ",".join(parts) + "}"
    if isinstance(value, (list, tuple)):
        return "[" + ",".join(_dump(item, None) for item in value) + "]"
    if isinstance(value, (int, float)) and key in SCORE_KEYS:
        return format(round(float(value), 3), ".3f")
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return json.dumps(value)
    raise TypeError(type(value).__name__)


class ContractJSONResponse(JSONResponse):
    def render(self, content: Any) -> bytes:
        return dumps(content).encode("utf-8")

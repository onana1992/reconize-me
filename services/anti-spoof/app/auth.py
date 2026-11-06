"""Jeton partagé. Absent ou faux : 401, corps vide."""

from __future__ import annotations

import hashlib
import secrets
from collections.abc import Callable, Mapping

from app.preprocess import MAX_BODY_BYTES, PayloadTooLarge


class AuthRejected(Exception):
    """Le jeton ne correspond pas. La réponse HTTP n'a pas de corps."""


def token_matches(presented: str, expected: str) -> bool:
    if presented == "" or expected == "":
        return False
    return secrets.compare_digest(
        hashlib.sha256(presented.encode("utf-8")).digest(),
        hashlib.sha256(expected.encode("utf-8")).digest(),
    )


def bearer_value(header: str | None) -> str | None:
    if header is None:
        return None
    scheme, separator, value = header.partition(" ")
    if separator != " " or scheme.lower() != "bearer" or value == "" or " " in value:
        return None
    return value


class BearerMiddleware:
    """Refuse /v1/analyze avant de lire le corps."""

    def __init__(self, app: object, token: str, paths: frozenset[str]) -> None:
        self.app = app
        self.token = token
        self.paths = paths

    async def __call__(self, scope: Mapping[str, object], receive: object, send: object) -> None:
        if scope.get("type") == "http" and scope.get("path") in self.paths:
            headers = scope.get("headers")
            presented = bearer_value(_authorization(headers)) or ""
            if not token_matches(presented, self.token):
                await _empty(send, 401)
                return
            length = _content_length(headers)
            if length is not None and length > MAX_BODY_BYTES:
                await _json(send, 413, b'{"error":"payload_too_large"}')
                return
            receive = _cap_body(receive, MAX_BODY_BYTES)
        await self.app(scope, receive, send)  # type: ignore[operator]


def _authorization(headers: object) -> str | None:
    if not isinstance(headers, list):
        return None
    for key, value in headers:
        if isinstance(key, bytes) and key.lower() == b"authorization" and isinstance(value, bytes):
            return value.decode("latin-1")
    return None


def _content_length(headers: object) -> int | None:
    if not isinstance(headers, list):
        return None
    for key, value in headers:
        if isinstance(key, bytes) and key.lower() == b"content-length" and isinstance(value, bytes):
            try:
                return int(value.decode("ascii"))
            except ValueError:
                return None
    return None


def _cap_body(receive: object, limit: int) -> Callable[[], object]:
    total = 0

    async def capped() -> object:
        nonlocal total
        message = await receive()  # type: ignore[operator]
        if isinstance(message, dict) and message.get("type") == "http.request":
            chunk = message.get("body") or b""
            total += len(chunk)
            if total > limit:
                raise PayloadTooLarge()
        return message

    return capped


async def _json(send: object, status: int, body: bytes) -> None:
    await send(  # type: ignore[operator]
        {
            "type": "http.response.start",
            "status": status,
            "headers": [
                (b"content-type", b"application/json"),
                (b"content-length", str(len(body)).encode("ascii")),
            ],
        }
    )
    await send({"type": "http.response.body", "body": body})  # type: ignore[operator]


async def _empty(send: object, status: int) -> None:
    await send(  # type: ignore[operator]
        {
            "type": "http.response.start",
            "status": status,
            "headers": [(b"content-length", b"0")],
        }
    )
    await send({"type": "http.response.body", "body": b""})  # type: ignore[operator]

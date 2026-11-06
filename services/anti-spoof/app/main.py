"""FastAPI. GET /health, POST /v1/analyze, et l'upload si la doc est allumée.

Lancement local : les variables sont lues dans services/anti-spoof/.env.
ANTI_SPOOF_TOKEN est obligatoire. Port 8090.
ANTI_SPOOF_DOCS=true allume /docs et POST /v1/analyze-upload.
"""

from __future__ import annotations

import base64
import os
import sys
import time
from pathlib import Path

import uvicorn
from fastapi import Depends, FastAPI, File, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.openapi.utils import get_openapi
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.responses import Response

from app.auth import AuthRejected, BearerMiddleware, token_matches
from app.contract import AnalyzeRequest, Assessment, ErrorBody
from app.pipeline import ENGINE_VERSION, assess
from app.preprocess import InvalidImage, PayloadTooLarge
from app.serialize import ContractJSONResponse, dumps

HOST = "0.0.0.0"
PORT = 8090

_bearer = HTTPBearer(
    auto_error=False,
    scheme_name="bearer",
    description="Valeur de ANTI_SPOOF_TOKEN. Swagger ajoute le mot Bearer.",
)


def load_env_file(path: Path | None = None) -> None:
    """Remplit les variables absentes depuis .env. N'écrase pas l'environnement du processus."""
    env_path = path or Path(__file__).resolve().parent.parent / ".env"
    if not env_path.is_file():
        return
    for raw in env_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line.removeprefix("export ").strip()
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        if key and key not in os.environ:
            os.environ[key] = value


def load_token() -> str:
    token = os.environ.get("ANTI_SPOOF_TOKEN", "").strip()
    if not token:
        print("ANTI_SPOOF_TOKEN is required", file=sys.stderr)
        raise SystemExit(1)
    return token


def docs_enabled() -> bool:
    return os.environ.get("ANTI_SPOOF_DOCS", "").strip().lower() == "true"


def create_app() -> FastAPI:
    token = load_token()
    docs = docs_enabled()
    application = FastAPI(
        title="Recogniz-Me anti-spoof",
        version=ENGINE_VERSION,
        description=(
            "Mesure d'authenticité d'une image de pièce. "
            "La réponse ne contient pas de décision. "
            "anti-spoof-1 : les cinq détecteurs mesurent. La fusion est l'identité."
        ),
        docs_url="/docs" if docs else None,
        redoc_url=None,
        openapi_url="/openapi.json" if docs else None,
        default_response_class=ContractJSONResponse,
        swagger_ui_parameters={"persistAuthorization": True},
    )

    def _presented(credentials: HTTPAuthorizationCredentials | None) -> str:
        return credentials.credentials if credentials is not None else ""

    @application.exception_handler(AuthRejected)
    async def rejected(_request: Request, _exc: AuthRejected) -> Response:
        return Response(status_code=401)

    @application.exception_handler(RequestValidationError)
    async def invalid_request(_request: Request, _exc: RequestValidationError) -> ContractJSONResponse:
        return ContractJSONResponse(status_code=400, content={"error": "invalid_request"})

    @application.exception_handler(InvalidImage)
    async def invalid_image(_request: Request, _exc: InvalidImage) -> ContractJSONResponse:
        return ContractJSONResponse(status_code=400, content={"error": "invalid_image"})

    @application.exception_handler(PayloadTooLarge)
    async def payload_too_large(_request: Request, _exc: PayloadTooLarge) -> ContractJSONResponse:
        return ContractJSONResponse(status_code=413, content={"error": "payload_too_large"})

    @application.exception_handler(Exception)
    async def unexpected(_request: Request, exc: Exception) -> Response:
        if isinstance(exc, StarletteHTTPException):
            return Response(status_code=exc.status_code)
        # Le message peut citer le corps. On ne le recopie ni dans la réponse ni dans le journal.
        sys.stderr.write(dumps({"error": "internal", "type": type(exc).__name__}) + "\n")
        return ContractJSONResponse(status_code=500, content={"error": "internal"})

    @application.get("/health", tags=["anti-spoof"], summary="Sonde, sans jeton")
    def health() -> dict[str, str]:
        return {"status": "ok", "engine_version": ENGINE_VERSION}

    @application.post(
        "/v1/analyze",
        tags=["anti-spoof"],
        summary="Analyse JSON",
        description=(
            "Corps du contrat. Les cinq alertes peuvent monter. "
            "engine_version vaut anti-spoof-1 et fusion_version vaut identity."
        ),
        response_model=Assessment,
        responses={
            400: {"model": ErrorBody, "description": "JSON hors contrat, ou JPEG illisible."},
            401: {"description": "Jeton absent ou faux. Corps vide."},
            413: {"model": ErrorBody, "description": "Corps au-dessus de 8 Mo."},
            500: {"model": ErrorBody, "description": "Erreur interne. Corps sans extrait d'image."},
        },
    )
    async def analyze(
        body: AnalyzeRequest,
        credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    ) -> ContractJSONResponse:
        if not token_matches(_presented(credentials), token):
            raise AuthRejected()
        started = time.perf_counter()
        image = base64.b64decode(body.image_base64, validate=True)
        try:
            assessment = assess(image, body.hints)
        except InvalidImage:
            return ContractJSONResponse(status_code=400, content={"error": "invalid_image"})
        except Exception as exc:
            return _internal(exc)
        _journal(
            assessment,
            round((time.perf_counter() - started) * 1000),
            request_id=str(body.request_id),
            verification_id=str(body.verification_id),
            side=body.side,
        )
        return ContractJSONResponse(content=assessment.model_dump(mode="json"))

    protected = {"/v1/analyze"}
    if docs:

        @application.post(
            "/v1/analyze-upload",
            tags=["anti-spoof"],
            summary="Analyse d'un fichier",
            description="Même pipeline que POST /v1/analyze. Absent si ANTI_SPOOF_DOCS n'est pas true.",
            response_model=Assessment,
            responses={
                400: {"model": ErrorBody, "description": "Fichier absent ou JPEG illisible."},
                401: {"description": "Jeton absent ou faux. Corps vide."},
                413: {"model": ErrorBody, "description": "Corps au-dessus de 8 Mo."},
            },
        )
        async def analyze_upload(
            file: UploadFile = File(description="JPEG."),
            credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
        ) -> ContractJSONResponse:
            if not token_matches(_presented(credentials), token):
                raise AuthRejected()
            started = time.perf_counter()
            payload = await file.read()
            try:
                assessment = assess(payload)
            except InvalidImage:
                return ContractJSONResponse(status_code=400, content={"error": "invalid_image"})
            except Exception as exc:
                return _internal(exc)
            _journal(assessment, round((time.perf_counter() - started) * 1000))
            return ContractJSONResponse(content=assessment.model_dump(mode="json"))

        protected.add("/v1/analyze-upload")

    application.add_middleware(BearerMiddleware, token=token, paths=frozenset(protected))
    _install_openapi(application)
    return application


def _internal(exc: Exception) -> ContractJSONResponse:
    # Le message peut citer l'image. On ne garde que le type.
    sys.stderr.write(dumps({"error": "internal", "type": type(exc).__name__}) + "\n")
    return ContractJSONResponse(status_code=500, content={"error": "internal"})


def _journal(
    assessment: Assessment,
    duration_ms: int,
    request_id: str | None = None,
    verification_id: str | None = None,
    side: str | None = None,
) -> None:
    record: dict[str, object] = {}
    if request_id is not None:
        record["request_id"] = request_id
        record["verification_id"] = verification_id
        record["side"] = side
    record["duration_ms"] = duration_ms
    record["engine_version"] = assessment.engine_version
    record["forensic_usable"] = assessment.forensic_usable
    for alert in assessment.alerts:
        record[alert.code] = alert.score
    sys.stdout.write(dumps(record) + "\n")
    sys.stdout.flush()


def _install_openapi(application: FastAPI) -> None:
    def openapi() -> dict[str, object]:
        if application.openapi_schema:
            return application.openapi_schema
        schema = get_openapi(
            title=application.title,
            version=application.version,
            description=application.description,
            routes=application.routes,
        )
        for path_item in schema.get("paths", {}).values():
            for key, operation in path_item.items():
                if key.startswith("x-") or not isinstance(operation, dict):
                    continue
                responses = operation.get("responses")
                if isinstance(responses, dict):
                    responses.pop("422", None)
        application.openapi_schema = schema
        return schema

    application.openapi = openapi  # type: ignore[method-assign]


def main() -> None:
    uvicorn.run(app, host=HOST, port=PORT, log_level="info")


load_env_file()
app = create_app()


if __name__ == "__main__":
    main()

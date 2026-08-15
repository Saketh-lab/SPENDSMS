"""Shared HTTP helpers for SpendSMS Phase-0 Lambda foundations.

Never log request or response bodies. Safe fields only (Step-4 §9).
"""

from __future__ import annotations

import json
import logging
import os
import uuid
from typing import Any

SAFE_LOG = logging.getLogger("spendsms")
if not SAFE_LOG.handlers:
    handler = logging.StreamHandler()
    handler.setFormatter(logging.Formatter("%(message)s"))
    SAFE_LOG.addHandler(handler)
SAFE_LOG.setLevel(logging.INFO)
SAFE_LOG.propagate = False

UUID_RE = r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"


def max_body_bytes() -> int:
    return int(os.environ.get("MAX_BODY_BYTES", "65536"))


def headers_of(event: dict[str, Any]) -> dict[str, str]:
    raw = event.get("headers") or {}
    return {str(k).lower(): str(v) for k, v in raw.items() if v is not None}


def request_id(event: dict[str, Any], context: Any) -> str:
    hdrs = headers_of(event)
    incoming = hdrs.get("x-request-id")
    if incoming:
        return incoming
    aws_id = getattr(context, "aws_request_id", None)
    return str(aws_id) if aws_id else str(uuid.uuid4())


def log_safe(
    *,
    request_id_value: str,
    endpoint: str,
    status: int,
    error_code: str | None = None,
    duration_ms: int | None = None,
    app_version: str | None = None,
) -> None:
    payload: dict[str, Any] = {
        "requestId": request_id_value,
        "endpoint": endpoint,
        "status": status,
    }
    if error_code:
        payload["errorCode"] = error_code
    if duration_ms is not None:
        payload["durationMs"] = duration_ms
    if app_version:
        payload["appVersion"] = app_version
    SAFE_LOG.info(json.dumps(payload, separators=(",", ":")))


def error_body(
    *,
    code: str,
    message: str,
    retryable: bool,
    request_id_value: str,
    validation_errors: list[dict[str, str]] | None = None,
) -> dict[str, Any]:
    error: dict[str, Any] = {
        "code": code,
        "message": message,
        "retryable": retryable,
        "requestId": request_id_value,
    }
    if validation_errors:
        error["validationErrors"] = validation_errors[:20]
    return {"error": error}


def respond(
    status: int,
    body: dict[str, Any],
    *,
    retry_after: int | None = None,
) -> dict[str, Any]:
    headers = {"content-type": "application/json"}
    if retry_after is not None:
        headers["retry-after"] = str(retry_after)
    return {
        "statusCode": status,
        "headers": headers,
        "body": json.dumps(body, separators=(",", ":")),
    }


def raw_body(event: dict[str, Any]) -> tuple[bytes | None, dict[str, Any] | None]:
    body = event.get("body")
    if body is None:
        return None, error_body(
            code="VALIDATION_ERROR",
            message="Request body is required.",
            retryable=False,
            request_id_value="",
        )
    if event.get("isBase64Encoded"):
        import base64

        try:
            return base64.b64decode(body), None
        except Exception:
            return None, error_body(
                code="VALIDATION_ERROR",
                message="Body is not valid base64.",
                retryable=False,
                request_id_value="",
            )
    if isinstance(body, str):
        return body.encode("utf-8"), None
    return None, error_body(
        code="VALIDATION_ERROR",
        message="Request body is required.",
        retryable=False,
        request_id_value="",
    )

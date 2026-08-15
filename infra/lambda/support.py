"""POST /v1/support/unsupported-format foundation handler (Step-4)."""

from __future__ import annotations

import hashlib
import os
import time
from datetime import datetime, timedelta, timezone
from typing import Any

import boto3
from botocore.exceptions import ClientError

from http_util import (
    error_body,
    headers_of,
    log_safe,
    max_body_bytes,
    raw_body,
    request_id,
    respond,
)
from validation import ValidationFailure, parse_json_object, require_common_headers, validate_support

_DDB = boto3.resource("dynamodb")


def handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    started = time.monotonic()
    rid = request_id(event, context)
    headers = headers_of(event)
    endpoint = "POST /v1/support/unsupported-format"
    app_version = headers.get("x-app-version")

    def done(status: int, body: dict[str, Any], error_code: str | None = None, retry_after: int | None = None) -> dict[str, Any]:
        log_safe(
            request_id_value=rid,
            endpoint=endpoint,
            status=status,
            error_code=error_code,
            duration_ms=int((time.monotonic() - started) * 1000),
            app_version=app_version,
        )
        return respond(status, body, retry_after=retry_after)

    try:
        meta = require_common_headers(headers)
        raw, parse_err = raw_body(event)
        if parse_err is not None:
            parse_err["error"]["requestId"] = rid
            return done(400, parse_err, "VALIDATION_ERROR")
        assert raw is not None
        parsed = parse_json_object(raw, max_body_bytes())
        submission = validate_support(parsed, headers.get("idempotency-key"))
        status, response = _persist(submission, meta, hashlib.sha256(raw).hexdigest(), rid)
        return done(status, response, None if status == 201 else "IDEMPOTENCY_CONFLICT")
    except ValidationFailure as exc:
        status = 413 if exc.code == "PAYLOAD_TOO_LARGE" else 400
        errors = None
        if exc.field:
            errors = [{"field": exc.field, "code": exc.field_code, "message": exc.message}]
        return done(
            status,
            error_body(
                code=exc.code,
                message=exc.message,
                retryable=False,
                request_id_value=rid,
                validation_errors=errors,
            ),
            exc.code,
        )
    except ClientError:
        return done(
            503,
            error_body(
                code="SERVICE_UNAVAILABLE",
                message="Service temporarily unavailable.",
                retryable=True,
                request_id_value=rid,
            ),
            "SERVICE_UNAVAILABLE",
            retry_after=2,
        )
    except Exception:
        return done(
            500,
            error_body(
                code="INTERNAL_ERROR",
                message="Temporary internal service failure.",
                retryable=True,
                request_id_value=rid,
            ),
            "INTERNAL_ERROR",
            retry_after=2,
        )


def _persist(
    submission: dict[str, Any],
    meta: dict[str, str],
    payload_hash: str,
    request_id_value: str,
) -> tuple[int, dict[str, Any]]:
    table = _DDB.Table(os.environ["SUPPORT_TABLE"])
    ttl_days = int(os.environ.get("SUPPORT_TTL_DAYS", "14"))
    now = datetime.now(timezone.utc)
    expires = now + timedelta(days=ttl_days)
    ttl = int(expires.timestamp())
    pk = f"IDEMP#{submission['idempotencyKey']}"
    existing = table.get_item(Key={"pk": pk, "sk": "SUBMISSION"}).get("Item")
    if existing:
        if existing.get("payloadHash") == payload_hash:
            return 201, {
                "requestId": request_id_value,
                "submissionId": existing["submissionId"],
                "status": "accepted",
                "deletionScheduledAt": existing.get("deletionScheduledAt"),
            }
        return 409, error_body(
            code="IDEMPOTENCY_CONFLICT",
            message="Idempotency key was reused with a different payload.",
            retryable=False,
            request_id_value=request_id_value,
        )

    item = {
        "pk": pk,
        "sk": "SUBMISSION",
        "submissionId": submission["submissionId"],
        "payloadHash": payload_hash,
        "redactedTemplate": submission["redactedTemplate"],
        "reason": submission["reason"],
        "redactionVersion": submission["redactionVersion"],
        "consentedAt": submission["consentedAt"],
        "appVersion": meta["appVersion"],
        "parserVersion": meta.get("parserVersion") or None,
        "status": "accepted",
        "deletionScheduledAt": expires.replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "ttl": ttl,
    }
    table.put_item(Item=item, ConditionExpression="attribute_not_exists(pk)")
    return 201, {
        "requestId": request_id_value,
        "submissionId": submission["submissionId"],
        "status": "accepted",
        "deletionScheduledAt": item["deletionScheduledAt"],
    }

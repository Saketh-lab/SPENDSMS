"""POST /v1/telemetry/batch foundation handler (Step-4)."""

from __future__ import annotations

import os
import time
from datetime import datetime, timezone
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
from validation import ValidationFailure, parse_json_object, require_common_headers, validate_telemetry

_DDB = boto3.resource("dynamodb")


def handler(event: dict[str, Any], context: Any) -> dict[str, Any]:
    started = time.monotonic()
    rid = request_id(event, context)
    headers = headers_of(event)
    endpoint = "POST /v1/telemetry/batch"
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
        batch = validate_telemetry(parsed)
        accepted, duplicates = _persist(batch, meta)
        return done(
            202,
            {
                "requestId": rid,
                "batchId": batch["batchId"],
                "acceptedEvents": accepted,
                "duplicateEvents": duplicates,
            },
        )
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


def _persist(batch: dict[str, Any], meta: dict[str, str]) -> tuple[int, int]:
    table = _DDB.Table(os.environ["TELEMETRY_TABLE"])
    ttl_days = int(os.environ.get("TELEMETRY_TTL_DAYS", "30"))
    now = datetime.now(timezone.utc)
    ttl = int(now.timestamp()) + ttl_days * 86400
    date_bucket = now.date().isoformat()
    accepted = 0
    duplicates = 0
    for event in batch["events"]:
        pk = f"EVENT#{event['eventId']}"
        item = {
            "pk": pk,
            "sk": "EVENT",
            "eventId": event["eventId"],
            "batchId": batch["batchId"],
            "eventType": event["eventType"],
            "occurredAt": event["occurredAt"],
            "appVersion": meta["appVersion"],
            "ttl": ttl,
        }
        try:
            table.put_item(
                Item=item,
                ConditionExpression="attribute_not_exists(pk)",
            )
            accepted += 1
            table.update_item(
                Key={
                    "pk": f"AGG#{date_bucket}",
                    "sk": f"{event['eventType']}#{meta['appVersion']}",
                },
                UpdateExpression="ADD #c :one SET eventType = :et, dateBucket = :db, appVersion = :av, ttl = :ttl",
                ExpressionAttributeNames={"#c": "count"},
                ExpressionAttributeValues={
                    ":one": 1,
                    ":et": event["eventType"],
                    ":db": date_bucket,
                    ":av": meta["appVersion"],
                    ":ttl": ttl,
                },
            )
        except ClientError as exc:
            if exc.response.get("Error", {}).get("Code") == "ConditionalCheckFailedException":
                duplicates += 1
            else:
                raise
    return accepted, duplicates

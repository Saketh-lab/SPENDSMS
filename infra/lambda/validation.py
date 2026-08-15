"""Step-4 / OpenAPI request validation for SpendSMS Phase-0 Lambda foundations."""

from __future__ import annotations

import json
import re
from typing import Any

from http_util import UUID_RE

TELEMETRY_EVENT_TYPES = {
    "scan_started",
    "scan_completed",
    "scan_failed",
    "parser_update_checked",
    "parser_update_activated",
    "parser_update_failed",
    "app_error",
}

DURATION_BUCKETS = {
    "LT_1S",
    "1S_5S",
    "5S_15S",
    "15S_30S",
    "30S_60S",
    "1M_5M",
    "GT_5M",
}

COUNT_BUCKETS = {"0", "1_10", "11_50", "51_200", "201_1000", "1001_PLUS"}

SUPPORT_REASONS = {
    "no_rule_match",
    "parse_failed",
    "incorrect_extraction",
    "other",
}

PROHIBITED_KEYS = {
    "sms",
    "smsbody",
    "body",
    "amount",
    "currency",
    "merchant",
    "institution",
    "account",
    "card",
    "upi",
    "reference",
    "transaction",
    "transactions",
    "category",
    "subscription",
    "sender",
    "totals",
}

_UUID = re.compile(UUID_RE)
_ERROR_CODE = re.compile(r"^[A-Z0-9_]{3,64}$")


class ValidationFailure(Exception):
    def __init__(
        self,
        code: str,
        message: str,
        field: str | None = None,
        field_code: str = "INVALID",
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.field = field
        self.field_code = field_code


def require_common_headers(headers: dict[str, str]) -> dict[str, str]:
    missing: list[str] = []
    for name in ("x-app-version", "x-platform", "x-os-version", "x-install-id"):
        if not headers.get(name, "").strip():
            missing.append(name)
    if missing:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Required headers are missing.",
            field=",".join(missing),
            field_code="MISSING",
        )
    if headers.get("x-platform") != "android":
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Unsupported platform.",
            field="X-Platform",
            field_code="UNSUPPORTED_VALUE",
        )
    install_id = headers["x-install-id"]
    if not _UUID.match(install_id):
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "X-Install-Id must be a UUID.",
            field="X-Install-Id",
            field_code="INVALID_FORMAT",
        )
    return {
        "appVersion": headers["x-app-version"][:32],
        "osVersion": headers["x-os-version"][:16],
        "installId": install_id,
        "parserVersion": (headers.get("x-parser-version") or "")[:64],
    }


def parse_json_object(raw: bytes, max_bytes: int) -> dict[str, Any]:
    if len(raw) > max_bytes:
        raise ValidationFailure("PAYLOAD_TOO_LARGE", "Request exceeds endpoint payload limits.")
    try:
        parsed = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValidationFailure("VALIDATION_ERROR", "Body is not valid JSON.") from exc
    if not isinstance(parsed, dict):
        raise ValidationFailure("VALIDATION_ERROR", "Body must be a JSON object.")
    reject_prohibited_keys(parsed)
    return parsed


def reject_prohibited_keys(value: Any, path: str = "") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            normalized = re.sub(r"[^a-z0-9]", "", str(key).lower())
            if normalized in PROHIBITED_KEYS:
                raise ValidationFailure(
                    "PROHIBITED_FIELD",
                    "The request contains a privacy-prohibited field.",
                    field=f"{path}{key}" if path else str(key),
                    field_code="PROHIBITED_FIELD",
                )
            reject_prohibited_keys(child, f"{path}{key}.")
    elif isinstance(value, list):
        for idx, child in enumerate(value):
            reject_prohibited_keys(child, f"{path}[{idx}].")


def validate_telemetry(body: dict[str, Any]) -> dict[str, Any]:
    allowed = {"schemaVersion", "batchId", "events"}
    extra = set(body) - allowed
    if extra:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Unknown properties are not allowed.",
            field=sorted(extra)[0],
            field_code="UNKNOWN_PROPERTY",
        )
    if body.get("schemaVersion") != 1:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Unsupported schemaVersion.",
            field="schemaVersion",
            field_code="UNSUPPORTED_VALUE",
        )
    batch_id = body.get("batchId")
    if not isinstance(batch_id, str) or not _UUID.match(batch_id):
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "batchId must be a UUID.",
            field="batchId",
            field_code="INVALID_FORMAT",
        )
    events = body.get("events")
    if not isinstance(events, list) or not 1 <= len(events) <= 50:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "events must contain 1–50 items.",
            field="events",
            field_code="INVALID",
        )
    cleaned: list[dict[str, Any]] = []
    for i, event in enumerate(events):
        if not isinstance(event, dict):
            raise ValidationFailure(
                "VALIDATION_ERROR",
                "Each event must be an object.",
                field=f"events[{i}]",
                field_code="INVALID",
            )
        cleaned.append(_validate_telemetry_event(event, i))
    return {"schemaVersion": 1, "batchId": batch_id, "events": cleaned}


def _validate_telemetry_event(event: dict[str, Any], index: int) -> dict[str, Any]:
    allowed = {
        "eventId",
        "occurredAt",
        "eventType",
        "durationBucket",
        "countBucket",
        "errorCode",
    }
    extra = set(event) - allowed
    if extra:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Unknown properties are not allowed.",
            field=f"events[{index}].{sorted(extra)[0]}",
            field_code="UNKNOWN_PROPERTY",
        )
    event_id = event.get("eventId")
    if not isinstance(event_id, str) or not _UUID.match(event_id):
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "eventId must be a UUID.",
            field=f"events[{index}].eventId",
            field_code="INVALID_FORMAT",
        )
    occurred = event.get("occurredAt")
    if not isinstance(occurred, str) or not occurred.strip():
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "occurredAt is required.",
            field=f"events[{index}].occurredAt",
            field_code="MISSING",
        )
    event_type = event.get("eventType")
    if event_type not in TELEMETRY_EVENT_TYPES:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Unsupported event type.",
            field=f"events[{index}].eventType",
            field_code="UNSUPPORTED_VALUE",
        )
    out: dict[str, Any] = {
        "eventId": event_id,
        "occurredAt": occurred,
        "eventType": event_type,
    }
    if "durationBucket" in event:
        if event["durationBucket"] not in DURATION_BUCKETS:
            raise ValidationFailure(
                "VALIDATION_ERROR",
                "Unsupported duration bucket.",
                field=f"events[{index}].durationBucket",
                field_code="UNSUPPORTED_VALUE",
            )
        out["durationBucket"] = event["durationBucket"]
    if "countBucket" in event:
        if event["countBucket"] not in COUNT_BUCKETS:
            raise ValidationFailure(
                "VALIDATION_ERROR",
                "Unsupported count bucket.",
                field=f"events[{index}].countBucket",
                field_code="UNSUPPORTED_VALUE",
            )
        out["countBucket"] = event["countBucket"]
    if "errorCode" in event:
        code = event["errorCode"]
        if not isinstance(code, str) or not _ERROR_CODE.match(code) or len(code) > 64:
            raise ValidationFailure(
                "VALIDATION_ERROR",
                "Invalid errorCode.",
                field=f"events[{index}].errorCode",
                field_code="INVALID_FORMAT",
            )
        out["errorCode"] = code
    return out


def validate_support(body: dict[str, Any], idempotency_key: str | None) -> dict[str, Any]:
    if not idempotency_key or not _UUID.match(idempotency_key):
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Idempotency-Key must be a UUID.",
            field="Idempotency-Key",
            field_code="INVALID_FORMAT",
        )
    allowed = {
        "schemaVersion",
        "submissionId",
        "consentedAt",
        "previewConfirmed",
        "redactionVersion",
        "reason",
        "redactedTemplate",
    }
    extra = set(body) - allowed
    if extra:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Unknown properties are not allowed.",
            field=sorted(extra)[0],
            field_code="UNKNOWN_PROPERTY",
        )
    if body.get("schemaVersion") != 1:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Unsupported schemaVersion.",
            field="schemaVersion",
            field_code="UNSUPPORTED_VALUE",
        )
    submission_id = body.get("submissionId")
    if not isinstance(submission_id, str) or not _UUID.match(submission_id):
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "submissionId must be a UUID.",
            field="submissionId",
            field_code="INVALID_FORMAT",
        )
    if body.get("previewConfirmed") is not True:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "previewConfirmed must be true.",
            field="previewConfirmed",
            field_code="UNSUPPORTED_VALUE",
        )
    consented = body.get("consentedAt")
    if not isinstance(consented, str) or not consented.strip():
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "consentedAt is required.",
            field="consentedAt",
            field_code="MISSING",
        )
    redaction_version = body.get("redactionVersion")
    if not isinstance(redaction_version, str) or not redaction_version.strip() or len(redaction_version) > 32:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "redactionVersion is invalid.",
            field="redactionVersion",
            field_code="INVALID",
        )
    reason = body.get("reason")
    if reason not in SUPPORT_REASONS:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "Unsupported reason.",
            field="reason",
            field_code="UNSUPPORTED_VALUE",
        )
    template = body.get("redactedTemplate")
    if not isinstance(template, str) or not template.strip() or len(template) > 4096:
        raise ValidationFailure(
            "VALIDATION_ERROR",
            "redactedTemplate is invalid.",
            field="redactedTemplate",
            field_code="INVALID",
        )
    return {
        "schemaVersion": 1,
        "submissionId": submission_id,
        "consentedAt": consented,
        "previewConfirmed": True,
        "redactionVersion": redaction_version.strip(),
        "reason": reason,
        "redactedTemplate": template,
        "idempotencyKey": idempotency_key,
    }

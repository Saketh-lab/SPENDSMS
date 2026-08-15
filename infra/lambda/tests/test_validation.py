from __future__ import annotations

import json
import os
import sys
import unittest

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, ROOT)

from validation import (  # noqa: E402
    ValidationFailure,
    parse_json_object,
    require_common_headers,
    validate_support,
    validate_telemetry,
)


class HeaderTests(unittest.TestCase):
    def test_requires_android_platform(self) -> None:
        with self.assertRaises(ValidationFailure):
            require_common_headers(
                {
                    "x-app-version": "0.1.0",
                    "x-platform": "ios",
                    "x-os-version": "35",
                    "x-install-id": "018f2c80-0b53-7e53-bf26-f6a84d7e4c11",
                }
            )


class TelemetryTests(unittest.TestCase):
    def test_accepts_allowlisted_event(self) -> None:
        body = {
            "schemaVersion": 1,
            "batchId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c11",
            "events": [
                {
                    "eventId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c12",
                    "occurredAt": "2026-08-10T12:20:00Z",
                    "eventType": "scan_completed",
                    "durationBucket": "30S_60S",
                    "countBucket": "51_200",
                }
            ],
        }
        cleaned = validate_telemetry(body)
        self.assertEqual(1, len(cleaned["events"]))

    def test_rejects_unknown_event_type(self) -> None:
        body = {
            "schemaVersion": 1,
            "batchId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c11",
            "events": [
                {
                    "eventId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c12",
                    "occurredAt": "2026-08-10T12:20:00Z",
                    "eventType": "sms_uploaded",
                }
            ],
        }
        with self.assertRaises(ValidationFailure) as ctx:
            validate_telemetry(body)
        self.assertEqual("VALIDATION_ERROR", ctx.exception.code)

    def test_rejects_prohibited_amount_field(self) -> None:
        raw = json.dumps(
            {
                "schemaVersion": 1,
                "batchId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c11",
                "events": [],
                "amount": 120,
            }
        ).encode()
        with self.assertRaises(ValidationFailure) as ctx:
            parse_json_object(raw, 65536)
        self.assertEqual("PROHIBITED_FIELD", ctx.exception.code)


class SupportTests(unittest.TestCase):
    def test_requires_preview_confirmed(self) -> None:
        body = {
            "schemaVersion": 1,
            "submissionId": "018f2c80-0b53-7e53-bf26-f6a84d7e4d01",
            "consentedAt": "2026-08-10T12:21:00Z",
            "previewConfirmed": False,
            "redactionVersion": "1",
            "reason": "no_rule_match",
            "redactedTemplate": "Debited [AMOUNT]",
        }
        with self.assertRaises(ValidationFailure):
            validate_support(body, "018f2c80-0b53-7e53-bf26-f6a84d7e4d02")

    def test_accepts_redacted_template(self) -> None:
        body = {
            "schemaVersion": 1,
            "submissionId": "018f2c80-0b53-7e53-bf26-f6a84d7e4d01",
            "consentedAt": "2026-08-10T12:21:00Z",
            "previewConfirmed": True,
            "redactionVersion": "1",
            "reason": "no_rule_match",
            "redactedTemplate": "Debited [AMOUNT] at [MERCHANT]",
        }
        cleaned = validate_support(body, "018f2c80-0b53-7e53-bf26-f6a84d7e4d02")
        self.assertEqual("no_rule_match", cleaned["reason"])


if __name__ == "__main__":
    unittest.main()

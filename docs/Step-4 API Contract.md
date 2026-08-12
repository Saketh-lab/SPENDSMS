# SpendSMS — Step-4 API Contract

**Document:** `Step-4_API_Contract.md`
**Scope:** Phase-0 MVP
**API style:** JSON over HTTPS
**Backend:** API Gateway HTTP API + Lambda
**Authentication:** No user authentication in Phase-0

---

## 1. Purpose

SpendSMS is local-first. The Android device remains the system of record for SMS-derived financial data.

The MVP API exists only for:

1. Privacy-safe batched telemetry.
2. Explicitly consented, on-device-redacted unsupported-format submissions.

The following are **not APIs** and should be delivered directly through signed/versioned S3 + CloudFront objects:

* Parser manifest.
* Parser-rule bundles.
* Non-sensitive remote configuration.
* Legal/privacy/support static content.

The mobile API must never accept:

* Raw SMS.
* Normalised transaction history.
* Transaction amounts.
* Merchant names or merchant history.
* Account/card identifiers.
* Transaction references.
* Categories or category history.
* Subscription names/history.
* Financial account data.

---

# 2. MVP API Surface

| Method | Path                             | Purpose                                                      |
| ------ | -------------------------------- | ------------------------------------------------------------ |
| `POST` | `/v1/telemetry/batch`            | Upload allow-listed coarse operational telemetry             |
| `POST` | `/v1/support/unsupported-format` | Submit explicitly approved redacted unsupported-SMS template |

`/v1/support/unsupported-format` may be disabled by Phase-0 feature configuration if the support-submission feature is not shipped.

No other public mobile APIs are required.

---

# 3. Common Transport Contract

## HTTPS

All requests use HTTPS.

The Android client must:

* Use platform certificate validation.
* Maintain a strict endpoint allow-list.
* Never place credentials or sensitive financial values in URLs.
* Never serialize transaction-domain objects into network requests.

---

## Common mobile headers

| Header             | Required | Description                           |
| ------------------ | -------: | ------------------------------------- |
| `Content-Type`     |      Yes | `application/json`                    |
| `Accept`           |      Yes | `application/json`                    |
| `X-App-Version`    |      Yes | Android application version           |
| `X-Platform`       |      Yes | Always `android`                      |
| `X-OS-Version`     |      Yes | Android API level/version             |
| `X-Parser-Version` |       No | Active parser version where available |
| `X-Install-Id`     |      Yes | Random, resettable installation UUID  |
| `X-Request-Id`     |       No | Client-generated UUID for tracing     |

`X-Install-Id`:

* Is not a user account.
* Is not authentication.
* Must be resettable.
* Must not be used for cross-app tracking.
* May be used for abuse prevention and rate limiting.

No secret API key is embedded in the APK.

---

# 4. Standard Error Contract

All API errors use:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "retryable": false,
    "requestId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c11",
    "validationErrors": [
      {
        "field": "events[0].eventType",
        "code": "UNSUPPORTED_VALUE",
        "message": "Unsupported event type."
      }
    ]
  }
}
```

Rules:

* Messages must be safe for application logs.
* No stack traces.
* No AWS implementation details.
* No request body reflection.
* No sensitive values.
* `validationErrors` is optional.
* `requestId` is always returned when available.

Common codes:

| HTTP  | Code                   | Retry |
| ----- | ---------------------- | ----- |
| `400` | `VALIDATION_ERROR`     | No    |
| `400` | `PROHIBITED_FIELD`     | No    |
| `409` | `IDEMPOTENCY_CONFLICT` | No    |
| `413` | `PAYLOAD_TOO_LARGE`    | No    |
| `429` | `RATE_LIMITED`         | Yes   |
| `500` | `INTERNAL_ERROR`       | Yes   |
| `503` | `SERVICE_UNAVAILABLE`  | Yes   |

`429` and temporary `5xx` responses may include `Retry-After`.

---

# 5. POST `/v1/telemetry/batch`

## Purpose

Upload a bounded batch of strictly allow-listed operational/product events.

Telemetry must never block scanning, dashboard usage, corrections, or deletion.

## Authentication

None.

`X-Install-Id` is used only as a resettable pseudonymous installation identifier and rate-limiting dimension.

---

## Request

Maximum:

* 50 events.
* 64 KiB uncompressed JSON body.
* Unknown fields rejected.

```json
{
  "schemaVersion": 1,
  "batchId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c11",
  "events": [
    {
      "eventId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c12",
      "occurredAt": "2026-08-10T12:20:00Z",
      "eventType": "scan_completed",
      "durationBucket": "30s_60s",
      "countBucket": "51_200"
    }
  ]
}
```

---

## Allowed event types

Phase-0 allow-list:

```text
scan_started
scan_completed
scan_failed
parser_update_checked
parser_update_activated
parser_update_failed
app_error
```

A new event type requires:

1. Schema review.
2. Privacy review.
3. Server allow-list deployment.
4. Client schema version compatibility.

---

## Allowed event fields

| Field            | Sensitive | Notes                       |
| ---------------- | --------: | --------------------------- |
| `eventId`        |        No | Random UUID                 |
| `occurredAt`     |        No | UTC timestamp               |
| `eventType`      |        No | Allow-listed enum           |
| `durationBucket` |        No | Coarse bucket only          |
| `countBucket`    |        No | Coarse bucket only          |
| `errorCode`      |        No | Allow-listed technical code |

App, OS, parser, and installation metadata are supplied in request headers.

---

## Count buckets

```text
0
1_10
11_50
51_200
201_1000
1001_PLUS
```

Exact SMS or transaction counts must not be uploaded.

---

## Duration buckets

```text
LT_1S
1S_5S
5S_15S
15S_30S
30S_60S
1M_5M
GT_5M
```

---

## Prohibited telemetry

The server must reject requests containing fields representing:

* SMS body.
* SMS sender content intended to identify transaction details.
* Amount.
* Currency tied to a transaction.
* Merchant.
* Institution tied to a transaction.
* Account/card identifier.
* UPI identifier.
* Transaction reference.
* Transaction timestamp/history.
* Category.
* Subscription name.
* User correction value.
* Financial totals.

Unknown JSON properties are rejected.

---

## Response

### `202 Accepted`

```json
{
  "requestId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c20",
  "batchId": "018f2c80-0b53-7e53-bf26-f6a84d7e4c11",
  "acceptedEvents": 1,
  "duplicateEvents": 0
}
```

---

## Validation

* `schemaVersion` must equal a supported schema version.
* `batchId` must be a UUID.
* `events` must contain 1–50 items.
* Every `eventId` must be a UUID.
* `eventType` must be allow-listed.
* Unknown properties are rejected.
* Body must be ≤64 KiB uncompressed.
* Prohibited keys or values cause request rejection.

---

## Idempotency

`eventId` is the event-level idempotency key.

Resending the same event ID must not create a second stored event or increment the same aggregate twice.

`batchId` is used for tracing, not event uniqueness.

---

## Retry

Retry:

* Network failure.
* `429`.
* `500`.
* `502`.
* `503`.
* `504`.

Do not retry validation/privacy failures.

Client strategy:

```text
1s
2s
4s
8s
16s
```

Apply jitter.

Maximum five attempts per scheduled upload cycle.

Offline events remain in the bounded local telemetry outbox.

Old events may be dropped rather than affecting the user experience.

---

## Rate limiting

Apply API Gateway/Lambda rate limiting using:

* Installation ID.
* Source IP.
* Global service limits.

The exact server rate is deployment configuration, not part of the mobile compatibility contract.

Return `429 RATE_LIMITED` with `Retry-After` when practical.

---

# 6. POST `/v1/support/unsupported-format`

## Purpose

Allow a user to voluntarily submit an unsupported transaction-message template after:

1. On-device redaction.
2. User preview.
3. Explicit user consent.

Raw SMS must never be submitted.

---

## Authentication

None.

Use the resettable `X-Install-Id` only for rate limiting and abuse prevention.

---

## Idempotency header

Required:

```text
Idempotency-Key: <UUID>
```

The same key with the same body returns the original logical result.

The same key with a different body returns:

```text
409 IDEMPOTENCY_CONFLICT
```

---

## Request

Maximum body size: **16 KiB**.

```json
{
  "schemaVersion": 1,
  "submissionId": "018f2c80-0b53-7e53-bf26-f6a84d7e4d01",
  "consentedAt": "2026-08-10T12:21:00Z",
  "previewConfirmed": true,
  "redactionVersion": "1",
  "reason": "no_rule_match",
  "redactedTemplate": "Your account [ACCOUNT] was debited by [AMOUNT] on [DATE] at [MERCHANT]. Ref [REFERENCE]."
}
```

---

## Reason values

```text
no_rule_match
parse_failed
incorrect_extraction
other
```

---

## Validation

The server must require:

* Supported `schemaVersion`.
* UUID `submissionId`.
* `previewConfirmed == true`.
* Valid UTC `consentedAt`.
* Supported `redactionVersion`.
* Allow-listed `reason`.
* Non-empty redacted template.
* `redactedTemplate` ≤4096 characters.
* Whole request ≤16 KiB.
* No unknown JSON properties.

Server-side defense-in-depth validation should reject obvious unredacted values such as:

* Long account/card-number sequences.
* Transaction-reference patterns.
* URLs.
* Obvious unredacted amounts.
* Other known sensitive patterns.

Server validation does not replace on-device redaction and user preview.

---

## Response

### `201 Created`

```json
{
  "requestId": "018f2c80-0b53-7e53-bf26-f6a84d7e4d10",
  "submissionId": "018f2c80-0b53-7e53-bf26-f6a84d7e4d01",
  "status": "accepted",
  "deletionScheduledAt": "2026-08-24T12:21:00Z"
}
```

`deletionScheduledAt` reflects the configured short-retention TTL.

The actual retention period remains an operational/privacy-policy configuration and must match published policy.

---

## Retry

Retry only:

* Network failure.
* `429`.
* Retryable `5xx`.

Requirements:

* Reuse the identical `Idempotency-Key`.
* Reuse the exact user-previewed redacted payload.
* Never reconstruct a retry from raw SMS.
* Maximum three automatic attempts.
* Stop retrying if the approved local redacted payload is discarded.

---

## Privacy restrictions

Support storage must:

* Contain only the user-previewed redacted template and safe metadata.
* Use short TTL retention.
* Restrict employee access.
* Avoid request-body logging.
* Avoid CloudWatch payload logging.
* Never combine the support sample with financial transaction history.
* Support deletion according to the published support/privacy process.

---

# 7. APIs Explicitly Not Created

The MVP must not expose:

```text
POST /sms
POST /messages
POST /transactions
GET  /transactions
POST /financial-history
POST /merchants
POST /accounts
POST /subscriptions
POST /sync
POST /backup
```

No equivalent generic upload endpoint should exist.

---

# 8. Static CloudFront Delivery

The following are delivered as signed/versioned static objects rather than Lambda APIs:

```text
Remote configuration
Parser manifest
Parser-rule packages
Legal/privacy pages
Public support content
```

Client behaviour:

* Cache configuration for approximately 6–24 hours.
* Use `ETag` / `If-None-Match`.
* Use immutable URLs for versioned parser bundles.
* Verify parser signatures and checksums before activation.
* Continue using bundled/last-known-good data when offline.

---

# 9. Logging Contract

Backend structured logs may contain:

```text
requestId
endpoint
HTTP status
safe error code
duration
app version
coarse operational metrics
```

Logs must not contain:

* Request body.
* Response body containing submitted template content.
* Raw SMS.
* Amount.
* Merchant.
* Account/card identifier.
* Transaction reference.
* Financial transaction history.

---

# 10. Phase-0 API Decision

The final public mobile API surface is:

```text
POST /v1/telemetry/batch
POST /v1/support/unsupported-format   # only when support submission is enabled
```

All configuration, parser, and public static content is delivered through S3 + CloudFront.

This keeps the Phase-0 backend a small control/observability plane and preserves the Android device as the sole system of record for user financial data.

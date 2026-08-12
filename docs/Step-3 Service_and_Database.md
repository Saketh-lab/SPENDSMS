# SpendSMS — Step-3 Service and Database Design

**Document:** `Step-3_Service_and_DB_Design.md`
**Scope:** Phase-0 MVP only
**Platform:** Android-first
**Architecture:** Local-first Android application with thin AWS control plane
**Status:** Implementation design

---

## 1. Purpose

This document converts the approved SpendSMS MVP architecture into implementation-level module boundaries and database design.

The Phase-0 design keeps all financial processing and financial history on the Android device.

Core principles:

* Raw SMS remains on-device.
* Raw SMS content is not persisted after processing.
* Normalised transactions are stored locally in Room/SQLite.
* Parsing, deduplication, categorisation, subscription detection, corrections, and dashboard calculation run locally.
* AWS is limited to parser/config distribution, privacy-safe telemetry, legal/static content, and explicitly consented redacted support submissions.
* No cloud transaction ledger.
* No mandatory user account.
* No microservices.
* Scans and rescans must be resumable and idempotent.

---

## 2. Module Overview

```text
Presentation
    |
    v
Application / Use Cases
    |
    +----------------------------------------------+
    |                                              |
    v                                              v
Scan Coordinator                            User Correction Service
    |
    v
SMS Source
    |
    v
Message Filter
    |
    v
Transaction Parser
    |
    v
Merchant Normalizer
    |
    v
Duplicate Detector
    |
    v
Categorisation Engine
    |
    v
Transaction Repository / Room
    |
    +-----------------------+
    |                       |
    v                       v
Subscription Detector   Dashboard Calculator
    |                       |
    +----------+------------+
               |
               v
              UI

Supporting modules:

Parser Bundle Manager
Remote Config Provider
Telemetry Client
Support Submission Client
Privacy / Data Deletion Service
```

These are modules inside the Android application, not independently deployed backend services.

---

## 3. Android Modules and Services

### 3.1 SMS Source

**Responsibility**

Read SMS messages for the user-selected time range through the approved Android access mechanism.

The source must never modify, delete, send, or mark SMS messages as read.

**Inputs**

* Start timestamp.
* End timestamp.
* Pagination/batch cursor.
* Cancellation signal.

**Outputs**

Ephemeral message records:

```text
sourceMessageId
sender
receivedAt
body
```

`body` exists only in the processing pipeline and must not be written to the application database.

**Dependencies**

* Android SMS provider.
* Permission state.
* Scan coordinator.

**Data owned**

No persistent domain data.

**Failure handling**

* Permission denied → fail scan with actionable status.
* Permission revoked mid-scan → stop current batch and preserve completed batches.
* Provider/query error → mark scan interrupted.
* App termination → resume using persisted scan state.

**Testing**

* Fake SMS content provider.
* Pagination tests.
* Permission-loss tests.
* Date-range boundary tests.
* Cancellation tests.
* Large inbox/bounded-memory tests.

---

### 3.2 Message Filter

**Responsibility**

Reject messages that are clearly outside the financial transaction scope before parsing.

Examples:

* OTPs.
* Marketing.
* Delivery updates.
* Personal conversations.
* General service messages.
* Non-transactional bank notices.

**Inputs**

Ephemeral SMS record.

**Outputs**

```kotlin
sealed interface FilterResult {
    data object Reject : FilterResult
    data class Accept(val message: SmsMessage) : FilterResult
}
```

**Dependencies**

* Bundled filtering rules.
* Active parser bundle.

**Data owned**

None.

**Failure handling**

Unexpected rule failures should fail closed for that message rather than creating a false transaction.

**Testing**

* Synthetic transactional corpus.
* OTP/marketing/personal-message negatives.
* Institution-specific sender cases.
* Regression tests for false positives.

---

### 3.3 Transaction Parser

**Responsibility**

Convert an accepted financial SMS into a normalised transaction candidate.

**Inputs**

* Ephemeral SMS.
* Active parser rule set.
* Parser version.

**Outputs**

```text
amountMinorUnits
currency
transactionTimestamp
merchantRaw
institution
maskedAccount
direction
paymentMethod
referenceHash
confidence
parserVersion
```

**Dependencies**

* Parser rule repository.
* Confidence policy.
* Date/currency parsing utilities.

**Data owned**

No persistent data directly.

**Failure handling**

* Unsupported template → no transaction created.
* Required extraction failure → reject candidate.
* Partial extraction → candidate allowed only where confidence policy permits.
* Rule runtime error → isolate affected message and record privacy-safe error code.

**Testing**

* Version-controlled parser regression corpus.
* Supported Indian bank/card/UPI examples.
* Refund, transfer, reversal, ATM, debit, and credit cases.
* Malformed inputs.
* Parser-version regression tests.

---

### 3.4 Merchant Normalizer

**Responsibility**

Convert noisy merchant/recipient text into a stable local merchant key and display value.

**Inputs**

* Parser merchant/recipient value.
* Institution/context.
* Merchant alias rules.

**Outputs**

```text
merchantKey
merchantDisplayName
```

**Dependencies**

* Bundled/remote merchant alias mappings.
* User correction rules.

**Data owned**

No independent persistent data required for Phase-0.

**Failure handling**

If no rule matches:

```text
merchantKey = normalized parser text
merchantDisplayName = cleaned parser text
```

The service must not block transaction creation.

**Testing**

* Case/punctuation normalization.
* UPI handles.
* Payment-processor prefixes.
* Alias mapping.
* Unknown merchant fallback.
* User-rule precedence.

---

### 3.5 Duplicate Detector

**Responsibility**

Prevent multiple SMS alerts representing the same financial transaction from inflating totals.

**Inputs**

Transaction candidate plus nearby stored transactions.

Fingerprint inputs may include:

* Institution.
* Amount.
* Timestamp window.
* Masked account.
* Reference hash.
* Merchant.
* Direction.

**Outputs**

```text
transactionFingerprint
duplicateStatus
possibleDuplicateOf
```

**Dependencies**

* Transaction repository.

**Data owned**

Duplicate status is stored with the transaction.

**Failure handling**

Ambiguous matches must be marked as suspected duplicates rather than destructive merging.

User overrides take precedence.

**Testing**

* Exact rescans.
* Multiple alerts for one transaction.
* Same merchant/amount occurring legitimately twice.
* Reference-present/reference-missing cases.
* Timestamp-window edges.

---

### 3.6 Categorisation Engine

**Responsibility**

Assign one MVP category to each eligible transaction.

**Precedence**

```text
User correction rule
    >
Exact merchant rule
    >
Institution / transaction-pattern rule
    >
Keyword rule
    >
Other
```

**Inputs**

* Effective merchant.
* Direction.
* Payment method.
* Institution.
* Parser-derived attributes.

**Outputs**

```text
categoryId
categoryConfidence
```

**Dependencies**

* Category repository.
* Correction repository.
* Parser/category rules.

**Data owned**

None directly.

**Failure handling**

Unknown cases always fall back to `Other`.

**Testing**

* Category-rule precedence.
* Default fallback.
* Income/refund and transfer handling.
* User-defined correction persistence.

---

### 3.7 Scan Coordinator

**Responsibility**

Orchestrate the complete analysis pipeline in bounded, resumable batches.

**Inputs**

* Analysis period.
* Active parser version.
* User cancellation.

**Outputs**

* Scan progress.
* Scan completion result.
* Counts suitable for local UI and coarse telemetry.

**Dependencies**

* SMS Source.
* Filter.
* Parser.
* Merchant normalizer.
* Duplicate detector.
* Categorisation engine.
* Transaction repository.
* Subscription detector.
* Dashboard calculator.
* WorkManager.

**Data owned**

`scan_state`.

**Failure handling**

Each completed batch is committed atomically.

A failed or killed scan:

* Does not remove existing transactions.
* Persists the last processed source identifier.
* Can resume safely.
* Does not generate duplicate transactions.

**Testing**

* Kill/restart between batches.
* Permission revocation.
* Database write failure.
* Low-storage condition.
* Cancellation.
* Repeat complete scan produces same effective result.

---

### 3.8 Subscription Detector

**Responsibility**

Identify recurring transaction patterns and create suspected subscription records.

**Inputs**

Eligible stored transactions grouped primarily by merchant.

Signals:

* Stable merchant.
* Similar amounts.
* Repeated intervals.
* Recurring/mandate wording.
* Minimum occurrence count.

**Outputs**

Suspected subscription plus evidence transaction links.

**Dependencies**

* Transaction repository.
* Subscription repository.
* Active confidence policy.

**Data owned**

* `subscriptions`
* `subscription_transactions`

**Failure handling**

No automatic detector result is authoritative.

Machine results remain `SUSPECTED` until user confirmation.

Previously dismissed suggestions should not continuously reappear without materially new evidence.

**Testing**

* Monthly recurrence.
* Variable amounts.
* Missing months.
* False recurring merchant cases.
* Confirm/dismiss persistence.
* Reanalysis stability.

---

### 3.9 Dashboard Calculator

**Responsibility**

Produce dashboard values exclusively from effective persisted transaction records.

**Outputs**

* Gross spending.
* Net spending.
* Credits.
* Refunds.
* Category totals.
* Monthly totals.
* Merchant totals.
* Subscription totals.
* Recent transactions.
* Last-analysis timestamp.

**Dependencies**

* Transaction repository.
* Subscription repository.

**Data owned**

Optional persisted dashboard cache only.

**Failure handling**

If cached aggregates are unavailable or invalid, recompute from Room.

A cache failure must never alter transaction data.

**Testing**

* Known fixed transaction fixtures.
* Refunds.
* Transfers.
* Duplicates.
* User corrections.
* Empty dataset.
* Cross-month ranges.

---

### 3.10 User Correction Service

**Responsibility**

Persist user decisions separately from parser output so they survive rescans and parser updates.

Supported Phase-0 correction targets include:

* Merchant.
* Category.
* Direction/type.
* Duplicate status.
* Transfer status.
* Subscription status.
* Not-a-transaction decision where supported.

**Inputs**

Transaction ID, field, corrected value, and optional future-rule choice.

**Outputs**

Updated effective transaction.

**Dependencies**

* Correction repository.
* Transaction repository.
* Dashboard calculator.
* Subscription detector where affected.

**Data owned**

`user_corrections`.

**Failure handling**

Correction write and resulting transaction state change must be transactional.

Parser rescans must never overwrite explicit user corrections.

**Testing**

* Per-field correction.
* Re-scan persistence.
* Parser upgrade persistence.
* Apply-to-future rule.
* Correction deletion/reset.
* Aggregate recalculation.

---

### 3.11 Parser Bundle Manager

**Responsibility**

Maintain bundled, downloaded, active, and rollback parser-rule versions.

**Inputs**

* Signed parser manifest.
* Parser-rule package.

**Outputs**

Validated active parser bundle.

**Dependencies**

* HTTPS client.
* CloudFront.
* Signature verifier.
* Local file storage/DataStore.

**Data owned**

* Parser package files.
* `parser_metadata`.

**Failure handling**

Downloaded packages must pass:

1. Signature verification.
2. Checksum verification.
3. Schema compatibility.
4. Minimum-app-version check.

Activation must be atomic.

Previous known-good rules remain available for rollback.

Network failure must not block scanning.

**Testing**

* Valid package.
* Bad checksum.
* Bad signature.
* Unsupported schema.
* Partial download.
* Rollback.
* Offline bundled fallback.

---

### 3.12 Remote Config Provider

**Responsibility**

Provide non-sensitive application configuration and Phase-0 feature flags.

**Dependencies**

* Signed S3/CloudFront configuration or approved AWS configuration mechanism.
* Local DataStore cache.

**Data owned**

Cached non-sensitive configuration.

**Failure handling**

Priority:

```text
last known valid config
    >
bundled defaults
```

Remote configuration must not enable new sensitive permissions or new financial-data transmission.

**Testing**

* Offline start.
* Invalid signature.
* Expired cache.
* Missing keys.
* Version compatibility.

---

### 3.13 Telemetry Client

**Responsibility**

Send only approved coarse operational/product telemetry.

**Allowed examples**

* App version.
* Parser version.
* Android API level.
* Device manufacturer/family.
* Scan started/completed/failed.
* Coarse message-count bucket.
* Scan-duration bucket.
* Error code.

**Prohibited**

* Raw SMS.
* Amount.
* Merchant.
* Institution.
* Account/card identifier.
* Transaction reference.
* Category history.
* Subscription name.
* Transaction history.

**Dependencies**

* API Gateway/Lambda telemetry endpoint.
* Bounded local queue.

**Data owned**

Temporary non-sensitive telemetry queue.

**Failure handling**

Telemetry must never block application functionality.

Retry later and discard old events if the bounded queue is full.

**Testing**

* Schema allow-list tests.
* Network payload inspection.
* Retry/drop behavior.
* Prohibited-field static/regression tests.

---

### 3.14 Support Submission Client

**Responsibility**

Submit a user-approved, redacted unsupported-message example.

**Flow**

```text
Raw SMS
  ↓
On-device redaction
  ↓
User preview
  ↓
Explicit consent
  ↓
Redacted template only
  ↓
AWS
```

**Dependencies**

* Redaction engine.
* API Gateway/Lambda.

**Data owned**

No permanent local submission history required.

**Failure handling**

Failed submission may be retried only while the explicitly approved redacted payload remains valid.

Do not retain the original SMS for retry.

**Testing**

* Amount redaction.
* Account/reference redaction.
* URL redaction.
* Preview equality with network payload.
* Consent required.
* Payload size validation.

---

### 3.15 Privacy and Data Deletion Service

**Responsibility**

Delete user-controlled analysed data safely.

**Inputs**

Deletion scope:

* Analysed transactions and derived records.
* Correction history.
* Cached dashboard.
* Optional parser/config cache reset separately where appropriate.

**Dependencies**

All local repositories.

**Failure handling**

Deletion should execute in a Room transaction for related financial tables.

Failure must be surfaced; never report deletion success before commit.

**Testing**

* Full database deletion.
* Referential integrity.
* Cache invalidation.
* Relaunch after deletion.
* Telemetry contains only `data_deleted`, never deleted values.

---

## 4. Local Room / SQLite Database

### 4.1 Database Principles

* Room over SQLite.
* Encryption layer such as SQLCipher or equivalent where practical.
* Encryption key protected by Android Keystore.
* No raw SMS body column.
* Amounts stored as integer minor units.
* UTC timestamps stored as epoch milliseconds.
* Foreign keys enabled.
* Schema migrations explicitly versioned and tested.
* Financial tables excluded from unsecured device backup.

---

## 5. Schema

### 5.1 `transactions`

| Column                    | SQLite Type | Null | Notes                                              |
| ------------------------- | ----------- | ---: | -------------------------------------------------- |
| `transaction_id`          | TEXT        |   No | UUID/ULID primary key                              |
| `source_message_hash`     | TEXT        |   No | One-way identifier; not raw SMS                    |
| `transaction_fingerprint` | TEXT        |   No | Deterministic deduplication key                    |
| `transaction_timestamp`   | INTEGER     |   No | Epoch milliseconds                                 |
| `amount_minor_units`      | INTEGER     |   No | Paise/minor currency units                         |
| `currency`                | TEXT        |   No | ISO-style currency code, e.g. INR                  |
| `merchant_raw_normalized` | TEXT        |  Yes | Normalized extracted value, not SMS body           |
| `merchant_display_name`   | TEXT        |  Yes | User-visible merchant                              |
| `merchant_key`            | TEXT        |  Yes | Stable normalized lookup key                       |
| `institution`             | TEXT        |  Yes | Parsed institution                                 |
| `masked_account`          | TEXT        |  Yes | Already-masked identifier only                     |
| `reference_hash`          | TEXT        |  Yes | Hash/token; avoid raw reference where not required |
| `direction`               | TEXT        |   No | Enum                                               |
| `payment_method`          | TEXT        |   No | Enum                                               |
| `category_id`             | TEXT        |   No | FK categories                                      |
| `confidence`              | REAL        |   No | 0.0–1.0                                            |
| `parser_version`          | TEXT        |   No | Parser that produced base values                   |
| `duplicate_status`        | TEXT        |   No | `NONE`, `SUSPECTED`, `CONFIRMED`                   |
| `possible_duplicate_of`   | TEXT        |  Yes | FK transactions                                    |
| `transfer_status`         | TEXT        |   No | `NONE`, `SUSPECTED`, `CONFIRMED`                   |
| `is_user_confirmed`       | INTEGER     |   No | Boolean                                            |
| `created_at`              | INTEGER     |   No | Epoch millis                                       |
| `updated_at`              | INTEGER     |   No | Epoch millis                                       |

#### Constraints

```sql
PRIMARY KEY (transaction_id)

UNIQUE (transaction_fingerprint)

CHECK (amount_minor_units >= 0)

CHECK (confidence >= 0.0 AND confidence <= 1.0)

FOREIGN KEY (category_id)
    REFERENCES categories(category_id)

FOREIGN KEY (possible_duplicate_of)
    REFERENCES transactions(transaction_id)
    ON DELETE SET NULL
```

#### Indexes

```sql
INDEX idx_transactions_timestamp
    ON transactions(transaction_timestamp DESC);

INDEX idx_transactions_category_timestamp
    ON transactions(category_id, transaction_timestamp DESC);

INDEX idx_transactions_merchant_timestamp
    ON transactions(merchant_key, transaction_timestamp DESC);

INDEX idx_transactions_direction_timestamp
    ON transactions(direction, transaction_timestamp DESC);

UNIQUE INDEX idx_transactions_fingerprint
    ON transactions(transaction_fingerprint);

INDEX idx_transactions_source_hash
    ON transactions(source_message_hash);
```

---

### 5.2 `categories`

| Column               | Type    | Null | Notes             |
| -------------------- | ------- | ---: | ----------------- |
| `category_id`        | TEXT    |   No | Stable identifier |
| `name`               | TEXT    |   No | Display label     |
| `is_system_category` | INTEGER |   No | Boolean           |
| `sort_order`         | INTEGER |   No | UI order          |
| `created_at`         | INTEGER |   No | Epoch millis      |

#### Constraints

```sql
PRIMARY KEY (category_id)
UNIQUE (name)
```

Seed the approved MVP categories during database creation.

---

### 5.3 `user_corrections`

| Column               | Type    | Null |
| -------------------- | ------- | ---: |
| `correction_id`      | TEXT    |   No |
| `transaction_id`     | TEXT    |   No |
| `field_name`         | TEXT    |   No |
| `old_value`          | TEXT    |  Yes |
| `new_value`          | TEXT    |   No |
| `apply_to_future`    | INTEGER |   No |
| `merchant_match_key` | TEXT    |  Yes |
| `created_at`         | INTEGER |   No |
| `updated_at`         | INTEGER |   No |

#### Constraints

```sql
PRIMARY KEY (correction_id)

FOREIGN KEY (transaction_id)
    REFERENCES transactions(transaction_id)
    ON DELETE CASCADE
```

#### Indexes

```sql
INDEX idx_corrections_transaction
    ON user_corrections(transaction_id);

INDEX idx_corrections_future_rule
    ON user_corrections(merchant_match_key, field_name)
    WHERE apply_to_future = 1;
```

Correction records are authoritative over parser-derived values.

---

### 5.4 `subscriptions`

| Column                   | Type    | Null |
| ------------------------ | ------- | ---: |
| `subscription_id`        | TEXT    |   No |
| `merchant_key`           | TEXT    |   No |
| `merchant_display_name`  | TEXT    |  Yes |
| `frequency`              | TEXT    |   No |
| `estimated_amount_minor` | INTEGER |  Yes |
| `currency`               | TEXT    |   No |
| `last_payment_date`      | INTEGER |  Yes |
| `estimated_next_date`    | INTEGER |  Yes |
| `confidence`             | REAL    |   No |
| `status`                 | TEXT    |   No |
| `created_at`             | INTEGER |   No |
| `updated_at`             | INTEGER |   No |

#### Status

```text
SUSPECTED
CONFIRMED
DISMISSED
POSSIBLY_INACTIVE
```

#### Indexes

```sql
INDEX idx_subscriptions_status
    ON subscriptions(status);

INDEX idx_subscriptions_merchant
    ON subscriptions(merchant_key);
```

---

### 5.5 `subscription_transactions`

| Column            | Type | Null |
| ----------------- | ---- | ---: |
| `subscription_id` | TEXT |   No |
| `transaction_id`  | TEXT |   No |

```sql
PRIMARY KEY (subscription_id, transaction_id)

FOREIGN KEY (subscription_id)
    REFERENCES subscriptions(subscription_id)
    ON DELETE CASCADE

FOREIGN KEY (transaction_id)
    REFERENCES transactions(transaction_id)
    ON DELETE CASCADE
```

---

### 5.6 `scan_state`

| Column                      | Type    | Null |
| --------------------------- | ------- | ---: |
| `scan_id`                   | TEXT    |   No |
| `start_date`                | INTEGER |   No |
| `end_date`                  | INTEGER |   No |
| `last_processed_message_id` | TEXT    |  Yes |
| `parser_version`            | TEXT    |   No |
| `status`                    | TEXT    |   No |
| `processed_count`           | INTEGER |   No |
| `accepted_count`            | INTEGER |   No |
| `started_at`                | INTEGER |   No |
| `completed_at`              | INTEGER |  Yes |
| `updated_at`                | INTEGER |   No |

#### Status

```text
PENDING
RUNNING
COMPLETED
INTERRUPTED
CANCELLED
FAILED
```

#### Index

```sql
INDEX idx_scan_state_status_updated
    ON scan_state(status, updated_at DESC);
```

Only one active scan should be permitted.

Enforce at application level through the scan coordinator/work uniqueness.

---

### 5.7 `parser_metadata`

| Column           | Type    | Null |
| ---------------- | ------- | ---: |
| `parser_version` | TEXT    |   No |
| `rules_version`  | TEXT    |   No |
| `schema_version` | INTEGER |   No |
| `checksum`       | TEXT    |   No |
| `installed_at`   | INTEGER |   No |
| `activated_at`   | INTEGER |  Yes |
| `status`         | TEXT    |   No |

#### Status

```text
INSTALLED
ACTIVE
ROLLBACK
INVALID
```

Application logic permits exactly one `ACTIVE` parser bundle.

---

### 5.8 Optional `dashboard_cache`

Use only if measurement shows aggregate queries prevent the dashboard from meeting the approved startup target.

| Column            | Type             |
| ----------------- | ---------------- |
| `cache_key`       | TEXT PRIMARY KEY |
| `period_start`    | INTEGER          |
| `period_end`      | INTEGER          |
| `payload_json`    | TEXT             |
| `source_revision` | INTEGER          |
| `calculated_at`   | INTEGER          |

This table contains only derived local financial data and therefore has the same protection requirements as `transactions`.

It may be deleted and rebuilt at any time.

---

## 6. Transaction Write Behaviour

### Batch Processing

Each SMS batch should be handled approximately as:

```text
Read batch
  ↓
Filter
  ↓
Parse
  ↓
Normalize
  ↓
Fingerprint
  ↓
Categorise
  ↓
BEGIN ROOM TRANSACTION
    Upsert accepted transactions
    Update scan progress
COMMIT
```

Subscription detection and dashboard recalculation occur after successful financial-record commits.

### Upsert Rule

`transaction_fingerprint` is the logical idempotency key.

On rescan:

```text
existing fingerprint
    → update parser-derived values where safe
    → preserve user corrections

new fingerprint
    → insert
```

Explicit user corrections must never be overwritten by parser output.

---

## 7. Database Transaction Boundaries

Use Room transactions for:

### Scan batch commit

* Transaction upserts.
* Duplicate relations.
* Scan-state progress update.

### User correction

* Insert/update correction.
* Update effective local state if materialised.
* Invalidate dashboard cache.

### Subscription user action

* Change subscription status.
* Update supporting relation state if required.

### Data deletion

Delete related financial records atomically:

```text
subscription_transactions
subscriptions
user_corrections
transactions
scan_state
dashboard_cache
```

Parser/config files can be retained because they contain no user financial history unless the user selects a full application reset.

---

## 8. Cache Design

### 8.1 In-Memory

Use bounded caches only.

Suitable data:

* Active parser rules.
* Merchant mappings.
* Category lookup map.
* Current dashboard model.
* Current transaction filters.

No raw SMS cache should outlive the processing batch.

### 8.2 Persistent Device Cache

Use DataStore or application-private files for:

* Signed remote configuration.
* Feature flags.
* Active parser manifest.
* Parser packages.
* Last selected analysis period.
* Non-sensitive application preferences.

Use Room for financial and relational state.

### 8.3 Cloud Cache

Use CloudFront for:

* Parser manifest.
* Versioned parser bundles.
* Signed configuration/static artifacts where selected.

Do not introduce:

* Redis.
* ElastiCache.
* Application-level distributed cache.

They provide no MVP benefit.

---

## 9. AWS Components Required by the Approved MVP

The backend remains a thin control plane.

```text
Android
   |
   +---- CloudFront / S3
   |       ├── parser manifest
   |       ├── parser bundles
   |       ├── signed config
   |       └── static/legal content
   |
   +---- API Gateway HTTP API
           |
           +---- Lambda
                   ├── telemetry ingestion
                   └── support submission
                           |
                           v
                       DynamoDB
```

No backend service owns user transaction history.

---

## 10. DynamoDB Usage

### 10.1 Telemetry Aggregate

Prefer aggregate/coarse operational counters rather than user-level financial event histories.

Example logical fields:

```text
date_bucket
event_type
app_version
device_family
count
```

No financial attributes are allowed.

### 10.2 Support Submission

Only present if the approved Phase-0 support flow is enabled.

```text
submission_id
redacted_template
app_version
parser_version
consent_timestamp
status
ttl
```

Requirements:

* Redacted before upload.
* User previews exact submitted content.
* Explicit submission consent.
* DynamoDB TTL enabled.
* Short retention.
* Restricted operational access.

---

## 11. Data Ownership Boundary

| Data                          |                     Device |                        AWS |
| ----------------------------- | -------------------------: | -------------------------: |
| Raw SMS body                  |             Yes, ephemeral |                  **Never** |
| SMS sender                    | Ephemeral/local processing |                         No |
| Normalised transactions       |                    **Yes** |                  **Never** |
| Amounts                       |                    **Yes** |                  **Never** |
| Merchant names/history        |                    **Yes** |                  **Never** |
| Institution per transaction   |                    **Yes** |                  **Never** |
| Masked account identifiers    |                    **Yes** |                  **Never** |
| Transaction references        |  Local/hash only as needed |                  **Never** |
| Categories/history            |                    **Yes** |                  **Never** |
| Subscriptions                 |                    **Yes** |                  **Never** |
| User corrections              |                    **Yes** |                  **Never** |
| Dashboard aggregates          |                    **Yes** |                  **Never** |
| Parser rules                  |                     Cached |                        Yes |
| Merchant/category rule bundle |                     Cached |                        Yes |
| Feature configuration         |                     Cached |                        Yes |
| App/parser version telemetry  |                   Optional |                        Yes |
| Coarse count/duration buckets |                   Optional |                        Yes |
| Redacted support template     |          Previewed locally | Yes, explicit consent only |

---

## 12. Failure and Resilience Rules

### Permission revoked

* Stop scan.
* Persist completed batch only.
* Mark scan `INTERRUPTED`.
* Resume after permission restoration.

### App process killed

* WorkManager restarts eligible work.
* Continue from `last_processed_message_id`.
* Fingerprinting prevents duplicate rows.

### Invalid parser update

* Reject package.
* Continue with previous known-good version.
* Never block local functionality.

### AWS unavailable

* Use cached/bundled configuration.
* Use current parser bundle.
* Continue core product offline.

### Telemetry unavailable

* Queue only bounded non-sensitive events.
* Retry later.
* Drop oldest events when capacity is exceeded.

### Local database error

* Stop writes.
* Preserve existing data where possible.
* Do not silently recreate/delete the financial database.
* Offer explicit recovery/reset where required.

### Low storage

* Stop before unsafe writes.
* Preserve committed data.
* Surface actionable user state.

---

## 13. Testing Strategy

### Pure domain unit tests

Highest coverage should exist around:

* Message filtering.
* Parser rules.
* Merchant normalization.
* Fingerprinting.
* Duplicate detection.
* Categorisation.
* Subscription detection.
* Dashboard math.
* Correction precedence.

### Repository tests

Use in-memory/test Room databases for:

* Constraints.
* Foreign keys.
* Queries.
* Upserts.
* Transactions.
* Deletion.
* Migrations.

### Parser regression suite

Every bundled or remote parser change must run against:

* Synthetic supported messages.
* Redacted voluntarily submitted samples.
* Negative/non-financial messages.
* Duplicate scenarios.
* Transfers.
* Refunds.
* Reversals.
* Subscription patterns.

### Resilience tests

Test:

* Process death mid-scan.
* Permission loss mid-scan.
* Offline startup.
* Parser download interruption.
* Invalid rule packages.
* Low storage.
* Database migration failure.

### Privacy tests

Automated tests must assert that network/log payloads never contain:

```text
SMS body
amount
merchant
institution tied to transaction
account/card identifier
transaction reference
category history
subscription name
financial transaction history
```

---

## 14. Schema Migration Strategy

Start Room at database version `1`.

Every production schema modification must:

1. Increment the Room database version.
2. Provide an explicit migration.
3. Have upgrade tests using exported historical schemas.
4. Preserve transaction and correction data.
5. Avoid destructive migration in production.
6. Invalidate derived dashboard caches when their inputs/schema change.

Parser-rule updates are independent of Room schema versions.

A parser update must not require a database migration unless the Android application itself introduces a new persisted model.

---

## 15. Implementation Package Boundaries

Recommended Android package structure:

```text
app/
presentation/
    onboarding/
    scan/
    dashboard/
    transactions/
    subscriptions/
    settings/

domain/
    model/
    filtering/
    parsing/
    merchant/
    deduplication/
    categorisation/
    subscriptions/
    dashboard/
    corrections/

data/
    sms/
    room/
        entity/
        dao/
        migration/
        repository/
    parser/
    config/
    telemetry/
    support/

application/
    analysis/
    corrections/
    deletion/
    parserupdate/

platform/
    permissions/
    security/
    work/
    network/
```

Dependencies should flow toward the domain layer.

Domain logic must not depend directly on Android ContentProvider, Room entities, Retrofit DTOs, or AWS concepts.

---

## 16. Implementation Decisions Still Requiring Lock

The approved documents intentionally leave several items unresolved.

These must be locked before or during implementation without changing the architecture:

1. Exact Google Play-approved SMS permission path and controlled-import fallback.
2. Minimum Android API level.
3. SQLCipher or equivalent database-encryption implementation.
4. Local backup/exclusion policy.
5. Exact transaction confidence thresholds.
6. Supported institutions and languages at launch.
7. Parser bundle schema and signing-key custody.
8. Telemetry opt-in/default and retention.
9. Support-submission retention period.
10. Whether the optional redacted unsupported-SMS submission ships in the first public MVP.

None of these require introducing a new service or changing the local-first architecture.

---

## 17. Explicit Phase-0 Non-Additions

Do not implement as part of this step:

* PostgreSQL.
* RDS.
* User-account database.
* Cognito.
* Cloud transaction storage.
* Transaction upload API.
* SMS upload API.
* Financial-history API.
* Redis/ElastiCache.
* Kubernetes.
* Long-running backend servers.
* Bank integration.
* Email/receipt ingestion.
* Cross-device sync.
* Cloud backup of financial records.
* Server-side AI over transaction data.

---

## 18. Implementation Readiness

Step-3 is ready to hand to implementation when:

* Room entities and migrations are defined from this schema.
* DAO queries are specified for transaction lists and dashboard aggregates.
* Repository interfaces separate Room from domain logic.
* Parser regression fixtures exist.
* Transaction fingerprinting is deterministic.
* Repeated scanning is idempotent.
* Correction precedence is enforced.
* Raw SMS cannot enter Room or network DTOs.
* Parser update verification and rollback are implemented as an isolated module.
* Telemetry DTOs use an explicit allow-list.
* Support DTOs can only be constructed from user-previewed redacted content.
* Financial deletion is covered by integration tests.
* Core scan, correction, subscription, dashboard, and deletion flows operate offline.

---

## Final Design Decision

For Phase-0, SpendSMS should be implemented as an **offline-capable Android modular application whose encrypted Room database is the sole financial system of record**, supported by a **small serverless AWS control plane** for parser/config distribution, privacy-safe telemetry, and optional redacted support submissions.

No additional backend business services or cloud financial database are required for the MVP.

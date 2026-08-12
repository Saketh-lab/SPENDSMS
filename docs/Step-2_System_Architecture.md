# SpendSMS MVP System Architecture

## 1. Explanation

### 1.1 Architecture principles

The MVP should use a **local-first Android architecture**.

The PRD recommends no mandatory account, on-device SMS processing, local encrypted storage, and discarding raw SMS after extracting normalized transaction records.

This leads to four key decisions:

1. Raw SMS messages never leave the device.
2. Transaction parsing, categorisation, duplicate detection, subscription detection, corrections, and dashboard calculations run locally.
3. AWS is used only for lightweight supporting services such as configuration, parser-rule updates, feature flags, application health, and optional support submissions.
4. The system should work offline after installation and initial configuration download.

This architecture reduces:

- Privacy and security risk.
- Google Play compliance risk.
- AWS infrastructure cost.
- Network dependency.
- Backend scaling complexity.

The MVP does not need a traditional financial transaction backend because cross-device synchronization and user accounts are outside the current scope.

---

## 2. MVP Components

### 2.1 Android frontend

Recommended implementation:

- Kotlin.
- Jetpack Compose.
- MVVM or Clean Architecture.
- Coroutines and Flow.
- WorkManager for background and resumable processing.
- Hilt for dependency injection.
- Room for local persistence.

#### Main UI modules

##### Onboarding module

Responsibilities:

- Explain product purpose.
- Display sample dashboard.
- Show privacy summary.
- Display prominent SMS-access disclosure.
- Trigger Android permission request only after explicit user action.
- Handle permission denial.

##### Scan module

Responsibilities:

- Let the user select a time range.
- Read eligible SMS records.
- Process records in batches.
- Display scan progress.
- Support cancellation and safe restart.
- Store the last successful scan position.

##### Dashboard module

Responsibilities:

- Total spending.
- Total credits and refunds.
- Category breakdown.
- Monthly trend.
- Top merchants.
- Recent transactions.
- Suspected subscriptions.
- Last analysis timestamp.

##### Transactions module

Responsibilities:

- Search.
- Filter.
- Sort.
- View transaction details.
- Correct merchant, category, direction, duplicate status, transfer status, or subscription status.

##### Subscription module

Responsibilities:

- Show suspected subscriptions.
- Show evidence transactions.
- Confirm or dismiss suggestions.
- Calculate estimated monthly and annual recurring costs.

##### Settings and privacy module

Responsibilities:

- Permission status.
- Biometric or device-lock protection.
- Delete analysed data.
- Clear correction history.
- Privacy policy.
- Terms.
- Support.
- Diagnostics opt-in.

---

### 2.2 On-device domain services

These are the core business components of the MVP.

#### SMS ingestion service

Reads SMS metadata and content only for the user-selected date range.

Responsibilities:

- Query Android SMS provider.
- Read in pages or batches.
- Avoid loading the entire inbox into memory.
- Stop if permission is revoked.
- Avoid modifying, deleting, sending, or marking messages as read.

#### Message pre-filter

Removes obvious non-transactional messages before deeper processing.

Filters include:

- OTP messages.
- Personal conversations.
- Marketing messages.
- Delivery notifications.
- Non-financial bank messages.
- Failed or declined transactions where appropriate.

#### Transaction parser

Extracts:

- Amount.
- Currency.
- Transaction timestamp.
- Merchant or recipient.
- Debit, credit, refund, or transfer direction.
- Payment method.
- Bank or institution.
- Masked account identifier.
- Reference token.
- Confidence score.

The parser should use versioned rules:

- Sender patterns.
- Regular expressions.
- Institution-specific templates.
- Merchant normalization rules.
- Transaction keywords.

#### Deduplication service

Creates a transaction fingerprint using fields such as:

- Institution.
- Amount.
- Timestamp window.
- Masked account.
- Reference hash.
- Merchant.
- Transaction direction.

The fingerprint must be deterministic so rescanning does not create duplicate records.

#### Categorisation engine

Uses local rules to map normalized merchants and descriptions into categories.

Order of precedence:

1. User-defined correction rule.
2. Exact merchant rule.
3. Institution or transaction-pattern rule.
4. Keyword classification.
5. Default `Other` category.

#### Subscription detector

Evaluates recurring transactions using:

- Normalized merchant.
- Similar amounts.
- Repeated payment intervals.
- Mandate or recurring-payment wording.
- Minimum number of occurrences.
- Confidence threshold.

All machine-detected results remain marked as **suspected** until the user confirms them.

#### Calculation engine

Produces dashboard aggregates from the local transaction database:

- Gross expenses.
- Net expenses.
- Credits.
- Refunds.
- Category totals.
- Monthly totals.
- Merchant totals.
- Subscription totals.

The dashboard should always be calculated from normalized database records, not directly from SMS messages.

---

### 2.3 Local database

#### Recommended database

Use:

- Room over SQLite.
- Android Keystore for encryption-key protection.
- SQLCipher or equivalent encrypted database support where practical.

#### Core tables

##### `transactions`

```text
transaction_id
source_message_hash
transaction_fingerprint
timestamp
amount_minor_units
currency
merchant_raw_normalized
merchant_display_name
institution
masked_account
direction
payment_method
category_id
confidence
parser_version
is_duplicate
is_user_confirmed
created_at
updated_at
```

Amounts should be stored in minor units, such as paise, using integers rather than floating-point values.

##### `categories`

```text
category_id
name
is_system_category
created_at
```

##### `user_corrections`

```text
correction_id
transaction_id
field_name
old_value
new_value
apply_to_future
merchant_match_key
created_at
```

##### `subscriptions`

```text
subscription_id
merchant_key
frequency
estimated_amount
last_payment_date
estimated_next_date
confidence
status
created_at
updated_at
```

##### `subscription_transactions`

Links subscriptions to supporting transactions.

##### `scan_state`

```text
scan_id
start_date
end_date
last_processed_message_id
parser_version
status
processed_count
accepted_count
started_at
completed_at
```

##### `parser_metadata`

```text
parser_version
rules_version
installed_at
checksum
```

---

### 2.4 On-device cache

A separate cache server is unnecessary for the MVP.

Use two levels of local cache:

#### In-memory cache

Used for:

- Current dashboard aggregates.
- Category lists.
- Merchant mappings.
- Active filters.
- Parser rules loaded during scanning.

A bounded LRU cache is sufficient.

#### Persistent local cache

Room tables or DataStore can cache:

- Last dashboard result.
- Last selected time period.
- Last scan status.
- Feature flags.
- Parser-rule package.
- Remote configuration.

The cached dashboard allows the app to open quickly without recalculating all aggregates immediately.

---

## 3. Backend Architecture

The MVP backend should be intentionally thin.

### 3.1 API layer

Recommended AWS services:

- Amazon API Gateway HTTP API.
- AWS Lambda.
- Amazon DynamoDB.
- Amazon S3.
- Amazon CloudFront.
- AWS Systems Manager Parameter Store or AppConfig.
- Amazon CloudWatch.

The Android app should never upload normalized financial transactions in the MVP.

#### Backend API responsibilities

Possible endpoints:

```text
GET  /v1/config
GET  /v1/parser-manifest
GET  /v1/parser-rules/{version}
POST /v1/telemetry
POST /v1/support/parser-failure
GET  /v1/legal-links
```

The API should not expose:

```text
POST /transactions
POST /sms
POST /financial-history
```

Those endpoints are not needed for the MVP.

---

### 3.2 Parser-rule distribution

Institution SMS formats can change frequently. Releasing a new app version for every parser adjustment would be slow.

Recommended design:

1. Store versioned parser-rule packages in S3.
2. Publish a signed manifest.
3. Deliver files through CloudFront.
4. Android downloads the package.
5. Android verifies:
   - Signature.
   - Checksum.
   - Minimum application version.
   - Rule schema version.
6. Android activates the new package.
7. Previous working package remains available for rollback.

Rules should contain data only, not executable code.

Example:

```json
{
  "institution": "Example Bank",
  "senderPatterns": ["EX-BANK"],
  "transactionPatterns": [
    {
      "type": "DEBIT",
      "regex": "...",
      "amountGroup": "amount",
      "merchantGroup": "merchant"
    }
  ]
}
```

This creates an extension point for Phase-1 without creating remote-code-execution risk.

---

### 3.3 DynamoDB

For the MVP, DynamoDB stores only non-financial operational data.

Possible tables:

#### `AppConfigMetadata`

```text
config_version
minimum_app_version
parser_version
rollout_percentage
created_at
```

#### `TelemetryAggregate`

Prefer coarse counters instead of individual user-event histories.

```text
date_bucket
app_version
device_family
event_type
count
```

#### `SupportSubmission`

Only when the user explicitly submits a redacted unsupported format.

```text
submission_id
redacted_template
app_version
parser_version
consent_timestamp
status
ttl
```

Use DynamoDB TTL to delete temporary support submissions automatically.

---

### 3.4 Backend cache

Do not introduce ElastiCache in the MVP.

At MVP scale:

- API Gateway provides managed request handling.
- Lambda can cache configuration briefly in warm execution environments.
- CloudFront caches parser manifests and rule files.
- Android caches configuration locally.

Adding Redis would create unnecessary fixed cost and operational complexity.

ElastiCache becomes relevant only in later phases if there is:

- High-volume authenticated API traffic.
- Shared sessions.
- Frequently accessed computed server data.
- Rate-limit state requiring low latency.

---

## 4. Foundational Services

### 4.1 Security

#### On the Android device

- Android Keystore for encryption keys.
- Encrypted Room database where feasible.
- No sensitive values in logs.
- Disable screenshots on sensitive screens if appropriate.
- Optional biometric lock.
- Certificate pinning only if the team can maintain safe certificate rotation.
- Release signing through Google Play App Signing.
- Rooted-device warning rather than automatic blocking.

#### AWS

- Separate development, staging, and production environments.
- Least-privilege IAM roles.
- AWS KMS encryption for S3 and DynamoDB.
- CloudTrail enabled.
- S3 Block Public Access.
- AWS WAF added only when traffic or abuse justifies its cost.
- Secrets stored in Parameter Store or Secrets Manager.
- No long-lived AWS credentials in the Android application.

---

### 4.2 Observability

Use privacy-safe telemetry only.

Allowed events:

```text
app_opened
onboarding_completed
permission_granted
permission_denied
scan_started
scan_completed
scan_failed
dashboard_opened
parser_rule_download_failed
database_migration_failed
data_deleted
```

Allowed metadata:

```text
app_version
parser_version
Android API level
device manufacturer
coarse message count bucket
scan duration bucket
error code
```

Do not send:

- SMS text.
- Merchant names.
- Amounts.
- Account identifiers.
- Transaction references.
- Categories.
- Subscription names.
- Screenshots containing financial data.

#### AWS observability components

- CloudWatch Logs.
- CloudWatch Metrics.
- CloudWatch Alarms.
- CloudWatch dashboard.
- Lambda structured logs.
- Dead-letter queue for failed asynchronous events.

---

### 4.3 Feature flags

Use AWS AppConfig or a small signed JSON document in S3.

Example flags:

```text
controlled_import_enabled
subscription_detection_enabled
new_parser_version_enabled
premium_experiment_enabled
support_submission_enabled
minimum_supported_app_version
```

Feature flags must not silently enable new data collection or permissions.

---

### 4.4 CI/CD

Recommended pipeline:

```text
GitHub
   |
   +--> GitHub Actions
          |
          +--> Android unit tests
          +--> Parser regression tests
          +--> Static analysis
          +--> Dependency scan
          +--> Build signed bundle
          +--> Deploy backend through AWS CDK/Terraform
```

Infrastructure should be defined with:

- AWS CDK, or
- Terraform.

Avoid manually creating production resources through the AWS console.

---

### 4.5 Parser test corpus

Create a version-controlled corpus containing:

- Synthetic SMS examples.
- Redacted voluntarily submitted samples.
- Expected extraction output.
- Expected confidence.
- Expected category.
- Duplicate test cases.
- Refund and reversal cases.

Every parser-rule update must pass this test suite before publication.

---

## 5. Architecture Data Flow

### 5.1 First application launch

1. User opens the Android app.
2. App loads bundled default configuration.
3. App asynchronously requests the latest signed configuration from AWS.
4. CloudFront returns cached configuration or parser manifest.
5. App verifies the signature and stores the configuration locally.
6. User can continue even if AWS is unavailable.

---

### 5.2 Transaction scan

1. User selects an analysis period.
2. User approves the prominent disclosure.
3. Android requests the approved SMS permission.
4. SMS ingestion service queries messages in batches.
5. Message pre-filter rejects irrelevant messages.
6. Parser converts eligible messages into normalized transaction candidates.
7. Deduplication service calculates transaction fingerprints.
8. Categorisation service assigns categories.
9. Records are stored in the local encrypted Room database.
10. Subscription detector evaluates recurring patterns.
11. Calculation engine generates dashboard aggregates.
12. Dashboard cache is updated.
13. UI displays the results.
14. A privacy-safe scan-completion event may be sent to AWS.

Raw SMS is not uploaded to AWS.

---

### 5.3 User correction

1. User opens a transaction.
2. User changes its merchant, type, category, or status.
3. Correction is stored separately from the parser output.
4. Effective transaction view combines parsed values with user overrides.
5. Aggregates are recalculated.
6. Dashboard updates immediately.
7. The user correction takes precedence after future rescans.

---

### 5.4 Parser update

1. App periodically checks the parser manifest.
2. Manifest indicates a newer rule package.
3. App downloads it from CloudFront.
4. Signature and checksum are verified.
5. Rules are validated against the supported schema.
6. New rules are stored alongside the previous rules.
7. The app activates the update.
8. If parsing fails abnormally, the app rolls back to the previous version.

---

### 5.5 Support submission

1. User identifies an unsupported SMS format.
2. App performs on-device redaction.
3. User previews the redacted content.
4. User explicitly approves submission.
5. Redacted template is sent through API Gateway.
6. Lambda validates and stores it temporarily.
7. DynamoDB TTL removes it after the defined retention period.

---

## 6. Scaling Plan: 10K to 1M Users

Because most computation and data storage are local, scaling is primarily based on configuration downloads and coarse telemetry rather than financial transaction volume.

### Stage A: Up to 10,000 users

#### Architecture

- API Gateway HTTP API.
- Lambda.
- DynamoDB on-demand capacity.
- S3.
- CloudFront.
- CloudWatch.
- No Redis.
- No containers.
- No relational database.
- No user account service.

#### Expected characteristics

- Low backend request volume.
- Most parser files served from CloudFront cache.
- Telemetry batched by the application.
- Minimal operational overhead.

#### Optimizations

- Send telemetry in batches.
- Cache configuration for 6–24 hours.
- Compress parser-rule packages.
- Avoid polling on every app open.
- Use DynamoDB TTL.

---

### Stage B: 10,000 to 100,000 users

Maintain the same serverless architecture.

Add:

- AWS WAF if abusive traffic appears.
- SQS between API ingestion and telemetry processing.
- Lambda reserved concurrency.
- CloudWatch cost alarms.
- Better CloudFront cache policies.
- Deployment canaries for parser rules.
- Separate telemetry aggregates from support submissions.

Flow:

```text
API Gateway
     |
     v
Ingestion Lambda
     |
     v
    SQS
     |
     v
Processing Lambda
     |
     v
DynamoDB aggregates
```

SQS prevents traffic spikes from overwhelming downstream services.

---

### Stage C: 100,000 to 500,000 users

Continue using serverless components unless profiling shows a specific limitation.

Enhancements:

- CloudFront origin access control.
- Multiple parser-rule deployment channels:
  - Stable.
  - Beta.
  - Internal.
- Regional disaster-recovery copies for S3 assets.
- DynamoDB partition-key review.
- Sample low-value telemetry rather than collecting every event.
- Aggregate events before storage.
- Automated anomaly detection.
- S3 lifecycle rules for logs and old rule packages.

At this stage, the principal cost driver is likely observability rather than application APIs.

---

### Stage D: 500,000 to 1 million users

The local-first model remains unchanged.

Backend improvements:

- Multi-region S3 and CloudFront distribution if required.
- API Gateway throttling by application installation token.
- SQS buffering for all non-interactive writes.
- DynamoDB global tables only if multi-region writes are genuinely needed.
- Kinesis Firehose or S3-based analytics if telemetry volume becomes high.
- Athena for querying aggregated telemetry.
- Separate operational and product analytics pipelines.
- Aggressive event sampling.
- Bot and abuse protection.
- Automated key and signing-certificate rotation procedures.

Do not migrate to Kubernetes merely because the user count reaches one million. The serverless architecture can remain appropriate because each user generates relatively little backend traffic.

---

## 7. Failure Scenarios

### 7.1 SMS permission denied

#### Impact

Automatic scanning cannot run.

#### Handling

- Do not continuously reprompt.
- Show sample dashboard.
- Explain how to enable access later.
- Offer controlled import if available.
- Keep settings and privacy screens available.

---

### 7.2 Permission revoked during scan

#### Impact

The scan stops partway through.

#### Handling

- Catch the security exception.
- Mark scan as interrupted.
- Commit only completed batches.
- Preserve existing transactions.
- Allow resume after permission is restored.

---

### 7.3 App killed during scan

#### Handling

- Process messages in batches.
- Persist progress after each batch.
- Use WorkManager where appropriate.
- Resume using `last_processed_message_id`.
- Ensure transaction fingerprinting makes the operation idempotent.

---

### 7.4 Parser misclassifies a personal message

#### Impact

Sensitive content could appear in the transaction interface.

#### Handling

- Strong pre-filtering.
- Never store full source text after extraction.
- Display only extracted normalized fields.
- Provide “Not a transaction” action.
- Treat this as a high-priority parser defect.

---

### 7.5 Duplicate transaction

#### Handling

- Deterministic fingerprint.
- Multi-field duplicate comparison.
- Do not include suspected duplicates twice.
- Allow user to override the duplicate decision.

---

### 7.6 Parser-rule update is invalid

#### Handling

- Verify digital signature.
- Verify checksum.
- Validate schema.
- Retain previous version.
- Activate updates atomically.
- Roll back after abnormal parser failures.

---

### 7.7 AWS configuration unavailable

#### Handling

- Use bundled configuration.
- Use last known valid parser rules.
- Do not block local scanning.
- Retry with exponential backoff.
- Avoid showing a fatal error.

---

### 7.8 Telemetry API unavailable

#### Handling

- Never block the user experience.
- Keep a small bounded queue.
- Retry later.
- Drop old telemetry if the queue limit is reached.
- Never retain financial data in the queue.

---

### 7.9 Local database corruption

#### Handling

- Detect Room migration or integrity failure.
- Keep schema migrations tested.
- Provide a safe local reset.
- Rebuild transactions from SMS only after user confirmation.
- Never silently delete data.

---

### 7.10 Low storage

#### Handling

- Check storage before a large scan.
- Process in bounded batches.
- Avoid storing raw SMS.
- Warn the user.
- Stop gracefully before database corruption.

---

### 7.11 Spoofed financial SMS

#### Handling

- Use sender confidence.
- Label unknown sources.
- Do not treat SMS as authoritative.
- Do not use results for lending, tax, or regulated decisions.
- Clearly state that the dashboard is informational.

---

### 7.12 Support submission contains sensitive data

#### Handling

- Redact account numbers, URLs, reference values, and amounts on-device.
- Show a preview.
- Require explicit consent.
- Apply short retention.
- Allow deletion requests.
- Restrict employee access.

---

## 8. Cost-Aware AWS Decisions

### Use serverless services

Prefer:

- Lambda instead of EC2.
- API Gateway HTTP API instead of REST API where sufficient.
- DynamoDB on-demand instead of provisioned relational databases.
- S3 and CloudFront for static assets.
- SQS for asynchronous workloads.
- CloudWatch with limited retention.

This avoids paying for continuously running servers.

---

### Avoid Cognito in the MVP

The MVP should not require user accounts.

Therefore:

- No Cognito user pool.
- No login service.
- No refresh-token management.
- No account database.
- No cloud transaction synchronization.
- No account-deletion backend.

An anonymous random installation identifier can be used for rate limiting, but it should be resettable and should not be used for cross-app tracking.

---

### Avoid RDS

A relational database is unnecessary because:

- Transactions remain on-device.
- No cross-user joins are needed.
- Backend data is configuration and operational metadata.
- DynamoDB is sufficient for low-volume key-value workloads.

RDS would introduce fixed compute, storage, backup, and maintenance costs.

---

### Avoid ElastiCache

CloudFront and local application caching cover MVP requirements.

Redis would add:

- Fixed monthly cost.
- Networking complexity.
- Availability management.
- Additional security surface.

---

### Reduce CloudWatch cost

CloudWatch can become unexpectedly expensive.

Use:

- Structured but minimal logs.
- No request or response payload logging.
- Short log retention in development.
- Moderate retention in production.
- Metric filters only for important errors.
- Sampling for high-frequency events.
- S3 lifecycle archiving where required.

---

### Reduce API requests

The app should:

- Fetch configuration at most once every 6–24 hours.
- Use ETag or `If-None-Match`.
- Batch telemetry.
- Use exponential backoff.
- Stop retries when offline.
- Download parser rules only when the version changes.

---

### Use S3 and CloudFront for parser packages

Do not serve static rule files from Lambda.

CloudFront provides:

- Edge caching.
- Lower origin load.
- Better download latency.
- Straightforward versioning.
- Efficient scaling to large user counts.

---

### Suggested MVP AWS resource set

```text
1 API Gateway HTTP API
3–5 Lambda functions
1–2 DynamoDB tables
1 S3 bucket for parser/config artifacts
1 CloudFront distribution
1 SQS queue
1 dead-letter queue
CloudWatch logs, metrics and alarms
Parameter Store or AppConfig
KMS encryption keys
```

This is sufficient for the MVP and can remain viable well beyond 100,000 users.

---

## 9. Architecture Diagram — Text

```text
┌─────────────────────────────────────────────────────────────────────┐
│                         ANDROID APPLICATION                         │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    Presentation Layer                         │  │
│  │                                                               │  │
│  │  Onboarding   Scan Progress   Dashboard   Transactions        │  │
│  │  Subscriptions   Settings   Privacy   Support                 │  │
│  └───────────────────────────────┬───────────────────────────────┘  │
│                                  │                                  │
│  ┌───────────────────────────────▼───────────────────────────────┐  │
│  │                    Application / Domain Layer                 │  │
│  │                                                               │  │
│  │  Scan Orchestrator                                            │  │
│  │       │                                                       │  │
│  │       ├── SMS Ingestion Service                               │  │
│  │       ├── Message Pre-filter                                  │  │
│  │       ├── Transaction Parser                                  │  │
│  │       ├── Deduplication Engine                                │  │
│  │       ├── Categorisation Engine                               │  │
│  │       ├── Subscription Detector                               │  │
│  │       └── Dashboard Calculation Engine                        │  │
│  │                                                               │  │
│  │  Correction Service      Deletion Service                     │  │
│  │  Rule Update Service     Privacy-safe Telemetry Client        │  │
│  └───────────────────────────────┬───────────────────────────────┘  │
│                                  │                                  │
│  ┌───────────────────────────────▼───────────────────────────────┐  │
│  │                       Local Data Layer                        │  │
│  │                                                               │  │
│  │  Encrypted Room / SQLite                                     │  │
│  │  ├── Transactions                                            │  │
│  │  ├── Categories                                              │  │
│  │  ├── User Corrections                                        │  │
│  │  ├── Suspected Subscriptions                                 │  │
│  │  ├── Scan State                                              │  │
│  │  └── Parser Metadata                                         │  │
│  │                                                               │  │
│  │  DataStore / Local Cache                                     │  │
│  │  Android Keystore                                            │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  RAW SMS REMAINS ON DEVICE                                         │
│  NO FINANCIAL TRANSACTIONS ARE SENT TO AWS                         │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                │ HTTPS
                                │ configuration, rule packages,
                                │ coarse telemetry, optional
                                │ user-approved redacted support data
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                           AWS CONTROL PLANE                         │
│                                                                     │
│  ┌──────────────────────┐       ┌────────────────────────────────┐  │
│  │ CloudFront           │◄──────│ S3                             │  │
│  │                      │       │                                │  │
│  │ Parser manifests     │       │ Signed parser-rule packages    │  │
│  │ Config documents     │       │ Legal/static assets            │  │
│  └──────────────────────┘       └────────────────────────────────┘  │
│                                                                     │
│  ┌──────────────────────┐       ┌────────────────────────────────┐  │
│  │ API Gateway HTTP API │──────►│ Lambda                         │  │
│  │                      │       │                                │  │
│  │ /config              │       │ Config handler                 │  │
│  │ /telemetry           │       │ Telemetry ingestion            │  │
│  │ /support             │       │ Support validation             │  │
│  └──────────────────────┘       └───────────────┬────────────────┘  │
│                                                 │                   │
│                               ┌─────────────────┼────────────────┐  │
│                               │                 │                │  │
│                               ▼                 ▼                ▼  │
│                         ┌───────────┐       ┌──────────┐   ┌────────┐│
│                         │ SQS       │       │ DynamoDB │   │AppConfig││
│                         │           │       │          │   │or SSM   ││
│                         │ Telemetry │       │ Metadata │   │Params   ││
│                         │ buffering │       │ counters │   │         ││
│                         └─────┬─────┘       └──────────┘   └────────┘│
│                               │                                     │
│                               ▼                                     │
│                         ┌───────────┐                                │
│                         │ Processing│                                │
│                         │ Lambda    │                                │
│                         └─────┬─────┘                                │
│                               │                                     │
│                               ▼                                     │
│                         ┌───────────┐                                │
│                         │CloudWatch │                                │
│                         │Metrics,   │                                │
│                         │Logs,Alarm │                                │
│                         └───────────┘                                │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 10. Future Extension Points

```text
Phase-1
   ├── Premium entitlements
   ├── Budgets and alerts
   ├── Export
   ├── Optional encrypted backup
   └── Custom categorisation rules

Phase-2
   ├── Account Aggregator integration
   ├── Email and receipt ingestion
   ├── Multi-device encrypted sync
   ├── Household sharing
   └── iOS companion application
```

The MVP should not build these services now. It should only preserve interfaces around ingestion, storage, entitlement, and synchronization so they can be added without rewriting the local parsing and dashboard domain.

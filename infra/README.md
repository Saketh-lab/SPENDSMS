# SpendSMS Phase-0 AWS infrastructure (IaC only)

This directory is the **thin serverless control/observability plane** from Steps 2–4.

## Framework choice

**AWS CDK v2 (TypeScript)** with Python 3.12 Lambda foundations.

- SAM would be a smaller YAML file for API+Lambda, but CloudFront Origin Access Control, a customer-managed KMS key, HTTP API access logs, and consistent tagging are first-class in CDK.
- Terraform was not chosen because the rest of this slice maps cleanly onto CloudFormation via `cdk synth`, which we can inspect without credentials.
- A dedicated CDK bootstrap qualifier `spendsms` is set so a later deploy does **not** reuse another application’s default `hnb659fds` CDK toolkit bucket.

## Isolation

All resources are SpendSMS-named and tagged:

- `Project=SpendSMS`
- `Phase=Phase0`
- `ManagedBy=IaC`

This stack does not import, rename, or depend on other applications’ buckets, tables, APIs, or roles.

## Do not deploy from Prompt 15

Allowed:

```bash
cd infra
npm ci
npm run validate
```

Forbidden here: `cdk deploy`, `cdk bootstrap`, `aws cloudformation deploy`, `aws s3 cp` to live buckets, or any create/update/delete of cloud resources.

## Prompt 14 URL mapping (after a later deploy)

| Android `BuildConfig` | Stack output |
| --- | --- |
| `PARSER_CDN_BASE_URL` | `https://<SpendSmsCdnDomain>/` |
| `PARSER_MANIFEST_URL` | `https://<SpendSmsCdnDomain>/parser/manifest.json` |
| `API_BASE_URL` | `SpendSmsApiBaseUrl` |

Versioned packages: `https://<cdn>/parser/<parserVersion>/bundle.json`

## Region / account

The template is environment-agnostic. Before a later deploy, lock:

1. AWS account (the existing one is fine if SpendSMS names/tags keep it isolated).
2. Region — recommended `ap-south-1` (India). CloudFront remains global.
3. `cdk bootstrap --qualifier spendsms` in that account/region (later prompt only).

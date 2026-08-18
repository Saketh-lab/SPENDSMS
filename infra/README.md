# SpendSMS Phase-0 AWS infrastructure (Terraform)

Thin serverless control/observability plane from Steps 2–4. **Terraform HCL** replaces the prior CDK app; architecture and Prompt 15/15B hardening are unchanged.

## Layout

```text
infra/
  lambda/          # Python 3.12 handlers (shared with prior CDK)
  static/          # Example parser/config object keys (not uploaded by IaC)
  terraform/       # Official AWS provider — application resources only
  scripts/         # Local validation helpers
```

## Prerequisites

- Terraform >= 1.5
- AWS CLI credentials for `ap-south-1` (plan/apply only)
- Python 3.12+ (Lambda unit tests)

## Commands (validation only in this slice)

```bash
cd infra/terraform
terraform fmt -check -recursive
terraform init
terraform validate
terraform plan -out=tfplan
```

Static checks (no AWS):

```bash
./infra/scripts/validate-static.sh
```

## Default region

`ap-south-1` via `variables.tf` (`aws_region`).

## Tags

Provider `default_tags`:

- `Project = SpendSMS`
- `Phase = Phase0`
- `ManagedBy = Terraform`

## State

Local state file in `infra/terraform/` (gitignored). No S3 backend in Phase-0.

## Outputs

| Output | Android mapping |
| --- | --- |
| `cdn_domain` | `PARSER_CDN_BASE_URL` host |
| `parser_manifest_url` | `PARSER_MANIFEST_URL` |
| `remote_config_url` | remote config CDN URL |
| `api_base_url` | `API_BASE_URL` |
| `artifacts_bucket_name` | publish target |
| `telemetry_table_name` | ops |
| `support_table_name` | ops |

## KMS / CloudFront OAC

Initial CMK policy allows `cloudfront.amazonaws.com` decrypt when `AWS:SourceArn` matches `distribution/*` to avoid Terraform dependency cycles (same tradeoff as CDK).

After first successful apply, tighten to this distribution only:

```bash
DIST_ARN="$(terraform output -raw cloudfront_distribution_arn)"
KEY_ID="$(aws kms list-aliases --query "Aliases[?AliasName=='alias/spendsms-phase0'].TargetKeyId" --output text)"
aws kms get-key-policy --key-id "$KEY_ID" --policy-name default --output text > /tmp/spendsms-key-policy.json
# Replace distribution/* with the exact DIST_ARN in the CloudFront decrypt statement.
# aws kms put-key-policy --key-id "$KEY_ID" --policy-name default --policy file:///tmp/spendsms-key-policy.json
```

S3 `GetObject` is already restricted to this distribution (Allow + Deny-unless-OAC).

## CDK → Terraform migration

1. Do **not** delete CDK bootstrap buckets/stacks (`cdk-spendsms-*` toolkit).
2. Inspect failed `SpendSMS-Phase0` CloudFormation stack and delete or retain partial resources before first `terraform apply` (see migration notes in finish report).
3. Run `terraform plan` and resolve name conflicts (tables, Lambdas, KMS alias, API name).
4. `terraform apply` only after cleanup/plan review.

## Do not deploy from validation prompts

Forbidden: `terraform apply`, deleting bootstrap resources, auto-cleaning failed CFN stacks.

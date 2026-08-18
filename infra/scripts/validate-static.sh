#!/usr/bin/env bash
# SpendSMS Phase-0 Terraform static checks (no AWS credentials required).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${ROOT}/terraform"

fail() {
  echo "STATIC CHECK FAILED: $*" >&2
  exit 1
}

echo "==> Scanning Terraform for forbidden Phase-0 resources..."
FORBIDDEN_PATTERN='aws_cognito_|aws_db_instance|aws_rds_|aws_elasticache_|aws_ecs_|aws_eks_|aws_ec2_instance|reserved_concurrent_executions'
if rg -n "${FORBIDDEN_PATTERN}" "${TF_DIR}" --glob '*.tf' 2>/dev/null; then
  fail "forbidden resource or setting detected"
fi

echo "==> Verifying provider default tags..."
rg -q 'ManagedBy = "Terraform"' "${TF_DIR}/locals.tf" || fail "ManagedBy tag missing"
rg -q 'Project   = "SpendSMS"' "${TF_DIR}/locals.tf" || fail "Project tag missing"

echo "==> Verifying Step-4 API routes..."
rg -q 'POST /v1/telemetry/batch' "${TF_DIR}/apigateway.tf" || fail "telemetry route missing"
rg -q 'POST /v1/support/unsupported-format' "${TF_DIR}/apigateway.tf" || fail "support route missing"

echo "==> Verifying least-privilege DynamoDB IAM..."
rg -q 'SpendSMSTelemetryDynamoDb' "${TF_DIR}/iam.tf" || fail "telemetry DDB policy missing"
rg -q 'dynamodb:PutItem' "${TF_DIR}/locals.tf" || fail "PutItem grant missing"
rg -q 'dynamodb:UpdateItem' "${TF_DIR}/locals.tf" || fail "UpdateItem grant missing"
rg -q 'dynamodb:GetItem' "${TF_DIR}/locals.tf" || fail "GetItem grant missing"
if rg -q 'dynamodb:\*' "${TF_DIR}" --glob '*.tf' || rg -q 'dynamodb:Scan' "${TF_DIR}" --glob '*.tf'; then
  fail "over-broad DynamoDB actions"
fi

echo "==> Verifying privacy-safe HTTP API access logs..."
if rg -q '\$context\.(request|response)Body' "${TF_DIR}/apigateway.tf"; then
  fail "HTTP API access log format includes request/response bodies"
fi

echo "==> Running Python Lambda handler tests..."
python3 -m unittest discover -s "${ROOT}/lambda/tests" -v

echo "All static checks passed."

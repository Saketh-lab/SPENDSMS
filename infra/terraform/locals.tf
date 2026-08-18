data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  partition  = data.aws_partition.current.partition

  common_tags = {
    Project   = "SpendSMS"
    Phase     = "Phase0"
    ManagedBy = "Terraform"
  }

  artifacts_bucket_name = "${var.project_name}-artifacts-${local.account_id}-${var.aws_region}"

  telemetry_function_name = "${var.project_name}-telemetry"
  support_function_name   = "${var.project_name}-support"

  telemetry_table_name = "SpendSMS-Phase0-Telemetry"
  support_table_name   = "SpendSMS-Phase0-SupportSubmissions"

  http_api_name = "${var.project_name}-http-api"

  kms_alias_name = "alias/spendsms-phase0"

  telemetry_timeout_seconds = 8
  support_timeout_seconds   = 5
  lambda_memory_mb          = 128
  log_retention_days        = 14

  telemetry_max_body_bytes = 65536
  support_max_body_bytes   = 16384
  telemetry_ttl_days       = 30
  support_ttl_days         = 14

  telemetry_ddb_actions = ["dynamodb:PutItem", "dynamodb:UpdateItem"]
  support_ddb_actions   = ["dynamodb:GetItem", "dynamodb:PutItem"]
  lambda_kms_actions    = ["kms:Decrypt", "kms:DescribeKey", "kms:Encrypt", "kms:GenerateDataKey"]

  parser_manifest_key = "parser/manifest.json"
  remote_config_key   = "config/remote-config.json"

  # Privacy-safe HTTP API access log format (Step-4 §9). No request/response bodies.
  http_api_access_log_format = jsonencode({
    requestId = "$context.requestId"
    routeKey  = "$context.routeKey"
    status    = "$context.status"
    latency   = "$context.responseLatency"
    ip        = "$context.identity.sourceIp"
  })

  # AWS managed response headers policy: SecurityHeaders
  cloudfront_security_headers_policy_id = "67f7725c-6f97-4210-82d7-5512b31e9d03"
}

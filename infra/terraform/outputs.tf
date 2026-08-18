output "cdn_domain" {
  description = "CloudFront domain for parser/config (Prompt 14 PARSER_CDN_BASE_URL)."
  value       = aws_cloudfront_distribution.phase0.domain_name
}

output "parser_manifest_url" {
  description = "HTTPS parser manifest URL (Prompt 14 PARSER_MANIFEST_URL)."
  value       = "https://${aws_cloudfront_distribution.phase0.domain_name}/${local.parser_manifest_key}"
}

output "remote_config_url" {
  description = "HTTPS remote-config URL (S3+CloudFront, not a Lambda API)."
  value       = "https://${aws_cloudfront_distribution.phase0.domain_name}/${local.remote_config_key}"
}

output "api_base_url" {
  description = "HTTP API base URL (Prompt 14 API_BASE_URL)."
  value       = aws_apigatewayv2_api.phase0.api_endpoint
}

output "artifacts_bucket_name" {
  description = "Private S3 bucket for parser/config artifacts."
  value       = aws_s3_bucket.artifacts.bucket
}

output "telemetry_table_name" {
  value = aws_dynamodb_table.telemetry.name
}

output "support_table_name" {
  value = aws_dynamodb_table.support.name
}

output "kms_key_arn" {
  description = "SpendSMS Phase-0 CMK ARN (for post-deploy KMS policy tightening)."
  value       = aws_kms_key.phase0.arn
}

output "cloudfront_distribution_arn" {
  description = "CloudFront distribution ARN (for post-deploy KMS policy tightening)."
  value       = aws_cloudfront_distribution.phase0.arn
}

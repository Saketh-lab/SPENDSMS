data "archive_file" "lambda" {
  type        = "zip"
  source_dir  = "${path.module}/../lambda"
  output_path = "${path.module}/build/lambda.zip"
  excludes    = ["tests", "__pycache__", ".pytest_cache"]
}

resource "aws_cloudwatch_log_group" "telemetry_lambda" {
  name              = "/aws/lambda/${local.telemetry_function_name}"
  retention_in_days = local.log_retention_days
}

resource "aws_cloudwatch_log_group" "support_lambda" {
  name              = "/aws/lambda/${local.support_function_name}"
  retention_in_days = local.log_retention_days
}

resource "aws_lambda_function" "telemetry" {
  function_name = local.telemetry_function_name
  description   = "SpendSMS Phase-0 POST /v1/telemetry/batch foundation"
  role          = aws_iam_role.telemetry_lambda.arn
  handler       = "telemetry.handler"
  runtime       = "python3.12"
  architectures = ["arm64"]
  timeout       = local.telemetry_timeout_seconds
  memory_size   = local.lambda_memory_mb

  filename         = data.archive_file.lambda.output_path
  source_code_hash = data.archive_file.lambda.output_base64sha256

  environment {
    variables = {
      TELEMETRY_TABLE    = aws_dynamodb_table.telemetry.name
      TELEMETRY_TTL_DAYS = tostring(local.telemetry_ttl_days)
      MAX_BODY_BYTES     = tostring(local.telemetry_max_body_bytes)
    }
  }

  depends_on = [aws_cloudwatch_log_group.telemetry_lambda]

  tags = {
    Name = local.telemetry_function_name
  }
}

resource "aws_lambda_function" "support" {
  function_name = local.support_function_name
  description   = "SpendSMS Phase-0 POST /v1/support/unsupported-format foundation"
  role          = aws_iam_role.support_lambda.arn
  handler       = "support.handler"
  runtime       = "python3.12"
  architectures = ["arm64"]
  timeout       = local.support_timeout_seconds
  memory_size   = local.lambda_memory_mb

  filename         = data.archive_file.lambda.output_path
  source_code_hash = data.archive_file.lambda.output_base64sha256

  environment {
    variables = {
      SUPPORT_TABLE    = aws_dynamodb_table.support.name
      SUPPORT_TTL_DAYS = tostring(local.support_ttl_days)
      MAX_BODY_BYTES   = tostring(local.support_max_body_bytes)
    }
  }

  depends_on = [aws_cloudwatch_log_group.support_lambda]

  tags = {
    Name = local.support_function_name
  }
}

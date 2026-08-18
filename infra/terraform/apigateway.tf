resource "aws_cloudwatch_log_group" "http_api" {
  name              = "/aws/apigateway/${local.http_api_name}"
  retention_in_days = local.log_retention_days
}

data "aws_iam_policy_document" "http_api_logs" {
  statement {
    sid    = "SpendSMSHttpApiWriteAccessLogs"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["apigateway.amazonaws.com"]
    }
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = [aws_cloudwatch_log_group.http_api.arn]
    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:${local.partition}:execute-api:${var.aws_region}:${local.account_id}:${aws_apigatewayv2_api.phase0.id}/*"]
    }
  }
}

resource "aws_cloudwatch_log_resource_policy" "http_api" {
  policy_name     = "${var.project_name}-http-api-logs"
  policy_document = data.aws_iam_policy_document.http_api_logs.json
}

resource "aws_apigatewayv2_api" "phase0" {
  name          = local.http_api_name
  description   = "SpendSMS Phase-0 mobile API. Step-4 surface only. No user authentication."
  protocol_type = "HTTP"

  tags = {
    Name = local.http_api_name
  }
}

resource "aws_apigatewayv2_integration" "telemetry" {
  api_id                 = aws_apigatewayv2_api.phase0.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.telemetry.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_integration" "support" {
  api_id                 = aws_apigatewayv2_api.phase0.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.support.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "telemetry_batch" {
  api_id    = aws_apigatewayv2_api.phase0.id
  route_key = "POST /v1/telemetry/batch"
  target    = "integrations/${aws_apigatewayv2_integration.telemetry.id}"
}

resource "aws_apigatewayv2_route" "support_unsupported_format" {
  api_id    = aws_apigatewayv2_api.phase0.id
  route_key = "POST /v1/support/unsupported-format"
  target    = "integrations/${aws_apigatewayv2_integration.support.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.phase0.id
  name        = "$default"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.http_api.arn
    format          = local.http_api_access_log_format
  }

  default_route_settings {
    throttling_burst_limit = 20
    throttling_rate_limit  = 10
  }

  tags = {
    Name = "${local.http_api_name}-default"
  }
}

resource "aws_lambda_permission" "telemetry_api" {
  statement_id  = "AllowExecutionFromApiGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.telemetry.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.phase0.execution_arn}/*/*"
}

resource "aws_lambda_permission" "support_api" {
  statement_id  = "AllowExecutionFromApiGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.support.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.phase0.execution_arn}/*/*"
}

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "telemetry_lambda" {
  name               = "${var.project_name}-telemetry-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role" "support_lambda" {
  name               = "${var.project_name}-support-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

data "aws_iam_policy_document" "telemetry_lambda" {
  statement {
    sid       = "SpendSMSTelemetryDynamoDb"
    effect    = "Allow"
    actions   = local.telemetry_ddb_actions
    resources = [aws_dynamodb_table.telemetry.arn]
  }

  statement {
    sid       = "SpendSMSLambdaCmk"
    effect    = "Allow"
    actions   = local.lambda_kms_actions
    resources = [aws_kms_key.phase0.arn]
  }

  statement {
    sid    = "SpendSMSLambdaLogs"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.telemetry_lambda.arn}:*"]
  }
}

data "aws_iam_policy_document" "support_lambda" {
  statement {
    sid       = "SpendSMSSupportDynamoDb"
    effect    = "Allow"
    actions   = local.support_ddb_actions
    resources = [aws_dynamodb_table.support.arn]
  }

  statement {
    sid       = "SpendSMSLambdaCmk"
    effect    = "Allow"
    actions   = local.lambda_kms_actions
    resources = [aws_kms_key.phase0.arn]
  }

  statement {
    sid    = "SpendSMSLambdaLogs"
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.support_lambda.arn}:*"]
  }
}

resource "aws_iam_role_policy" "telemetry_lambda" {
  name   = "${var.project_name}-telemetry-lambda"
  role   = aws_iam_role.telemetry_lambda.id
  policy = data.aws_iam_policy_document.telemetry_lambda.json
}

resource "aws_iam_role_policy" "support_lambda" {
  name   = "${var.project_name}-support-lambda"
  role   = aws_iam_role.support_lambda.id
  policy = data.aws_iam_policy_document.support_lambda.json
}

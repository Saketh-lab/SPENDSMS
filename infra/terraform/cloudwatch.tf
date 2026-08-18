resource "aws_cloudwatch_metric_alarm" "telemetry_errors" {
  alarm_name          = "${var.project_name}-telemetry-errors"
  alarm_description   = "SpendSMS Phase-0 telemetry Lambda errors"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.telemetry.function_name
  }
}

resource "aws_cloudwatch_metric_alarm" "support_errors" {
  alarm_name          = "${var.project_name}-support-errors"
  alarm_description   = "SpendSMS Phase-0 support Lambda errors"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.support.function_name
  }
}

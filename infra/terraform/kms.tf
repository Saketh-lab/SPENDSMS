resource "aws_kms_key" "phase0" {
  description             = "SpendSMS Phase-0 CMK for parser artifacts and operational DynamoDB"
  enable_key_rotation     = true
  deletion_window_in_days = 30

  policy = data.aws_iam_policy_document.kms_key.json
}

resource "aws_kms_alias" "phase0" {
  name          = local.kms_alias_name
  target_key_id = aws_kms_key.phase0.key_id
}

data "aws_iam_policy_document" "kms_key" {
  statement {
    sid    = "EnableRootAccountAdmin"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:${local.partition}:iam::${local.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  # Initial deploy: distribution/* avoids Key ↔ Distribution ↔ S3 dependency cycles.
  # After first apply, tighten to aws_cloudfront_distribution.phase0.arn only (see README).
  statement {
    sid    = "SpendSMSCloudFrontDecryptOac"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
    ]
    resources = ["*"]
    condition {
      test     = "ArnLike"
      variable = "AWS:SourceArn"
      values   = ["arn:${local.partition}:cloudfront::${local.account_id}:distribution/*"]
    }
  }
}

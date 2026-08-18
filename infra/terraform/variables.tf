variable "aws_region" {
  description = "AWS region for SpendSMS Phase-0 regional resources (Lambda, DynamoDB, S3, API Gateway)."
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "SpendSMS resource name prefix for isolation from other applications in the account."
  type        = string
  default     = "spendsms-phase0"
}

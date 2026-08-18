resource "aws_cloudfront_origin_access_control" "phase0" {
  name                              = "${var.project_name}-oac"
  description                       = "SpendSMS Phase-0 CloudFront OAC for parser/config artifacts"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_cache_policy" "manifest" {
  name        = "${var.project_name}-manifest"
  comment     = "Parser manifest / remote config: 1h default, 6-24h client cache window"
  default_ttl = 3600
  max_ttl     = 86400
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config {
      cookie_behavior = "none"
    }
    headers_config {
      header_behavior = "none"
    }
    query_strings_config {
      query_string_behavior = "none"
    }
    enable_accept_encoding_gzip   = true
    enable_accept_encoding_brotli = true
  }
}

resource "aws_cloudfront_cache_policy" "packages" {
  name        = "${var.project_name}-packages"
  comment     = "Versioned immutable parser bundles"
  default_ttl = 604800
  max_ttl     = 31536000
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config {
      cookie_behavior = "none"
    }
    headers_config {
      header_behavior = "none"
    }
    query_strings_config {
      query_string_behavior = "none"
    }
    enable_accept_encoding_gzip   = true
    enable_accept_encoding_brotli = true
  }
}

resource "aws_cloudfront_distribution" "phase0" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "SpendSMS Phase-0 parser/config/legal static delivery"
  http_version    = "http2and3"
  price_class     = "PriceClass_200"

  origin {
    domain_name              = aws_s3_bucket.artifacts.bucket_regional_domain_name
    origin_id                = "spendsms-phase0-s3-origin"
    origin_access_control_id = aws_cloudfront_origin_access_control.phase0.id
  }

  default_cache_behavior {
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    target_origin_id           = "spendsms-phase0-s3-origin"
    viewer_protocol_policy     = "https-only"
    compress                   = true
    cache_policy_id            = aws_cloudfront_cache_policy.packages.id
    response_headers_policy_id = local.cloudfront_security_headers_policy_id
  }

  ordered_cache_behavior {
    path_pattern               = "parser/manifest.json"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    target_origin_id           = "spendsms-phase0-s3-origin"
    viewer_protocol_policy     = "https-only"
    compress                   = true
    cache_policy_id            = aws_cloudfront_cache_policy.manifest.id
    response_headers_policy_id = local.cloudfront_security_headers_policy_id
  }

  ordered_cache_behavior {
    path_pattern               = "config/*"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    target_origin_id           = "spendsms-phase0-s3-origin"
    viewer_protocol_policy     = "https-only"
    compress                   = true
    cache_policy_id            = aws_cloudfront_cache_policy.manifest.id
    response_headers_policy_id = local.cloudfront_security_headers_policy_id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "${var.project_name}-cdn"
  }
}

import * as path from 'path';
import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import { HttpLambdaIntegration } from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as cloudwatch from 'aws-cdk-lib/aws-cloudwatch';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as kms from 'aws-cdk-lib/aws-kms';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import {
  ARTIFACT_KEY_PREFIXES,
  SPENDSMS_TAGS,
  SUPPORT_MAX_BODY_BYTES,
  SUPPORT_TTL_DAYS,
  TELEMETRY_MAX_BODY_BYTES,
  TELEMETRY_TTL_DAYS,
} from './constants';

/**
 * Thin Phase-0 control/observability plane (Steps 2–4).
 *
 * Creates only SpendSMS-named resources. Does not import or reference other
 * applications' stacks, buckets, tables, or roles.
 *
 * Mobile API surface is Step-4 / OpenAPI only:
 *   POST /v1/telemetry/batch
 *   POST /v1/support/unsupported-format
 *
 * Parser/config are S3 + CloudFront objects, matching Prompt 14 paths:
 *   /parser/manifest.json
 *   /parser/{parserVersion}/bundle.json
 *   /config/remote-config.json
 */
export class SpendSmsPhase0Stack extends cdk.Stack {
  public readonly artifactsBucket: s3.Bucket;
  public readonly distribution: cloudfront.Distribution;
  public readonly httpApi: apigwv2.HttpApi;
  public readonly telemetryTable: dynamodb.Table;
  public readonly supportTable: dynamodb.Table;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, {
      ...props,
      tags: {
        ...SPENDSMS_TAGS,
        ...props?.tags,
      },
    });

    for (const [key, value] of Object.entries(SPENDSMS_TAGS)) {
      cdk.Tags.of(this).add(key, value);
    }

    const key = this.createCmk();
    this.artifactsBucket = this.createArtifactsBucket(key);
    this.distribution = this.createCdn(this.artifactsBucket, key);
    this.telemetryTable = this.createTelemetryTable(key);
    this.supportTable = this.createSupportTable(key);

    const telemetryFn = this.createLambda({
      id: 'SpendSmsPhase0TelemetryFn',
      functionName: 'spendsms-phase0-telemetry',
      handler: 'telemetry.handler',
      description: 'SpendSMS Phase-0 POST /v1/telemetry/batch foundation',
      key,
      environment: {
        TELEMETRY_TABLE: this.telemetryTable.tableName,
        TELEMETRY_TTL_DAYS: String(TELEMETRY_TTL_DAYS),
        MAX_BODY_BYTES: String(TELEMETRY_MAX_BODY_BYTES),
      },
    });
    this.telemetryTable.grantReadWriteData(telemetryFn);
    key.grantEncryptDecrypt(telemetryFn);

    const supportFn = this.createLambda({
      id: 'SpendSmsPhase0SupportFn',
      functionName: 'spendsms-phase0-support',
      handler: 'support.handler',
      description: 'SpendSMS Phase-0 POST /v1/support/unsupported-format foundation',
      key,
      environment: {
        SUPPORT_TABLE: this.supportTable.tableName,
        SUPPORT_TTL_DAYS: String(SUPPORT_TTL_DAYS),
        MAX_BODY_BYTES: String(SUPPORT_MAX_BODY_BYTES),
      },
    });
    this.supportTable.grantReadWriteData(supportFn);
    key.grantEncryptDecrypt(supportFn);

    this.httpApi = this.createHttpApi(telemetryFn, supportFn);
    this.addAlarms(telemetryFn, supportFn);
    this.addOutputs();
  }

  private createCmk(): kms.Key {
    return new kms.Key(this, 'SpendSmsPhase0Key', {
      alias: 'alias/spendsms-phase0',
      description: 'SpendSMS Phase-0 CMK for parser artifacts and operational DynamoDB',
      enableKeyRotation: true,
      pendingWindow: cdk.Duration.days(30),
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });
  }

  private createArtifactsBucket(key: kms.Key): s3.Bucket {
    const bucket = new s3.Bucket(this, 'SpendSmsPhase0Artifacts', {
      bucketName: cdk.Fn.join('-', [
        'spendsms-phase0-artifacts',
        cdk.Aws.ACCOUNT_ID,
        cdk.Aws.REGION,
      ]),
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.KMS,
      encryptionKey: key,
      bucketKeyEnabled: true,
      enforceSSL: true,
      versioned: true,
      objectOwnership: s3.ObjectOwnership.BUCKET_OWNER_ENFORCED,
      publicReadAccess: false,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      autoDeleteObjects: false,
      lifecycleRules: [
        {
          id: 'spendsms-phase0-noncurrent-parser-expire',
          noncurrentVersionExpiration: cdk.Duration.days(180),
          expiredObjectDeleteMarker: true,
        },
      ],
    });

    bucket.addToResourcePolicy(
      new iam.PolicyStatement({
        sid: 'SpendSMSDenyInsecureTransport',
        effect: iam.Effect.DENY,
        principals: [new iam.AnyPrincipal()],
        actions: ['s3:*'],
        resources: [bucket.bucketArn, bucket.arnForObjects('*')],
        conditions: { Bool: { 'aws:SecureTransport': 'false' } },
      }),
    );

    return bucket;
  }

  private createCdn(bucket: s3.Bucket, key: kms.Key): cloudfront.Distribution {
    const oac = new cloudfront.S3OriginAccessControl(this, 'SpendSmsPhase0Oac', {
      description: 'SpendSMS Phase-0 CloudFront OAC for parser/config artifacts',
      originAccessControlName: 'spendsms-phase0-oac',
      signing: cloudfront.Signing.SIGV4_ALWAYS,
    });

    const origin = origins.S3BucketOrigin.withOriginAccessControl(bucket, {
      originAccessControl: oac,
      originId: 'spendsms-phase0-s3-origin',
    });

    const manifestCache = new cloudfront.CachePolicy(this, 'SpendSmsPhase0ManifestCache', {
      cachePolicyName: 'spendsms-phase0-manifest',
      comment: 'Parser manifest / remote config: 1h default, 6–24h client cache window',
      defaultTtl: cdk.Duration.hours(1),
      minTtl: cdk.Duration.seconds(0),
      maxTtl: cdk.Duration.hours(24),
      cookieBehavior: cloudfront.CacheCookieBehavior.none(),
      headerBehavior: cloudfront.CacheHeaderBehavior.none(),
      queryStringBehavior: cloudfront.CacheQueryStringBehavior.none(),
      enableAcceptEncodingGzip: true,
      enableAcceptEncodingBrotli: true,
    });

    const packageCache = new cloudfront.CachePolicy(this, 'SpendSmsPhase0PackageCache', {
      cachePolicyName: 'spendsms-phase0-packages',
      comment: 'Versioned immutable parser bundles',
      defaultTtl: cdk.Duration.days(7),
      minTtl: cdk.Duration.seconds(0),
      maxTtl: cdk.Duration.days(365),
      cookieBehavior: cloudfront.CacheCookieBehavior.none(),
      headerBehavior: cloudfront.CacheHeaderBehavior.none(),
      queryStringBehavior: cloudfront.CacheQueryStringBehavior.none(),
      enableAcceptEncodingGzip: true,
      enableAcceptEncodingBrotli: true,
    });

    const distribution = new cloudfront.Distribution(this, 'SpendSmsPhase0Cdn', {
      comment: 'SpendSMS Phase-0 parser/config/legal static delivery',
      enableIpv6: true,
      httpVersion: cloudfront.HttpVersion.HTTP2_AND_3,
      minimumProtocolVersion: cloudfront.SecurityPolicyProtocol.TLS_V1_2_2021,
      priceClass: cloudfront.PriceClass.PRICE_CLASS_200,
      defaultBehavior: {
        origin,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD,
        cachePolicy: packageCache,
        responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
        compress: true,
      },
      additionalBehaviors: {
        'parser/manifest.json': {
          origin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
          cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD,
          cachePolicy: manifestCache,
          responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
          compress: true,
        },
        'config/*': {
          origin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
          cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD,
          cachePolicy: manifestCache,
          responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
          compress: true,
        },
      },
    });

    key.addToResourcePolicy(
      new iam.PolicyStatement({
        sid: 'SpendSMSCloudFrontDecryptViaS3',
        principals: [new iam.ServicePrincipal('cloudfront.amazonaws.com')],
        actions: ['kms:Decrypt', 'kms:DescribeKey'],
        resources: ['*'],
        conditions: {
          StringEquals: {
            'kms:ViaService': cdk.Fn.join('', ['s3.', cdk.Aws.REGION, '.amazonaws.com']),
          },
        },
      }),
    );

    return distribution;
  }

  private createTelemetryTable(key: kms.Key): dynamodb.Table {
    return new dynamodb.Table(this, 'SpendSmsPhase0Telemetry', {
      tableName: 'SpendSMS-Phase0-Telemetry',
      partitionKey: { name: 'pk', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      encryption: dynamodb.TableEncryption.CUSTOMER_MANAGED,
      encryptionKey: key,
      timeToLiveAttribute: 'ttl',
      pointInTimeRecoverySpecification: { pointInTimeRecoveryEnabled: true },
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      contributorInsightsEnabled: false,
    });
  }

  private createSupportTable(key: kms.Key): dynamodb.Table {
    return new dynamodb.Table(this, 'SpendSmsPhase0Support', {
      tableName: 'SpendSMS-Phase0-SupportSubmissions',
      partitionKey: { name: 'pk', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING },
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      encryption: dynamodb.TableEncryption.CUSTOMER_MANAGED,
      encryptionKey: key,
      timeToLiveAttribute: 'ttl',
      pointInTimeRecoverySpecification: { pointInTimeRecoveryEnabled: true },
      removalPolicy: cdk.RemovalPolicy.RETAIN,
      contributorInsightsEnabled: false,
    });
  }

  private createLambda(opts: {
    id: string;
    functionName: string;
    handler: string;
    description: string;
    key: kms.Key;
    environment: Record<string, string>;
  }): lambda.Function {
    const logGroup = new logs.LogGroup(this, `${opts.id}Logs`, {
      logGroupName: `/aws/lambda/${opts.functionName}`,
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    return new lambda.Function(this, opts.id, {
      functionName: opts.functionName,
      description: opts.description,
      runtime: lambda.Runtime.PYTHON_3_12,
      architecture: lambda.Architecture.ARM_64,
      handler: opts.handler,
      code: lambda.Code.fromAsset(path.join(__dirname, '..', 'lambda'), {
        exclude: ['tests', '**/__pycache__', '*.pyc', '.pytest_cache'],
      }),
      timeout: cdk.Duration.seconds(10),
      memorySize: 256,
      environment: opts.environment,
      environmentEncryption: opts.key,
      logGroup,
      tracing: lambda.Tracing.DISABLED,
      reservedConcurrentExecutions: 5,
    });
  }

  private createHttpApi(
    telemetryFn: lambda.Function,
    supportFn: lambda.Function,
  ): apigwv2.HttpApi {
    const accessLogs = new logs.LogGroup(this, 'SpendSmsPhase0HttpApiAccessLogs', {
      logGroupName: '/aws/apigateway/spendsms-phase0-http-api',
      retention: logs.RetentionDays.ONE_MONTH,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    const api = new apigwv2.HttpApi(this, 'SpendSmsPhase0HttpApi', {
      apiName: 'spendsms-phase0-http-api',
      description:
        'SpendSMS Phase-0 mobile API. Step-4 surface only. No user authentication.',
      corsPreflight: undefined,
      disableExecuteApiEndpoint: false,
      createDefaultStage: false,
    });

    api.addRoutes({
      path: '/v1/telemetry/batch',
      methods: [apigwv2.HttpMethod.POST],
      integration: new HttpLambdaIntegration('SpendSmsTelemetryIntegration', telemetryFn),
    });
    api.addRoutes({
      path: '/v1/support/unsupported-format',
      methods: [apigwv2.HttpMethod.POST],
      integration: new HttpLambdaIntegration('SpendSmsSupportIntegration', supportFn),
    });

    const stage = new apigwv2.HttpStage(this, 'SpendSmsPhase0HttpApiStage', {
      httpApi: api,
      stageName: '$default',
      autoDeploy: true,
      throttle: {
        rateLimit: 10,
        burstLimit: 20,
      },
    });

    const cfnStage = stage.node.defaultChild as apigwv2.CfnStage;
    cfnStage.accessLogSettings = {
      destinationArn: accessLogs.logGroupArn,
      format: JSON.stringify({
        requestId: '$context.requestId',
        routeKey: '$context.routeKey',
        status: '$context.status',
        latency: '$context.responseLatency',
        ip: '$context.identity.sourceIp',
      }),
    };

    accessLogs.addToResourcePolicy(
      new iam.PolicyStatement({
        sid: 'SpendSMSHttpApiWriteAccessLogs',
        principals: [new iam.ServicePrincipal('apigateway.amazonaws.com')],
        actions: ['logs:CreateLogStream', 'logs:PutLogEvents'],
        resources: [accessLogs.logGroupArn],
        conditions: {
          ArnLike: {
            'aws:SourceArn': cdk.Fn.join('', [
              'arn:aws:execute-api:',
              cdk.Aws.REGION,
              ':',
              cdk.Aws.ACCOUNT_ID,
              ':',
              api.apiId,
              '/*',
            ]),
          },
        },
      }),
    );

    return api;
  }

  private addAlarms(telemetryFn: lambda.Function, supportFn: lambda.Function): void {
    new cloudwatch.Alarm(this, 'SpendSmsPhase0TelemetryErrors', {
      alarmName: 'spendsms-phase0-telemetry-errors',
      alarmDescription: 'SpendSMS Phase-0 telemetry Lambda errors',
      metric: telemetryFn.metricErrors({ period: cdk.Duration.minutes(5) }),
      threshold: 1,
      evaluationPeriods: 1,
      datapointsToAlarm: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });

    new cloudwatch.Alarm(this, 'SpendSmsPhase0SupportErrors', {
      alarmName: 'spendsms-phase0-support-errors',
      alarmDescription: 'SpendSMS Phase-0 support Lambda errors',
      metric: supportFn.metricErrors({ period: cdk.Duration.minutes(5) }),
      threshold: 1,
      evaluationPeriods: 1,
      datapointsToAlarm: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
  }

  private addOutputs(): void {
    new cdk.CfnOutput(this, 'SpendSmsCdnDomain', {
      description: 'CloudFront domain for parser/config (Prompt 14 PARSER_CDN_BASE_URL)',
      value: this.distribution.distributionDomainName,
    });
    new cdk.CfnOutput(this, 'SpendSmsParserManifestUrl', {
      description: 'HTTPS parser manifest URL (Prompt 14 PARSER_MANIFEST_URL)',
      value: `https://${this.distribution.distributionDomainName}/${ARTIFACT_KEY_PREFIXES.parserManifest}`,
    });
    new cdk.CfnOutput(this, 'SpendSmsRemoteConfigUrl', {
      description: 'HTTPS remote-config URL (S3+CloudFront, not a Lambda API)',
      value: `https://${this.distribution.distributionDomainName}/${ARTIFACT_KEY_PREFIXES.remoteConfig}`,
    });
    new cdk.CfnOutput(this, 'SpendSmsApiBaseUrl', {
      description: 'HTTP API base URL (Prompt 14 API_BASE_URL later)',
      value: this.httpApi.apiEndpoint,
    });
    new cdk.CfnOutput(this, 'SpendSmsArtifactsBucketName', {
      description: 'Private S3 bucket for parser/config artifacts',
      value: this.artifactsBucket.bucketName,
    });
    new cdk.CfnOutput(this, 'SpendSmsTelemetryTableName', {
      value: this.telemetryTable.tableName,
    });
    new cdk.CfnOutput(this, 'SpendSmsSupportTableName', {
      value: this.supportTable.tableName,
    });
  }
}

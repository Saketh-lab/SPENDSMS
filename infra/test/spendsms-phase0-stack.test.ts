import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { SpendSmsPhase0Stack } from '../lib/spendsms-phase0-stack';

function synth(): Template {
  const app = new App();
  const stack = new SpendSmsPhase0Stack(app, 'SpendSMS-Phase0');
  return Template.fromStack(stack);
}

describe('SpendSMS Phase-0 stack', () => {
  test('creates the approved thin control-plane resource types', () => {
    const template = synth();
    template.resourceCountIs('AWS::S3::Bucket', 1);
    template.resourceCountIs('AWS::CloudFront::Distribution', 1);
    template.resourceCountIs('AWS::CloudFront::OriginAccessControl', 1);
    template.resourceCountIs('AWS::ApiGatewayV2::Api', 1);
    template.resourceCountIs('AWS::Lambda::Function', 2);
    template.resourceCountIs('AWS::DynamoDB::Table', 2);
    template.resourceCountIs('AWS::KMS::Key', 1);
    template.resourceCountIs('AWS::Logs::LogGroup', 3);
    template.resourceCountIs('AWS::CloudWatch::Alarm', 2);
  });

  test('does not create disallowed Phase-0 services', () => {
    const template = synth();
    const names = Object.keys(template.toJSON().Resources as Record<string, unknown>).map(
      (logicalId) => (template.toJSON().Resources as Record<string, { Type: string }>)[logicalId].Type,
    );
    const forbidden = [
      'AWS::Cognito::UserPool',
      'AWS::RDS::DBInstance',
      'AWS::RDS::DBCluster',
      'AWS::EC2::Instance',
      'AWS::ECS::Cluster',
      'AWS::EKS::Cluster',
      'AWS::Elasticache::CacheCluster',
      'AWS::ApiGateway::RestApi',
      'AWS::SQS::Queue',
    ];
    for (const type of forbidden) {
      expect(names).not.toContain(type);
    }
  });

  test('HTTP API only exposes Step-4 mobile routes', () => {
    const template = synth();
    const routes = template.findResources('AWS::ApiGatewayV2::Route');
    const keys = Object.values(routes).map((r) => r.Properties.RouteKey as string);
    expect(keys.sort()).toEqual([
      'POST /v1/support/unsupported-format',
      'POST /v1/telemetry/batch',
    ]);
    expect(keys.join(' ')).not.toContain('parser-manifest');
    expect(keys.join(' ')).not.toContain('/v1/config');
  });

  test('DynamoDB tables are on-demand, TTL-enabled, and SpendSMS-named', () => {
    const template = synth();
    template.hasResourceProperties('AWS::DynamoDB::Table', {
      TableName: 'SpendSMS-Phase0-Telemetry',
      BillingMode: 'PAY_PER_REQUEST',
      TimeToLiveSpecification: { AttributeName: 'ttl', Enabled: true },
    });
    template.hasResourceProperties('AWS::DynamoDB::Table', {
      TableName: 'SpendSMS-Phase0-SupportSubmissions',
      BillingMode: 'PAY_PER_REQUEST',
      TimeToLiveSpecification: { AttributeName: 'ttl', Enabled: true },
    });
  });

  test('S3 artifacts bucket is private, versioned, and KMS-encrypted', () => {
    const template = synth();
    template.hasResourceProperties('AWS::S3::Bucket', {
      BucketName: Match.objectLike({
        'Fn::Join': Match.arrayWith([
          '-',
          Match.arrayWith(['spendsms-phase0-artifacts']),
        ]),
      }),
      PublicAccessBlockConfiguration: {
        BlockPublicAcls: true,
        BlockPublicPolicy: true,
        IgnorePublicAcls: true,
        RestrictPublicBuckets: true,
      },
      VersioningConfiguration: { Status: 'Enabled' },
      BucketEncryption: {
        ServerSideEncryptionConfiguration: [
          Match.objectLike({
            BucketKeyEnabled: true,
            ServerSideEncryptionByDefault: Match.objectLike({
              SSEAlgorithm: 'aws:kms',
            }),
          }),
        ],
      },
    });
  });

  test('CloudFront is HTTPS-only with OAC', () => {
    const template = synth();
    template.hasResourceProperties('AWS::CloudFront::Distribution', {
      DistributionConfig: Match.objectLike({
        HttpVersion: 'http2and3',
        IPV6Enabled: true,
        DefaultCacheBehavior: Match.objectLike({
          ViewerProtocolPolicy: 'https-only',
        }),
      }),
    });
    template.hasResourceProperties('AWS::CloudFront::OriginAccessControl', {
      OriginAccessControlConfig: Match.objectLike({
        Name: 'spendsms-phase0-oac',
        SigningBehavior: 'always',
        SigningProtocol: 'sigv4',
      }),
    });
  });

  test('applies SpendSMS isolation tags', () => {
    const template = synth();
    const bucket = Object.values(template.findResources('AWS::S3::Bucket'))[0];
    const tagMap = Object.fromEntries(
      ((bucket.Properties.Tags as Array<{ Key: string; Value: string }>) ?? []).map((t) => [
        t.Key,
        t.Value,
      ]),
    );
    expect(tagMap.Project).toBe('SpendSMS');
    expect(tagMap.Phase).toBe('Phase0');
    expect(tagMap.ManagedBy).toBe('IaC');

    const table = Object.values(template.findResources('AWS::DynamoDB::Table'))[0];
    const tableTags = Object.fromEntries(
      ((table.Properties.Tags as Array<{ Key: string; Value: string }>) ?? []).map((t) => [
        t.Key,
        t.Value,
      ]),
    );
    expect(tableTags.Project).toBe('SpendSMS');
  });

  test('Lambda IAM is scoped to SpendSMS tables rather than account-wide DynamoDB', () => {
    const template = synth();
    const policies = template.findResources('AWS::IAM::Policy');
    const docs = Object.values(policies).flatMap((p) => {
      const statements = p.Properties.PolicyDocument.Statement as Array<{
        Action: string | string[];
        Resource: unknown;
      }>;
      return statements;
    });
    const dynamo = docs.filter((s) => {
      const actions = Array.isArray(s.Action) ? s.Action : [s.Action];
      return actions.some((a) => a.startsWith('dynamodb:'));
    });
    expect(dynamo.length).toBeGreaterThan(0);
    for (const statement of dynamo) {
      const json = JSON.stringify(statement.Resource);
      expect(json).not.toBe('"*"');
    }
  });
});

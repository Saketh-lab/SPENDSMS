#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { SpendSmsPhase0Stack } from '../lib/spendsms-phase0-stack';
import { SPENDSMS_TAGS } from '../lib/constants';

/**
 * SpendSMS Phase-0 CDK app.
 *
 * Environment-agnostic by default so `cdk synth` does not require credentials
 * or mutate the shared AWS account. Region/account are chosen at deploy time
 * (later prompt). Recommended region: ap-south-1.
 *
 * Do not run `cdk deploy` / `cdk bootstrap` as part of Prompt 15.
 */
const app = new cdk.App();

const stack = new SpendSmsPhase0Stack(app, 'SpendSMS-Phase0', {
  description:
    'SpendSMS Phase-0 thin serverless control/observability plane (parser CDN + telemetry/support API). Isolated from other applications in the account.',
  env: undefined,
  terminationProtection: false,
});

for (const [key, value] of Object.entries(SPENDSMS_TAGS)) {
  cdk.Tags.of(stack).add(key, value);
}

app.synth();

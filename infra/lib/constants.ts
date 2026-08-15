/**
 * SpendSMS Phase-0 isolation constants.
 *
 * Tags are applied to every taggable resource. Names are SpendSMS-specific so
 * this stack can coexist with unrelated applications in the same AWS account.
 */
export const SPENDSMS_TAGS = {
  Project: 'SpendSMS',
  Phase: 'Phase0',
  ManagedBy: 'IaC',
} as const;

export const STACK_NAME = 'SpendSMS-Phase0';

/** Custom CDK bootstrap qualifier — do not use the default hnb659fds toolkit. */
export const CDK_BOOTSTRAP_QUALIFIER = 'spendsms';

export const ARTIFACT_KEY_PREFIXES = {
  parserManifest: 'parser/manifest.json',
  parserPackage: (parserVersion: string): string => `parser/${parserVersion}/bundle.json`,
  remoteConfig: 'config/remote-config.json',
  legal: 'legal/',
} as const;

export const TELEMETRY_MAX_BODY_BYTES = 64 * 1024;
export const SUPPORT_MAX_BODY_BYTES = 16 * 1024;
export const SUPPORT_TTL_DAYS = 14;
export const TELEMETRY_TTL_DAYS = 30;

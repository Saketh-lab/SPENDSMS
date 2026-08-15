SpendSMS Phase-0 static objects (S3 keys, CloudFront URLs).

These files are **not** uploaded by Prompt 15 (no AWS deploy). After a later
deploy, publish to the SpendSMS artifacts bucket:

| S3 key | CloudFront URL | Android contract |
| --- | --- | --- |
| `parser/manifest.json` | `https://<cdn>/parser/manifest.json` | `PARSER_MANIFEST_URL` |
| `parser/<parserVersion>/bundle.json` | `https://<cdn>/parser/<parserVersion>/bundle.json` | Prompt 14 package URL |
| `config/remote-config.json` | `https://<cdn>/config/remote-config.json` | remote config (not Lambda) |
| `legal/` | `https://<cdn>/legal/...` | optional public/legal pages |

`packageUrl` inside the manifest must be an absolute `https://` CloudFront URL.
Signed parser bundles reuse the Prompt 6/14 envelope; do not add a second format.

# SpendSMS — Revised Product Requirements Document

**Document version:** 1.0  
**Status:** Phase-0 MVP definition  
**Product type:** Android-first personal money-management application  
**Initial market:** India  
**Primary input:** Transactional SMS alerts  
**Working name:** SpendSMS  
**Last updated:** 5 August 2026

---

## 1. Executive Summary

SpendSMS is an Android-first application that converts transactional SMS alerts already present on a user's device into a clear view of spending, credits, categories, merchants, and suspected recurring subscriptions.

The product is designed for users who want a consolidated financial overview without manually entering every transaction or connecting bank credentials. The preferred architecture is local-first: raw SMS content should remain on the device, only financial messages should be processed, and raw message text should be discarded after extraction unless the user explicitly chooses otherwise.

The current focus is **Phase-0: a commercially testable MVP**. Phase-0 must prove four things before broader investment:

1. Google Play accepts the app's declared SMS-based money-management use case.
2. The parser achieves useful accuracy across common Indian bank, card, and UPI message formats.
3. Users trust the permission and privacy experience enough to complete onboarding.
4. The resulting dashboard is valuable enough to drive repeat usage or paid conversion.

Phase-0 will not attempt to become a full financial-planning platform. It will provide reliable transaction discovery, correction, categorisation, and a simple spending and subscription overview.

---



## 2. Product Vision

Help people understand where their money goes within minutes, using transaction information they already receive on their phones, without requiring bank passwords or tedious manual entry.

### Product promise

> Select a period and quickly understand your spending, credits, and recurring payments from transactional messages already on your phone.



### Positioning

SpendSMS is an **informational personal money-management tool**, not a bank, payment service, accounting system, credit provider, investment adviser, or authoritative financial record.

---



## 3. Problem Statement

Indian consumers commonly use multiple bank accounts, cards, wallets, and UPI applications. Transaction alerts may be spread across SMS messages from several institutions, making it difficult to answer basic questions such as:

- How much did I spend this month?
- Which categories consume most of my money?
- Which merchants receive the most money?
- What subscriptions or recurring payments am I still paying for?
- Are transfers, refunds, and reversals distorting my spending total?

Current alternatives often require manual entry, bank-account linking, reviewing multiple apps, maintaining spreadsheets, or searching SMS messages individually. Users need a faster and more privacy-conscious way to consolidate these alerts into an understandable overview.

---



## 4. Goals and Non-Goals



### 4.1 Phase-0 goals

1. Detect valid financial transactions from supported SMS formats.
2. Extract amount, date, direction, merchant or recipient, institution, and payment method.
3. Avoid counting duplicate alerts and own-account transfers as ordinary spending where confidence is sufficient.
4. Categorise detected expenses into useful, understandable categories.
5. Identify and clearly label suspected recurring subscriptions.
6. Provide a readable dashboard and searchable transaction list.
7. Let users correct detections and immediately update totals.
8. Establish user trust through transparent permissions, local-first processing, and deletion controls.
9. Validate commercial demand through activation, retention, and willingness-to-pay experiments.
10. Pass Google Play review without misrepresenting financial accuracy or data practices.



### 4.2 Phase-0 non-goals

Phase-0 will not include:

- Bank-account or Account Aggregator integration.
- UPI initiation, money transfer, bill payment, or subscription cancellation.
- Credit scores, loans, insurance, investments, tax filing, or financial advice.
- Budget automation, savings plans, or advanced forecasting.
- Shared, family, or business accounts.
- Receipt scanning or email analysis.
- Cross-device cloud synchronisation.
- iOS SMS-inbox analysis.
- Advertising targeted using SMS content, merchants, balances, categories, or transaction history.
- Sale or sharing of transactional data.
- A conversational AI financial adviser.
- Claims that the dashboard exactly matches a bank statement.

---



## 5. Target Users



### 5.1 Primary user

An Android user in India who:

- Uses one or more bank accounts, cards, or UPI services.
- Receives transaction alerts by SMS.
- Does not consistently track expenses.
- Wants a quick overview without sharing bank credentials.
- Is willing to grant narrowly explained SMS access for money management.
- Values simple language and low setup effort.



### 5.2 Secondary users

- Students and first-job professionals beginning to manage personal spending.
- Users with multiple cards or bank accounts who want one consolidated view.
- Users auditing recurring subscriptions and automatic debits.
- Privacy-conscious users who prefer local processing over bank linking.



### 5.3 Excluded or unsupported users in Phase-0

- Users whose institutions do not send useful transactional SMS alerts.
- Users primarily using iOS.
- Users expecting accounting-grade reconciliation.
- Businesses requiring GST, invoicing, expense approvals, or bookkeeping.
- Users who need joint or household financial management.



### 5.4 Representative persona

**Arjun, 29, working professional**  
Arjun uses two bank accounts, one credit card, and several UPI apps. He receives SMS alerts but rarely tracks expenses. He wants to open one application and understand monthly spending, top categories, and active subscriptions without connecting bank credentials.

---



## 6. Jobs to Be Done

1. **When I want to understand recent spending**, help me turn scattered transaction alerts into a trustworthy summary with minimal setup.
2. **When a transaction is misunderstood**, let me correct it quickly so my totals become useful.
3. **When I suspect I am paying for forgotten services**, show recurring payments with evidence and uncertainty clearly stated.
4. **When I am concerned about privacy**, explain exactly what is read, processed, stored, transmitted, and deleted.
5. **When I compare periods**, show whether spending has increased or decreased without presenting estimates as official bank records.

---



## 7. Core User Journeys



### 7.1 First-use journey

1. User installs and opens the app.
2. App explains the value proposition without immediately requesting permission.
3. App presents a concise privacy summary:
  - Only transactional financial messages are intended to be analysed.
  - Personal conversations and OTPs are excluded.
  - Whether processing is on-device.
  - Whether any extracted data leaves the device.
  - How data can be deleted.
4. User may view a sample dashboard before granting access.
5. User taps **Analyse my transactions**.
6. App presents the prominent disclosure and requests the minimum approved SMS permissions.
7. User selects an analysis period.
8. App scans in batches and displays progress.
9. App displays a review summary: messages examined, transactions found, uncertain items, and ignored messages.
10. User reaches the dashboard.
11. User can review uncertain items and correct mistakes.



### 7.2 Returning-user journey

1. User opens the app and sees the last analysed period and timestamp.
2. User can refresh recent transactions or choose a new date range.
3. New data is merged without duplicates.
4. Existing user corrections remain intact.
5. Dashboard highlights changes since the last analysis.



### 7.3 Correction journey

1. User opens a transaction.
2. User can edit merchant display name or category.
3. User can mark the record as expense, credit, refund, transfer, duplicate, subscription, or not a transaction.
4. Dashboard totals update immediately.
5. The correction persists across future analyses.



### 7.4 Subscription-review journey

1. User opens the subscriptions screen.
2. App separates **Suspected**, **Confirmed**, **Dismissed**, and **Possibly inactive** items.
3. App shows the payments that caused a subscription suggestion.
4. User confirms or dismisses the suggestion.
5. App never claims it has cancelled or verified a subscription with the merchant.



### 7.5 Data-deletion journey

1. User opens **Settings → Data and privacy**.
2. User chooses **Delete analysed data** or **Delete account and associated data**, where accounts exist.
3. App explains what will be deleted, what may legally be retained, and any backup-retention period.
4. User confirms deletion.
5. App displays completion or a clear pending status.

---



## 8. Scope by Product Phase



## 8.1 Phase-0 — MVP and current delivery focus

Phase-0 is the smallest store-ready product capable of validating policy feasibility, parsing quality, user trust, and commercial demand.

### A. Onboarding and trust

- Benefit-led onboarding.
- Sample dashboard before permission request.
- Prominent SMS-access disclosure immediately before the system prompt.
- Explicit consent and privacy-policy link.
- Clear statement that results are estimates based on available messages.
- No mandatory sign-up for local-only use.



### B. SMS access and ingestion

- Approved Google Play SMS-based money-management permission flow.
- User-selected analysis periods:
  - Last 30 days.
  - Last 3 months.
  - Last 6 months.
  - Last 12 months.
  - Custom dates.
- Batch processing with visible progress.
- Controlled import fallback if permission approval is unavailable or delayed.
- No modification, deletion, sending, or replying to SMS messages.



### C. Message filtering and parsing

- Exclude personal messages, OTPs, promotions, delivery notices, and non-transactional bank messages.
- Detect common debit, credit, UPI, card, ATM, transfer, refund, reversal, bill-payment, and mandate messages.
- Extract, where available:
  - Amount and currency.
  - Transaction date and time.
  - Merchant or recipient.
  - Debit, credit, refund, or transfer direction.
  - Payment method.
  - Institution and masked account/card identifier.
  - Reference identifier.
  - Confidence score.
- Never reconstruct or display full account or card numbers.



### D. Data quality controls

- Basic duplicate detection.
- Confidence-based handling:
  - High: included automatically.
  - Medium: included and marked for review.
  - Low: excluded from totals until confirmed, or placed in review.
- Persistent manual corrections.
- Own-account transfer correction.
- Refund and reversal handling.



### E. Categorisation

Minimum categories:

- Food and dining.
- Groceries.
- Shopping.
- Transport.
- Fuel.
- Bills and utilities.
- Entertainment.
- Travel.
- Health.
- Education.
- Rent and housing.
- Subscriptions.
- Cash withdrawal.
- Transfers.
- Income and refunds.
- Other.



### F. Dashboard

- Total expenses for selected period.
- Total credits/refunds detected.
- Transaction count.
- Monthly spending trend.
- Category breakdown.
- Top merchants.
- Recent transactions.
- Suspected subscriptions and estimated monthly subscription total.
- Latest analysis time.
- Visible informational disclaimer.



### G. Transaction management

- Search by merchant.
- Filter by date, category, method, and direction.
- Sort by newest, oldest, and amount.
- View extracted details and confidence.
- Correct category, merchant display name, type, duplicate status, and subscription status.



### H. Privacy and settings

- One-action deletion of analysed local data.
- Account deletion in-app and via web if accounts are introduced.
- Permission status and shortcut to Android settings.
- Privacy policy, terms, support, and app information.
- Privacy-safe diagnostics toggle where optional diagnostics are collected.



### I. Commercial validation

- Free core analysis sufficient to demonstrate value.
- Remote feature flags for experiments that do not change privacy-sensitive processing.
- Paywall may be tested only after the first useful dashboard is shown.
- Phase-0 premium experiments may include longer history, additional comparison views, or export, but must not make deletion, corrections, or core privacy controls paid features.
- No SMS-derived targeted advertising.



### Phase-0 release gates

Phase-0 cannot proceed to public production until all are true:

1. Google Play permission declaration is approved for the implemented use case, or the app ships without restricted SMS permissions using a controlled import model.
2. Privacy policy and Play Data safety declarations match actual SDK and data flows.
3. At least 90% amount-extraction accuracy on the supported test corpus.
4. At least 85% precision for transaction detection on supported formats.
5. Duplicate rate is below 5% on supported formats.
6. No raw SMS content appears in analytics, crash logs, support logs, or remote configuration.
7. Data deletion is tested end-to-end.
8. Permission-denied, no-data, interrupted-scan, and parser-failure states are usable.
9. The store listing clearly explains the app's purpose and does not imply banking-grade accuracy.
10. Security and privacy review has no unresolved critical issue.

---



## 8.2 Phase-1 — Retention and monetisation

Phase-1 begins only after Phase-0 demonstrates acceptable policy stability, accuracy, activation, and repeat usage.

- Monthly budgets and category limits.
- Overspending and unusual-spend alerts.
- New or increased subscription alerts.
- Bill and card-payment reminders.
- CSV/spreadsheet export.
- Manual transaction entry.
- Custom categories and merchant rules.
- Better period comparisons and net-cash-flow views.
- Expanded institution and regional-language support.
- Encrypted optional backup and restore.
- Premium subscription with clear store-compliant billing.
- Referral and family invite mechanics that do not expose financial data.
- Improved on-device classification informed by consented, anonymised or synthetic training data.

---



## 8.3 Phase-2 — Platform expansion

- Regulated bank-data integration through appropriate licensed partners or India's Account Aggregator ecosystem.
- Email receipt analysis with separate consent.
- Receipt scanning.
- Household or shared expense views with granular consent.
- Multi-device encrypted synchronisation.
- Natural-language questions over the user's own locally processed data.
- Advanced subscription lifecycle tools, without claiming cancellation unless an actual authorised integration exists.
- Additional countries, currencies, and institution templates.
- iOS companion experience using manual entry, email, receipts, files, or regulated bank integrations; iOS must not promise SMS-inbox scanning.
- Business or freelancer edition only as a separately validated product scope.

---



## 9. Detailed Functional Requirements — Phase-0



### FR-01: Onboarding

The app must:

- Explain the primary benefit in plain language.
- State that the app is not a bank and results may be incomplete.
- Avoid requesting SMS permission on first launch.
- Offer a sample dashboard.
- Link to the privacy policy and support page.
- Permit local-only use without registration, unless a technically necessary account feature is introduced later.



### FR-02: Prominent disclosure and consent

Immediately before the Android permission prompt, the app must disclose:

- The specific SMS access requested.
- That access is used to identify and organise financial transactions.
- Whether processing occurs on-device.
- Whether raw SMS or extracted records are transmitted.
- That OTPs and personal conversations are not intended to be processed.
- How to revoke access and delete data.

Consent must be affirmative and cannot be bundled with unrelated terms.

### FR-03: Analysis-period selection

- Preset and custom ranges must be supported.
- The selected dates must be shown before analysis begins.
- Invalid ranges must be blocked.
- The app must warn when a long range may take additional device resources.



### FR-04: Ingestion

- Read only the message fields needed for the approved core function.
- Do not send, edit, delete, mark read, or otherwise modify messages.
- Process in batches.
- Support cancellation and safe resume.
- Record the last successful scan boundary.



### FR-05: Filtering

The parser must identify likely financial transaction messages and reject unrelated content before deeper processing.

The app must never expose ignored personal-message content in UI, logs, analytics, or support reports.

### FR-06: Transaction extraction

Each transaction record should include:

- Internal ID.
- Timestamp.
- Amount and currency.
- Merchant/recipient display name.
- Direction and transaction type.
- Payment method.
- Institution.
- Masked account identifier.
- Reference hash or token where needed for deduplication.
- Confidence score.
- Source-message local identifier or non-reversible link.
- User-correction status.
- Created and updated timestamps.



### FR-07: Duplicate detection

Potential duplicates must be evaluated using a combination of amount, timestamp, merchant, reference number, masked account, institution, and sender.

A suspected duplicate must not affect totals twice. Users must be able to reverse an incorrect duplicate decision.

### FR-08: Transfers, refunds, and reversals

- Transfers must be separable from spending.
- Likely own-account transfers should be marked for review rather than silently assumed.
- Refunds and reversals should reduce net spending when confidently linked.
- Gross spending and net spending definitions must be documented in-app.



### FR-09: Categorisation

- Assign one primary category to each included transaction.
- Display uncertain categories for review.
- Preserve user corrections.
- Apply a user's merchant correction to future matching transactions only after explicit confirmation.



### FR-10: Subscription detection

A suspected subscription may be created when there are at least two qualifying payments and evidence such as similar merchant, recurring interval, similar amount, known merchant, or mandate wording.

Display:

- Merchant.
- Recent amount.
- Frequency estimate.
- Latest payment date.
- Estimated next date.
- Supporting transactions.
- Confidence.
- Status.

All machine-generated subscription results must be labelled **suspected** until confirmed by the user.

### FR-11: Dashboard

- Dashboard totals must be internally consistent.
- The selected period must always be visible.
- Empty and partial states must not fabricate insights.
- Charts must have accessible text alternatives.
- The latest analysis timestamp and accuracy disclaimer must be accessible.



### FR-12: Transaction list and detail

- Search and filters must work on local data.
- Detail view must show what the app inferred without exposing full sensitive identifiers.
- Corrections must update all affected totals immediately.



### FR-13: Re-analysis

- Incremental re-analysis must avoid duplicates.
- Existing corrections must survive parser updates where possible.
- If a parser update changes a corrected transaction, user choice takes precedence unless the user resets corrections.



### FR-14: Data deletion

Users must be able to delete:

- Analysed transaction records.
- Cached or imported source content.
- Categorisation history.
- Diagnostics linked to the user where applicable.
- Account and associated data, if accounts exist.

Deletion must not be presented as complete until all in-scope stores have processed it.

### FR-15: Help and support

The app must include:

- A support/contact method.
- Explanation of supported and unsupported message formats.
- Troubleshooting for permission denial and missing transactions.
- A privacy-safe method to report an unsupported SMS format, using redaction and explicit preview before submission.

---



## 10. Information Architecture



### Home

- Selected period.
- Total spending and credits.
- Period comparison, where data exists.
- Monthly trend.
- Category breakdown.
- Subscription summary.
- Recent transactions.
- Refresh action.



### Transactions

- Search.
- Filters and sorting.
- Review-needed section.
- Transaction detail and corrections.



### Subscriptions

- Suspected.
- Confirmed.
- Possibly inactive.
- Dismissed.
- Estimated monthly and annual totals.



### Settings

- Data and privacy.
- SMS permission status.
- Reanalyse.
- Manage correction rules.
- Delete analysed data.
- Account deletion, if applicable.
- Privacy policy.
- Terms of use.
- Support.
- About and version.

---



## 11. Non-Functional Requirements



### 11.1 Performance

- Cached dashboard should become interactive within 2 seconds on supported mid-range devices.
- Initial analysis of 12 months must not block the UI thread.
- Processing must be batched and cancellable.
- Progress must be visible.
- Scans must safely resume or restart after interruption.
- Memory usage must remain bounded for large inboxes.



### 11.2 Reliability and integrity

- Re-analysis must be idempotent.
- A failed scan must not corrupt existing records.
- User corrections must persist.
- Dashboard totals must be reproducible from stored records.
- Database migrations must be reversible or recoverable.
- Parser version must be recorded for debugging without retaining raw SMS.



### 11.3 Security

- Encrypt sensitive local records using platform-supported secure storage and database encryption where appropriate.
- Keep encryption keys in Android Keystore.
- Use TLS for all network traffic.
- Do not log raw SMS, full account numbers, card numbers, balances, transaction references, merchant-level history, or amounts in general analytics.
- Redact sensitive values in crash reports and support diagnostics.
- Use dependency scanning and signed release builds.
- Apply least privilege to backend and analytics systems.
- Maintain an incident-response and breach-notification procedure.



### 11.4 Privacy

- Prefer on-device processing.
- Minimise fields stored and transmitted.
- Discard raw SMS after extraction unless strictly needed and explicitly consented.
- Do not use transaction data for targeted advertising, creditworthiness, insurance, employment, or eligibility decisions.
- Do not sell financial or SMS data.
- Do not train models on user data without a separate, optional, informed consent flow.
- Provide revocation and deletion controls.



### 11.5 Accessibility

- Support scalable text and screen readers.
- Meet applicable contrast requirements.
- Do not rely on colour alone.
- Provide chart summaries.
- Use large, clearly labelled touch targets.
- Use simple language suitable for non-technical users.



### 11.6 Compatibility

- Define a minimum Android API level based on security support and device distribution.
- Test on representative Samsung, Xiaomi/Redmi, Vivo, Oppo/Realme, OnePlus, Motorola, and Pixel devices used in India.
- Test manufacturer-specific permission, battery, and background restrictions.
- Test devices with dual SIM and multiple banking apps.



### 11.7 Observability

Track operational events without financial payloads:

- Scan started/completed/failed.
- Parser version.
- Processing duration and batch count.
- Number of candidate and accepted records as coarse counts.
- Error code and device/app version.
- Database migration status.
- Crash-free sessions.

Never send raw SMS, merchant names, amounts, account identifiers, transaction references, category history, or subscription names to general analytics.

### 11.8 Maintainability

- Institution parsing rules must be independently testable and remotely updateable only if updates cannot execute arbitrary code.
- Maintain a versioned synthetic/redacted regression corpus.
- Require automated tests for every new supported message template.
- Feature flags must not silently expand data collection or permission use.

---



## 12. Edge Cases



### Data and parsing

- Multiple SMS alerts for one transaction.
- Delayed or out-of-order alerts.
- Refund without original transaction in the selected range.
- Reversal and debit both present.
- Partial card/account identifiers change after card replacement.
- Merchant name differs between UPI and bank alert.
- Amount contains commas, decimals, spaces, or non-INR currency.
- SMS lacks a usable date and relies on message timestamp.
- Bank message reports balance but no transaction.
- Failed, declined, pending, or blocked transaction alerts.
- Cash withdrawal followed by ATM reversal.
- Credit-card bill payment mistaken for spending.
- Own-account transfer counted twice across sender and receiver accounts.
- EMI or loan payment mistaken for a subscription.
- Annual subscription with only one historical payment.
- Subscription price changes.
- Merchant aggregator hides the underlying merchant.
- Institution changes sender ID or message template.
- Regional-language or transliterated messages.
- User deletes source messages after analysis.
- Device clock or timezone changes.
- Very large inbox or low-storage device.



### UX and lifecycle

- Permission granted, then revoked during scanning.
- App killed during a scan.
- Device restarted during database migration.
- User selects a period with no transactions.
- User has no SMS inbox or uses an SMS app with unusual behaviour.
- User expects real-time updates but background access is unavailable.
- User reinstalls the app and local data is gone.
- User changes correction rules and expects historical recategorisation.
- User deletes data while a scan is running.



### Commercial and entitlement

- User buys premium and later reinstalls.
- Billing succeeds but entitlement update is delayed.
- Subscription lapses while premium data remains local.
- User requests refund.
- Paywall appears before value is demonstrated.
- Price or feature differs from store listing.

---



## 13. Abuse and Misuse Scenarios

1. **Partner or family surveillance:** Someone installs the app on another person's unlocked phone to inspect spending.
  **Mitigation:** Require device authentication before viewing financial dashboards; do not support remote monitoring; provide visible app presence and permission controls.
2. **Stalkerware-style exfiltration:** A modified build uploads SMS content.
  **Mitigation:** No background export, strict network allow-listing, signed builds, privacy review, integrity checks, and transparent outbound data inventory.
3. **Spoofed financial SMS:** An attacker sends a fake message that appears transactional.
  **Mitigation:** Treat SMS as informational, use sender/template confidence, flag unknown senders, and state that results are not authoritative banking records.
4. **Manipulated totals:** A user or malicious app injects messages to create false financial history.
  **Mitigation:** Confidence scoring, unverified-source labels, and no use for lending, credit, tax, or eligibility decisions.
5. **Sensitive support upload:** User accidentally submits an unredacted message.
  **Mitigation:** On-device redaction, preview, explicit consent, minimal retention, and deletion workflow.
6. **Analytics leakage:** SDK captures screen text or user properties containing financial data.
  **Mitigation:** Disable automatic screen capture, review SDK defaults, use allow-listed events, and test network payloads.
7. **Advertising misuse:** Merchant or category data is used to target ads.
  **Mitigation:** Product policy and technical controls prohibit SMS- or transaction-derived ad targeting.
8. **Credential phishing:** A fake screen asks for bank login, card PIN, OTP, or UPI PIN.
  **Mitigation:** Never request these credentials; repeat this in support content; security review blocks such fields.
9. **Scraping or bulk export:** Malware or another user extracts local financial data.
  **Mitigation:** App sandbox, encryption, optional biometric lock, no unsecured backups, and protected exports.
10. **Misleading subscription claims:** App implies a recurring charge is active or cancelled without verification.
  **Mitigation:** Use suspected/confirmed labels and never display cancellation confirmation without an authorised merchant response.
11. **Review manipulation:** Users are prompted for ratings only after positive outcomes.
  **Mitigation:** Use neutral, policy-compliant rating prompts and never gate features on reviews.
12. **Dark-pattern monetisation:** Difficult cancellation, hidden pricing, or preselected trials.
  **Mitigation:** Clear pricing, renewal terms, restore purchase, manage subscription, and no obstruction of cancellation or deletion.

---



## 14. App Store and Play Store Compliance



## 14.1 Google Play — principal risks



### Risk A: Restricted SMS permission rejection

`READ_SMS` and related permissions are high-risk. Google Play currently identifies **SMS-based money management**, including budget-tracking apps, as a possible exception, subject to declaration and review.

**Requirements:**

- SMS analysis must be the app's documented core function.
- Request only the permissions essential to that function.
- Complete the Permissions Declaration Form.
- Ensure the store listing prominently describes the SMS-based money-management feature.
- Do not include disallowed, undisclosed, or future permission uses in the manifest.
- Do not exfiltrate non-financial or personal SMS history.
- Maintain a no-permission controlled-import fallback until approval is stable.



### Risk B: Prominent disclosure and consent failure

Sensitive data access must be explained in context before the runtime permission prompt. A generic privacy policy is insufficient.

**Required user-facing implementation:**

- Dedicated disclosure screen.
- Plain-language explanation of access, purpose, storage, sharing, and deletion.
- Affirmative action before the system prompt.
- No deceptive or forced consent.



### Risk C: Data safety mismatch

The Play Data safety form must match actual behaviour of the app and every included SDK.

**Mitigation:**

- Maintain a data inventory by SDK and endpoint.
- Test release network traffic.
- Update declarations before any data-flow change.
- Include a privacy policy even if no data leaves the device.



### Risk D: Account deletion non-compliance

If account creation is available, Google requires an in-app deletion path and an external web resource through which deletion can be requested.

**Product decision:** Avoid mandatory accounts in Phase-0. If accounts are introduced, deletion must ship in the same release.

### Risk E: Financial-services representation

The app must not imply that it is a bank, regulated account aggregator, payment provider, lender, or financial adviser.

**Mitigation:**

- Position as informational expense organisation.
- Do not execute transactions.
- Include accuracy and completeness disclaimers.
- Avoid logos, wording, or screens that imply affiliation with banks unless authorised.



### Risk F: SDK and advertising practices

Financial and SMS data is personal and sensitive. Any SDK collection beyond the declared purpose can trigger rejection or enforcement.

**Mitigation:**

- Prefer privacy-minimal analytics.
- No ad targeting based on SMS or transactions.
- Do not include SDKs that collect SMS content, screen content, financial identifiers, or unnecessary device identifiers.

---



## 14.2 Apple App Store — principal risks

The Phase-0 product is Android-first because iOS does not provide a general-purpose API for third-party apps to scan the user's SMS inbox. An iOS build must use another user-controlled source and must not claim SMS-inbox analysis.

### Risk A: Inaccurate iOS feature claims

An iOS listing that promises automatic SMS scanning would be misleading or technically unavailable.

**Mitigation:**

- Do not submit the Android SMS feature as an iOS-equivalent feature.
- Define a separate iOS value proposition based on manual entry, email, receipt, file import, or regulated bank integration.



### Risk B: Privacy-policy and data disclosure mismatch

Apple requires a privacy-policy link in App Store Connect and in the app, plus accurate App Privacy disclosures covering the app and third-party SDKs.

### Risk C: Sensitive financial-service review

Apple applies additional scrutiny to apps handling sensitive financial information or operating in regulated fields, and may expect submission by an appropriate legal entity.

**Mitigation:**

- Submit under a registered organisation rather than an individual account before iOS expansion.
- Clearly state the app is informational and does not provide regulated transactions or advice.
- Provide detailed App Review notes, test instructions, and sample data.



### Risk D: Account deletion

If an iOS version supports account creation, users must be able to initiate account deletion inside the app.

### Risk E: Digital subscription billing

Premium digital features sold inside the iOS app must use Apple's In-App Purchase unless a documented exception applies. Google Play Billing should be used for equivalent digital features distributed through Google Play.

### Risk F: Sign-in options

If third-party social login becomes the primary sign-in method on iOS, the app must satisfy Apple's login-service requirements, including an equivalent privacy-preserving option where applicable.

---



## 15. Required User-Facing Features to Reduce Rejection Risk

The following are mandatory for the applicable store build:

1. Privacy policy accessible before permission and from Settings.
2. Prominent SMS-use disclosure immediately before the Android permission prompt.
3. Clear consent action.
4. Permission-denied state with no repeated nagging.
5. System-settings shortcut for later permission changes.
6. Sample dashboard or useful explanation without granting permission.
7. Clear statement that results are estimates and not bank records.
8. Supported-data and limitations help page.
9. In-app deletion of analysed data.
10. In-app account deletion if accounts exist.
11. External account-deletion web page for Google Play if accounts exist.
12. Support/contact page.
13. Restore-purchase and manage-subscription links if premium subscriptions exist.
14. Transparent pricing, renewal, trial, and cancellation language.
15. No bank PIN, OTP, password, CVV, or UPI PIN collection.
16. Visible review and correction controls for uncertain financial results.
17. Biometric/device-lock option before displaying financial information.
18. Privacy-safe error-report flow with redaction preview.
19. Legal entity and developer identity consistent across store listing and legal pages.
20. Accurate store screenshots that do not expose real user financial data.

---



## 16. Required Legal and Public Pages



### Required before Phase-0 public launch

1. **Privacy Policy**
  - Legal entity and contact details.
  - Data categories accessed or collected.
  - SMS access purpose.
  - On-device versus server processing.
  - Third-party SDKs and processors.
  - Data use, sharing, retention, security, deletion, and consent withdrawal.
  - Children's privacy position.
  - Cross-border transfers, if any.
  - User rights and grievance/contact process.
2. **Terms of Use / End User Terms**
  - Informational nature of results.
  - No guarantee of completeness or accuracy.
  - No financial, tax, legal, or investment advice.
  - User responsibilities.
  - Acceptable use.
  - Intellectual property.
  - Warranty and liability limitations, subject to applicable law.
  - Suspension and termination.
3. **Data Deletion Page**
  - Required externally if accounts exist on Google Play.
  - Clearly identifies SpendSMS and the developer.
  - Explains how to request deletion.
  - Lists data deleted and any lawful retention.
4. **Support / Contact Page**
  - Working support channel.
  - Response expectations.
  - Troubleshooting and escalation.
5. **Security and Responsible Disclosure Page**
  - Method for reporting vulnerabilities.
  - Prohibition on sending live financial data unnecessarily.



### Recommended

1. **Data Processing and Subprocessor List**.
2. **Cookie Policy**, if the website uses non-essential cookies.
3. **Refund and Subscription Policy**, if premium plans are offered.
4. **Children's Privacy Notice** or explicit age restriction where applicable.
5. **Accessibility Statement**.
6. **Grievance Officer / India privacy contact page**, based on applicable Indian legal advice and business scale.

All pages must be public, mobile-readable, consistent with in-app behaviour, and updated whenever data practices change. Store-form answers must never be broader or narrower than the actual implementation.

---



## 17. India Legal and Regulatory Considerations

This PRD is not legal advice. Before public launch, counsel should review the product under applicable Indian privacy, consumer-protection, cybersecurity, and financial-services rules.

Product principles for legal risk reduction:

- Obtain clear, purpose-specific consent for SMS analysis.
- Collect only data necessary for user-requested money management.
- Provide notice, revocation, correction, and deletion mechanisms.
- Maintain reasonable security safeguards.
- Avoid representing the product as a regulated financial institution or authorised bank-data source.
- Do not use SMS-derived information for credit, insurance, employment, or other high-impact decisions.
- Establish a documented incident-response and user-notification process.
- Use contracts and due diligence for processors and SDK providers.
- Define retention schedules rather than storing data indefinitely.

---



## Acceptance Criteria for Phase-0

Phase-0 is ready for controlled public release when:

1. A user can understand the product before granting access.
2. The permission flow matches the approved Google Play declaration.
3. The app can analyse a chosen period or use the approved import fallback.
4. Personal and irrelevant messages are excluded from user-visible results.
5. Supported alerts produce amount, date, direction, and institution with acceptable accuracy.
6. Duplicate alerts do not inflate totals beyond the defined threshold.
7. Transfers, refunds, reversals, and failed transactions have safe handling.
8. Users can correct every transaction classification that affects totals.
9. Dashboard calculations update immediately and remain consistent.
10. Suspected subscriptions show evidence and uncertainty.
11. Users can revoke access and delete analysed data.
12. Account deletion works in-app and on the web if accounts exist.
13. No raw SMS or financial payload is present in analytics or crash reporting.
14. The app survives interruption, low-data, and no-data states.
15. Privacy policy, terms, support, and deletion pages are live and consistent.
16. Store listing, screenshots, permission declaration, and Data safety/App Privacy disclosures match the release build.
17. App review notes provide reviewers with a clear test path and non-sensitive sample data.
18. Security and privacy sign-off is complete.

---



## 21. Delivery Plan



### Milestone 0 — Policy and parser feasibility

- Build synthetic/redacted SMS corpus.
- Implement parser harness and confidence model.
- Prototype dashboard with synthetic data.
- Prepare Google Play permission declaration narrative and evidence.
- Validate controlled-import fallback.
- Decide whether Phase-0 can ship without accounts.



### Milestone 1 — Internal Phase-0 build

- Onboarding and disclosure.
- Permission/import flow.
- Local database and encryption.
- Transaction parser and categorisation.
- Dashboard, list, and corrections.
- Deletion and support flows.
- Privacy-safe telemetry.



### Milestone 2 — Closed beta

- Expand bank and UPI templates.
- Measure accuracy on consented redacted samples.
- Test trust messaging and onboarding.
- Validate duplicates and subscriptions.
- Perform security, privacy, accessibility, and device testing.



### Milestone 3 — Store submission

- Finalise legal pages.
- Complete Permissions Declaration and Data safety form.
- Prepare store metadata and reviewer notes.
- Release gradually.
- Monitor crashes, parser failures, reviews, and support issues without collecting financial payloads.

---



## 22. Key Risks and Mitigations


| Risk                                          | Impact                                | Mitigation                                                                                                |
| --------------------------------------------- | ------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Google Play rejects SMS permission            | Core automatic experience cannot ship | Treat approval as a release gate; retain controlled import; remove restricted permissions if not approved |
| Users distrust SMS access                     | Low activation                        | Local-first processing, sample dashboard, clear disclosure, no bank credentials, deletion controls        |
| Message formats change                        | Missed or incorrect transactions      | Versioned templates, confidence scores, regression corpus, privacy-safe failed-format reporting           |
| Duplicate or transfer errors                  | Inflated spending                     | Multi-signal deduplication, review queue, user correction                                                 |
| False subscription claims                     | Loss of trust                         | Label as suspected, show evidence, require confirmation                                                   |
| SDK leaks financial data                      | Severe privacy and store risk         | Minimal SDK set, payload testing, redaction, no screen capture                                            |
| App is treated as regulated financial service | Review or legal delay                 | Informational positioning, legal-entity submission, no transactions/advice, legal review                  |
| Monetisation harms trust                      | Poor retention and reviews            | Freemium, value before paywall, no SMS-targeted ads, transparent billing                                  |
| iOS expansion duplicates Android promise      | Rejection or unusable product         | Separate iOS data-source strategy; never promise inbox scanning                                           |
| Spoofed SMS creates false data                | Misleading insights                   | Confidence labels, unknown-sender warnings, non-authoritative disclaimer                                  |


---



## 23. Open Decisions Before Engineering Lock

1. Will all parsing and categorisation remain on-device in Phase-0?
2. Will Phase-0 have no user accounts?
3. What exact SMS permissions will be declared?
4. What controlled-import mechanism is acceptable as fallback?
5. Which institutions and formats are officially supported at launch?
6. What minimum confidence permits inclusion in dashboard totals?
7. Will low-confidence records be excluded or shown in a review queue?
8. How will own-account transfers be identified without collecting account ownership data?
9. What is the minimum supported Android version?
10. Will optional biometric lock be mandatory for launch or fast-follow?
11. Which premium hypothesis, if any, will be tested in Phase-0?
12. What legal entity will publish the application?
13. Which analytics and crash SDKs can meet the no-financial-payload requirement?
14. What retention period applies to diagnostics and support submissions?
15. What evidence and reviewer instructions are needed for the Google Play permission declaration?

---



## 24. Recommended Product Decisions

1. **Ship Phase-0 without mandatory accounts.** This lowers privacy, deletion, security, and onboarding complexity.
2. **Process raw SMS on-device and discard it after extraction.** Store only normalised records required for the dashboard.
3. **Treat Google Play SMS approval as a hard gate, not an assumption.** Keep controlled import functional.
4. **Do not become the default SMS application solely to access messages.** That would expand scope and weaken trust.
5. **Make correction and review central to the product.** Accuracy will never be perfect across all institutions.
6. **Use “suspected subscription” language until user confirmation.**
7. **Avoid advertising in Phase-0.** Trust and retention are more important, and transaction-derived targeting is unacceptable.
8. **Use freemium monetisation after the user sees a useful dashboard.**
9. **Submit under an organisation/legal entity before entering Apple's sensitive financial-data review path.**
10. **Design iOS as a distinct Phase-2 product input strategy rather than a port of Android SMS scanning.**

---



## 25. Official Policy References Reviewed

- Google Play: Use of SMS or Call Log permission groups.
- Google Play: Permissions Declaration requirements.
- Google Play: User Data, Privacy Policy, Data safety, and Account Deletion requirements.
- Apple: App Review Guidelines, especially privacy, financial services, metadata accuracy, billing, and login requirements.
- Apple: App Privacy disclosures and in-app account deletion guidance.

Policy requirements can change. Revalidate all store policies immediately before implementation lock and again before each public submission.
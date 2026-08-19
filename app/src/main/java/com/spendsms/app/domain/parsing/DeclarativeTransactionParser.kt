package com.spendsms.app.domain.parsing

import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.TransactionCandidate
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.sms.RawSmsMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates Prompt-6 declarative institution/sender/transaction patterns.
 */
@Singleton
class DeclarativeTransactionParser @Inject constructor(
    private val confidencePolicy: ConfidencePolicy,
) : TransactionParser {

    private val compiledPatterns = ConcurrentHashMap<String, Pattern>()

    override fun parse(message: RawSmsMessage, rules: DeclarativeParserRules): ParseResult {
        return try {
            evaluate(message, rules)
        } catch (e: Exception) {
            ParseResult.Unsupported(
                reason = ParseFailureReason.RULE_RUNTIME_ERROR,
                detail = e::class.java.simpleName,
            )
        }
    }

    private fun evaluate(message: RawSmsMessage, rules: DeclarativeParserRules): ParseResult {
        val senderMatchedInstitutions = rules.institutions.filter { institution ->
            SenderPatternMatcher.matchesAny(message.sender, institution.senderPatterns)
        }
        val institutionsToTry = senderMatchedInstitutions.ifEmpty { rules.institutions }
        val senderMatched = senderMatchedInstitutions.isNotEmpty()

        val extractions = mutableListOf<Extraction>()
        for (institution in institutionsToTry) {
            val institutionSenderMatched =
                SenderPatternMatcher.matchesAny(message.sender, institution.senderPatterns)
            for (pattern in institution.transactionPatterns) {
                val extraction = matchPattern(
                    message = message,
                    institution = institution,
                    pattern = pattern,
                    senderMatched = institutionSenderMatched || senderMatched,
                ) ?: continue
                extractions += extraction
            }
        }

        if (extractions.isEmpty()) {
            return ParseResult.Unsupported(
                reason = if (senderMatched) {
                    ParseFailureReason.UNSUPPORTED_TEMPLATE
                } else {
                    ParseFailureReason.NO_SENDER_OR_PATTERN_MATCH
                },
            )
        }

        val unique = dedupeEquivalent(extractions)
        if (unique.size > 1) {
            return ParseResult.Ambiguous(
                detail = "Multiple non-equivalent pattern matches (${unique.size})",
            )
        }

        val best = unique.single()
        if (best.confidence.value < confidencePolicy.minimumEmitConfidence) {
            return ParseResult.Unsupported(
                reason = ParseFailureReason.LOW_CONFIDENCE,
                detail = "confidence=${best.confidence.value}",
            )
        }

        return ParseResult.Success(best.toCandidate(message, rules))
    }

    private fun matchPattern(
        message: RawSmsMessage,
        institution: InstitutionRuleSet,
        pattern: TransactionPatternRule,
        senderMatched: Boolean,
    ): Extraction? {
        val compiled = compiledPattern(pattern.regex) ?: return null
        val matcher = compiled.matcher(message.body)
        if (!matcher.find()) return null

        fun groupOrNull(name: String?): String? {
            if (name.isNullOrBlank()) return null
            return try {
                matcher.group(name)?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        val amountRaw = groupOrNull(pattern.amountGroup)
            ?: return null
        val currencyRaw = groupOrNull(pattern.currencyGroup)
        val money = AmountParser.parseMoney(amountRaw, currencyRaw) ?: return null
        val merchant = groupOrNull(pattern.merchantGroup)
        val referenceRaw = groupOrNull(pattern.referenceGroup)
        val dateRaw = groupOrNull(pattern.dateGroup)
        val explicitDate = SmsDateParser.parseOrNull(dateRaw)
        val timestamp = explicitDate ?: message.receivedAt
        val explicitCurrency = currencyRaw != null && AmountParser.parseCurrency(currencyRaw) != null

        val confidence = confidencePolicy.score(
            senderMatched = senderMatched,
            hasMerchant = merchant != null,
            hasReference = referenceRaw != null,
            hasExplicitCurrency = explicitCurrency,
            hasExplicitDate = explicitDate != null,
        )

        return Extraction(
            patternId = pattern.id,
            institution = institution.institution,
            amount = money,
            direction = pattern.type,
            merchantRaw = merchant,
            referenceRaw = referenceRaw,
            timestamp = timestamp,
            paymentMethod = PaymentMethodClassifier.classify(message.body),
            confidence = confidence,
            usedExplicitCurrency = explicitCurrency,
            currencyDefaulted = money.currency == CurrencyCode.INR && !explicitCurrency,
        )
    }

    private fun dedupeEquivalent(extractions: List<Extraction>): List<Extraction> {
        val groups = linkedMapOf<String, Extraction>()
        for (extraction in extractions) {
            val key = extraction.equivalenceKey()
            val existing = groups[key]
            if (existing == null || extraction.confidence.value > existing.confidence.value) {
                groups[key] = extraction
            }
        }
        // Collapse only exact equivalents; different keys remain for ambiguity detection.
        return groups.values.toList()
    }

    private data class Extraction(
        val patternId: String,
        val institution: String,
        val amount: Money,
        val direction: TransactionDirection,
        val merchantRaw: String?,
        val referenceRaw: String?,
        val timestamp: EpochMillis,
        val paymentMethod: PaymentMethod,
        val confidence: com.spendsms.app.domain.model.Confidence,
        val usedExplicitCurrency: Boolean,
        val currencyDefaulted: Boolean,
    ) {
        fun equivalenceKey(): String =
            listOf(
                amount.amountMinorUnits.toString(),
                amount.currency.code,
                direction.name,
                merchantRaw.orEmpty().lowercase(),
                timestamp.toEpochMillis.toString(),
            ).joinToString("|")

        fun toCandidate(
            message: RawSmsMessage,
            rules: DeclarativeParserRules,
        ): TransactionCandidate =
            TransactionCandidate(
                sourceMessageHash = ParsingHashes.sourceMessageHash(
                    sourceMessageId = message.sourceMessageId,
                    sender = message.sender,
                    receivedAtEpochMillis = message.receivedAt.toEpochMillis,
                ),
                amount = amount,
                transactionTimestamp = timestamp,
                merchantRaw = merchantRaw,
                institution = institution,
                maskedAccount = null,
                direction = direction,
                paymentMethod = paymentMethod,
                referenceHash = referenceRaw?.let { ParsingHashes.referenceHash(it) },
                confidence = confidence,
                parserVersion = rules.parserVersion,
            )
    }

    private fun compiledPattern(regex: String): Pattern? {
        compiledPatterns[regex]?.let { return it }
        return try {
            Pattern.compile(regex, Pattern.CASE_INSENSITIVE or Pattern.DOTALL).also {
                compiledPatterns.putIfAbsent(regex, it)
            }
        } catch (_: PatternSyntaxException) {
            null
        }
    }
}

package com.spendsms.app.application.analysis

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.categorisation.CategorisationEngine
import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.merchant.MerchantNormalizer
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CorrectionId
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.TransactionCandidate
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.SpendingRole
import com.spendsms.app.domain.semantics.TransactionSemantics
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Test

class TransactionClassificationServiceTest {

    private val service = TransactionClassificationService(
        merchantNormalizer = MerchantNormalizer(),
        categorisationEngine = CategorisationEngine(),
        semantics = TransactionSemantics(),
        userCorrectionRepository = mockk(relaxed = true),
    )

    @Test
    fun debitSwiggy_normalizesCategorizesAndCountsAsSpending() {
        val candidate = candidate(
            merchantRaw = "PAYTM*SWIGGY",
            direction = TransactionDirection.DEBIT,
            confidence = 0.92,
        )
        val classified = service.classifyWithCorrections(candidate)
        assertThat(classified.merchant!!.key.value).isEqualTo("swiggy")
        assertThat(classified.merchant!!.displayName).isEqualTo("Swiggy")
        assertThat(classified.categoryId).isEqualTo(SystemCategories.FOOD_AND_DINING)
        assertThat(classified.spending.role).isEqualTo(SpendingRole.GROSS_EXPENSE)
        assertThat(classified.confidence.value).isEqualTo(0.92)
        assertThat(classified.parserVersion).isEqualTo(candidate.parserVersion)
        assertThat(classified.candidate).isEqualTo(candidate)
    }

    @Test
    fun unknownMerchant_otherCategoryStillSpends() {
        val classified = service.classifyWithCorrections(
            candidate(merchantRaw = "LOCAL KIRANA-12"),
        )
        assertThat(classified.merchant!!.key.value).isEqualTo("local kirana 12")
        assertThat(classified.categoryId).isEqualTo(SystemCategories.OTHER)
        assertThat(classified.spending.includeInGrossSpending).isTrue()
    }

    @Test
    fun refund_isIncomeCategoryAndRefundsBucket() {
        val classified = service.classifyWithCorrections(
            candidate(
                merchantRaw = "AMAZON",
                direction = TransactionDirection.REFUND,
                paymentMethod = PaymentMethod.CARD,
            ),
        )
        assertThat(classified.categoryId).isEqualTo(SystemCategories.INCOME_AND_REFUNDS)
        assertThat(classified.spending.role).isEqualTo(SpendingRole.REFUND)
        assertThat(classified.direction).isEqualTo(TransactionDirection.REFUND)
    }

    @Test
    fun transfer_excludedFromSpending() {
        val classified = service.classifyWithCorrections(
            candidate(
                merchantRaw = "OWN AC XX7788",
                direction = TransactionDirection.TRANSFER,
                paymentMethod = PaymentMethod.NET_BANKING,
            ),
        )
        assertThat(classified.transferStatus).isEqualTo(TransferStatus.SUSPECTED)
        assertThat(classified.categoryId).isEqualTo(SystemCategories.TRANSFERS)
        assertThat(classified.spending.excludedFromSpendingTotals).isTrue()
    }

    @Test
    fun futureMerchantAndCategoryCorrectionsTakePrecedence() {
        val candidate = candidate(merchantRaw = "SWIGGY")
        val now = EpochMillis.of(10L)
        val classified = service.classifyWithCorrections(
            candidate = candidate,
            futureCorrections = listOf(
                correction(CorrectionField.MERCHANT, "Swiggy Eats", now, MerchantKey.of("swiggy")),
                correction(
                    CorrectionField.CATEGORY,
                    SystemCategories.ENTERTAINMENT.value,
                    now,
                    MerchantKey.of("swiggy"),
                ),
            ),
        )
        assertThat(classified.merchant!!.displayName).isEqualTo("Swiggy Eats")
        assertThat(classified.merchant!!.key.value).isEqualTo("swiggy")
        assertThat(classified.categoryId).isEqualTo(SystemCategories.ENTERTAINMENT)
    }

    @Test
    fun notATransactionCorrectionExcludesSpending() {
        val classified = service.classifyWithCorrections(
            candidate = candidate(merchantRaw = "SWIGGY"),
            futureCorrections = listOf(
                correction(
                    field = CorrectionField.NOT_A_TRANSACTION,
                    newValue = "true",
                    at = EpochMillis.of(1L),
                    merchantKey = MerchantKey.of("swiggy"),
                ),
            ),
        )
        assertThat(classified.spending.role).isEqualTo(SpendingRole.EXCLUDED)
        assertThat(classified.confidence.value).isEqualTo(0.85)
    }

    @Test
    fun classify_loadsFutureRulesFromRepository() = kotlinx.coroutines.runBlocking {
        val repo = mockk<com.spendsms.app.application.port.UserCorrectionRepository>()
        coEvery {
            repo.findFutureRules(MerchantKey.of("swiggy"), CorrectionField.CATEGORY)
        } returns listOf(
            correction(
                CorrectionField.CATEGORY,
                SystemCategories.GROCERIES.value,
                EpochMillis.of(1L),
                MerchantKey.of("swiggy"),
            ),
        )
        coEvery {
            repo.findFutureRules(MerchantKey.of("swiggy"), match { it != CorrectionField.CATEGORY })
        } returns emptyList()

        val wired = TransactionClassificationService(
            merchantNormalizer = MerchantNormalizer(),
            categorisationEngine = CategorisationEngine(),
            semantics = TransactionSemantics(),
            userCorrectionRepository = repo,
        )
        val classified = wired.classify(candidate(merchantRaw = "SWIGGY"))
        assertThat(classified.categoryId).isEqualTo(SystemCategories.GROCERIES)
    }

    private fun candidate(
        merchantRaw: String? = "SWIGGY",
        direction: TransactionDirection = TransactionDirection.DEBIT,
        paymentMethod: PaymentMethod = PaymentMethod.UPI,
        confidence: Double = 0.85,
    ): TransactionCandidate = TransactionCandidate(
        sourceMessageHash = "hash-1",
        amount = Money.ofMinorUnits(12_500L, CurrencyCode.INR),
        transactionTimestamp = EpochMillis.of(1_700_000_000_000L),
        merchantRaw = merchantRaw,
        institution = "Example Bank",
        maskedAccount = "XX1234",
        direction = direction,
        paymentMethod = paymentMethod,
        referenceHash = "ref",
        confidence = Confidence.of(confidence),
        parserVersion = ParserVersion.of("bundled-2026.08.12.0"),
    )

    private fun correction(
        field: CorrectionField,
        newValue: String,
        at: EpochMillis,
        merchantKey: MerchantKey,
    ): UserCorrection = UserCorrection(
        id = CorrectionId.of("c-${field.name}"),
        transactionId = TransactionId.of("t-prior"),
        field = field,
        oldValue = "old",
        newValue = newValue,
        applyToFuture = true,
        merchantMatchKey = merchantKey,
        createdAt = at,
        updatedAt = at,
    )
}

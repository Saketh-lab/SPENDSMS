package com.spendsms.app.domain.subscriptions

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.SubscriptionFrequency
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.semantics.TransactionSemantics
import org.junit.Test

class SubscriptionDetectorTest {

    private val day = SubscriptionDetectionPolicy.DAY_MILLIS
    private val detector = SubscriptionDetector(
        semantics = TransactionSemantics(),
        policy = SubscriptionDetectionPolicy.Phase0,
    )
    private val now = EpochMillis.of(1_700_000_000_000L)

    @Test
    fun monthlyRecurring_sameMerchant_isSuspected() {
        val txs = listOf(
            debit("t1", "netflix", 499_00L, now.toEpochMillis - 60 * day),
            debit("t2", "netflix", 499_00L, now.toEpochMillis - 30 * day),
            debit("t3", "netflix", 499_00L, now.toEpochMillis),
        )
        val found = detector.detect(txs, now = now) { SubscriptionId.of("sub-${it.value}") }
        assertThat(found).hasSize(1)
        val sub = found.single().subscription
        assertThat(sub.frequency).isEqualTo(SubscriptionFrequency.MONTHLY)
        assertThat(sub.status).isEqualTo(SubscriptionStatus.SUSPECTED)
        assertThat(sub.estimatedAmount?.amountMinorUnits).isEqualTo(499_00L)
        assertThat(sub.evidenceTransactionIds).hasSize(3)
    }

    @Test
    fun irregularVaryingAmounts_areNotSubscriptions() {
        val txs = listOf(
            debit("t1", "swiggy", 250_00L, now.toEpochMillis - 3 * day),
            debit("t2", "swiggy", 980_00L, now.toEpochMillis - 1 * day),
            debit("t3", "swiggy", 120_00L, now.toEpochMillis),
        )
        val found = detector.detect(txs, now = now) { SubscriptionId.of("x") }
        assertThat(found).isEmpty()
    }

    @Test
    fun singlePayment_isNotSubscription() {
        val found = detector.detect(
            listOf(debit("t1", "spotify", 119_00L, now.toEpochMillis)),
            now = now,
        ) { SubscriptionId.of("x") }
        assertThat(found).isEmpty()
    }

    @Test
    fun transfersAndConfirmedDuplicates_areIgnored() {
        val txs = listOf(
            debit("t1", "rent", 15_000_00L, now.toEpochMillis - 30 * day),
            debit(
                id = "t2",
                merchant = "rent",
                amount = 15_000_00L,
                at = now.toEpochMillis,
                transferStatus = TransferStatus.CONFIRMED,
            ),
            debit(
                id = "t3",
                merchant = "rent",
                amount = 15_000_00L,
                at = now.toEpochMillis - 60 * day,
                duplicateStatus = DuplicateStatus.CONFIRMED,
            ),
        )
        assertThat(detector.detect(txs, now = now) { SubscriptionId.of("x") }).isEmpty()
    }

    @Test
    fun excludedTransactionIds_areIgnored() {
        val txs = listOf(
            debit("t1", "adobe", 1_599_00L, now.toEpochMillis - 30 * day),
            debit("t2", "adobe", 1_599_00L, now.toEpochMillis),
        )
        val found = detector.detect(
            transactions = txs,
            excludedTransactionIds = setOf(TransactionId.of("t2")),
            now = now,
        ) { SubscriptionId.of("x") }
        assertThat(found).isEmpty()
    }

    @Test
    fun staleRecurring_isPossiblyInactive() {
        val last = now.toEpochMillis - 90 * day
        val txs = listOf(
            debit("t1", "gym", 999_00L, last - 60 * day),
            debit("t2", "gym", 999_00L, last - 30 * day),
            debit("t3", "gym", 999_00L, last),
        )
        val found = detector.detect(txs, now = now) { SubscriptionId.of("gym-sub") }
        assertThat(found.single().subscription.status)
            .isEqualTo(SubscriptionStatus.POSSIBLY_INACTIVE)
    }

    private fun debit(
        id: String,
        merchant: String,
        amount: Long,
        at: Long,
        transferStatus: TransferStatus = TransferStatus.NONE,
        duplicateStatus: DuplicateStatus = DuplicateStatus.NONE,
    ): Transaction = Transaction(
        id = TransactionId.of(id),
        sourceMessageHash = "hash-$id",
        fingerprint = TransactionFingerprint.of("fp-$id"),
        timestamp = EpochMillis.of(at),
        amount = Money.ofMinorUnits(amount, CurrencyCode.INR),
        merchant = Merchant(
            key = MerchantKey.of(merchant),
            displayName = merchant.replaceFirstChar { it.uppercase() },
        ),
        institution = "BANK",
        maskedAccount = null,
        referenceHash = null,
        direction = TransactionDirection.DEBIT,
        paymentMethod = PaymentMethod.UPI,
        categoryId = SystemCategories.SUBSCRIPTIONS,
        confidence = Confidence.of(0.9),
        parserVersion = ParserVersion.of("v1"),
        duplicateStatus = duplicateStatus,
        possibleDuplicateOf = null,
        transferStatus = transferStatus,
        isUserConfirmed = false,
        createdAt = EpochMillis.of(at),
        updatedAt = EpochMillis.of(at),
    )
}

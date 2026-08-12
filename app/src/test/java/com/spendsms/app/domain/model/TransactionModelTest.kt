package com.spendsms.app.domain.model

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.merchant.Merchant
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionModelTest {

    private val now = EpochMillis.of(1_725_000_000_000L)

    @Test
    fun candidate_rejectsBlankSourceHash() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionCandidate(
                sourceMessageHash = " ",
                amount = Money.zero(),
                transactionTimestamp = now,
                merchantRaw = "STORE",
                institution = "Example Bank",
                maskedAccount = "XX1234",
                direction = TransactionDirection.DEBIT,
                paymentMethod = PaymentMethod.UPI,
                referenceHash = "refhash",
                confidence = Confidence.of(0.9),
                parserVersion = ParserVersion.of("2026.08.10.1"),
            )
        }
    }

    @Test
    fun candidate_doesNotExposeSmsBodyField() {
        val fields = TransactionCandidate::class.java.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("body")
        assertThat(fields).doesNotContain("rawSms")
        assertThat(fields).doesNotContain("smsBody")
    }

    @Test
    fun transaction_rejectsPossibleDuplicateWhenStatusNone() {
        assertThrows(IllegalArgumentException::class.java) {
            sampleTransaction(
                duplicateStatus = DuplicateStatus.NONE,
                possibleDuplicateOf = TransactionId.of("other"),
            )
        }
    }

    @Test
    fun transaction_allowsSuspectedDuplicateLink() {
        val tx = sampleTransaction(
            duplicateStatus = DuplicateStatus.SUSPECTED,
            possibleDuplicateOf = TransactionId.of("other"),
        )
        assertThat(tx.possibleDuplicateOf?.value).isEqualTo("other")
        assertThat(tx.duplicateStatus).isEqualTo(DuplicateStatus.SUSPECTED)
        assertThat(tx.transferStatus).isEqualTo(TransferStatus.NONE)
    }

    @Test
    fun transaction_rejectsUpdatedBeforeCreated() {
        assertThrows(IllegalArgumentException::class.java) {
            sampleTransaction(
                createdAt = EpochMillis.of(20L),
                updatedAt = EpochMillis.of(10L),
            )
        }
    }

    @Test
    fun directionAndPaymentEnums_coverApprovedPhase0Cases() {
        assertThat(TransactionDirection.entries.toSet()).containsAtLeast(
            TransactionDirection.DEBIT,
            TransactionDirection.CREDIT,
            TransactionDirection.REFUND,
            TransactionDirection.REVERSAL,
            TransactionDirection.TRANSFER,
        )
        assertThat(PaymentMethod.entries.toSet()).containsAtLeast(
            PaymentMethod.UPI,
            PaymentMethod.CARD,
            PaymentMethod.ATM,
            PaymentMethod.UNKNOWN,
        )
    }

    private fun sampleTransaction(
        duplicateStatus: DuplicateStatus = DuplicateStatus.NONE,
        possibleDuplicateOf: TransactionId? = null,
        createdAt: EpochMillis = now,
        updatedAt: EpochMillis = now,
    ): Transaction = Transaction(
        id = TransactionId.of("tx-1"),
        sourceMessageHash = "hash-1",
        fingerprint = TransactionFingerprint.of("fp-1"),
        timestamp = now,
        amount = Money.ofMinorUnits(250_00L, CurrencyCode.INR),
        merchant = Merchant(
            key = MerchantKey.of("store"),
            displayName = "Store",
            rawNormalized = "STORE",
        ),
        institution = "Example Bank",
        maskedAccount = "XX1234",
        referenceHash = "ref",
        direction = TransactionDirection.DEBIT,
        paymentMethod = PaymentMethod.UPI,
        categoryId = SystemCategories.OTHER,
        confidence = Confidence.of(0.95),
        parserVersion = ParserVersion.of("2026.08.10.1"),
        duplicateStatus = duplicateStatus,
        possibleDuplicateOf = possibleDuplicateOf,
        transferStatus = TransferStatus.NONE,
        isUserConfirmed = false,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

class CategoryTest {

    @Test
    fun systemCategories_seedContainsPrdMinimumSet() {
        val seeded = SystemCategories.seed(EpochMillis.of(1L))
        assertThat(seeded).hasSize(16)
        assertThat(seeded.map { it.name }).containsAtLeast(
            "Food and dining",
            "Groceries",
            "Subscriptions",
            "Other",
        )
        assertThat(seeded.all { it.isSystemCategory }).isTrue()
        assertThat(seeded.map { it.id }.toSet()).hasSize(16)
    }
}

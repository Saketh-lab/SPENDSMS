package com.spendsms.app.data.room.mapper

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
import com.spendsms.app.domain.model.SystemCategories
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import org.junit.Test

class RoomMapperTest {

    @Test
    fun transaction_roundTripsWithoutSmsBody() {
        val now = EpochMillis.of(1_725_000_000_000L)
        val domain = Transaction(
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
            categoryId = SystemCategories.GROCERIES,
            confidence = Confidence.of(0.91),
            parserVersion = ParserVersion.of("2026.08.10.1"),
            duplicateStatus = DuplicateStatus.NONE,
            possibleDuplicateOf = null,
            transferStatus = TransferStatus.NONE,
            isUserConfirmed = false,
            createdAt = now,
            updatedAt = now,
        )

        val entity = domain.toEntity()
        val restored = entity.toDomain()

        assertThat(restored).isEqualTo(domain)
        assertThat(entity.javaClass.declaredFields.map { it.name }).doesNotContain("body")
        assertThat(entity.sourceMessageHash).isEqualTo("hash-1")
    }

    @Test
    fun category_roundTrips() {
        val category = SystemCategories.seed(EpochMillis.of(10L)).first()
        assertThat(category.toEntity().toDomain()).isEqualTo(category)
    }
}

package com.spendsms.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun validMoney_isAccepted() {
        val money = Money.ofMinorUnits(12_545L, CurrencyCode.INR)
        assertThat(money.amountMinorUnits).isEqualTo(12_545L)
        assertThat(money.currency).isEqualTo(CurrencyCode.INR)
    }

    @Test
    fun zeroMoney_isAccepted() {
        assertThat(Money.zero().amountMinorUnits).isEqualTo(0L)
    }

    @Test
    fun negativeMoney_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.ofMinorUnits(-1L, CurrencyCode.INR)
        }
    }

    @Test
    fun equality_isByValue() {
        val a = Money.ofMinorUnits(100L, CurrencyCode.INR)
        val b = Money.ofMinorUnits(100L, CurrencyCode.INR)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }
}

class CurrencyCodeTest {

    @Test
    fun inr_isValid() {
        assertThat(CurrencyCode.of("inr").code).isEqualTo("INR")
        assertThat(CurrencyCode.INR.code).isEqualTo("INR")
    }

    @Test
    fun invalidCurrency_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.of("RUPEE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.of("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyCode.of("IN")
        }
    }
}

class ConfidenceTest {

    @Test
    fun bounds_areEnforced() {
        assertThat(Confidence.of(0.0).value).isEqualTo(0.0)
        assertThat(Confidence.of(1.0).value).isEqualTo(1.0)
        assertThat(Confidence.of(0.42).value).isEqualTo(0.42)
    }

    @Test
    fun outOfBounds_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Confidence.of(-0.01)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Confidence.of(1.01)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Confidence.of(Double.NaN)
        }
    }
}

class EpochMillisTest {

    @Test
    fun nonNegative_isAccepted() {
        assertThat(EpochMillis.of(0L).toEpochMillis).isEqualTo(0L)
        assertThat(EpochMillis.of(1_725_000_000_000L).toEpochMillis)
            .isEqualTo(1_725_000_000_000L)
    }

    @Test
    fun negative_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EpochMillis.of(-1L)
        }
    }

    @Test
    fun compareTo_ordersChronologically() {
        val earlier = EpochMillis.of(10L)
        val later = EpochMillis.of(20L)
        assertThat(earlier < later).isTrue()
    }
}

class IdentifierTest {

    @Test
    fun blankIds_areRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionId.of("  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CategoryId.of("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParserVersion.of("")
        }
    }

    @Test
    fun typedIds_trimAndPreserveValue() {
        assertThat(TransactionId.of(" abc ").value).isEqualTo("abc")
    }
}

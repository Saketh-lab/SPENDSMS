package com.spendsms.app.domain.parsing

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.model.CurrencyCode
import org.junit.Test

class AmountParserTest {

    @Test
    fun parsesIndianFormattedAmountToPaise() {
        val money = AmountParser.parseMoney("1,234.56", null)
        assertThat(money!!.amountMinorUnits).isEqualTo(123_456L)
        assertThat(money.currency).isEqualTo(CurrencyCode.INR)
    }

    @Test
    fun parsesWholeRupees() {
        assertThat(AmountParser.parseMinorUnits("99")).isEqualTo(9_900L)
    }

    @Test
    fun parsesCurrencySynonyms() {
        assertThat(AmountParser.parseCurrency("Rs")).isEqualTo(CurrencyCode.INR)
        assertThat(AmountParser.parseCurrency("₹")).isEqualTo(CurrencyCode.INR)
        assertThat(AmountParser.parseCurrency("INR")).isEqualTo(CurrencyCode.INR)
    }

    @Test
    fun rejectsGarbageAmount() {
        assertThat(AmountParser.parseMinorUnits("12.345")).isNull()
        assertThat(AmountParser.parseMinorUnits("abc")).isNull()
    }
}

class SmsDateParserTest {
    @Test
    fun parsesDdMmYyyy() {
        val epoch = SmsDateParser.parseOrNull("10-08-2026")
        assertThat(epoch).isNotNull()
        assertThat(SmsDateParser.parseOrNull("not-a-date")).isNull()
    }
}

class PaymentMethodClassifierTest {
    @Test
    fun classifiesCommonRails() {
        assertThat(PaymentMethodClassifier.classify("paid via UPI")).isEqualTo(
            com.spendsms.app.domain.model.PaymentMethod.UPI,
        )
        assertThat(PaymentMethodClassifier.classify("ATM cash withdrawal")).isEqualTo(
            com.spendsms.app.domain.model.PaymentMethod.ATM,
        )
        assertThat(PaymentMethodClassifier.classify("NEFT transfer done")).isEqualTo(
            com.spendsms.app.domain.model.PaymentMethod.NET_BANKING,
        )
    }
}

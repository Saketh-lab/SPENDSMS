package com.spendsms.app.domain.filtering

import com.google.common.truth.Truth.assertThat
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.sms.RawSmsMessage
import org.junit.Test

class FinancialMessageFilterTest {

    private val filter = DefaultFinancialMessageFilter()
    private val knownSenders = listOf("EX-BANK", "EXBANK", "DEMOUPI")

    @Test
    fun acceptsKnownSenderFinancialDebit() {
        val result = filter.filter(
            sms("VM-EX-BANK", "Rs.100.00 debited at STORE"),
            knownSenders,
        )
        assertThat(result).isInstanceOf(FilterResult.Accept::class.java)
    }

    @Test
    fun acceptsUnknownSenderWithFinancialKeywords() {
        val result = filter.filter(
            sms("UNKNOWN", "INR 50 spent on UPI to merchant@ybl"),
            knownSenders,
        )
        assertThat(result).isInstanceOf(FilterResult.Accept::class.java)
    }

    @Test
    fun rejectsOtp() {
        val result = filter.filter(
            sms("VM-EX-BANK", "Your OTP is 123456. Do not share this OTP with anyone."),
            knownSenders,
        )
        assertThat(result).isEqualTo(FilterResult.Reject)
    }

    @Test
    fun rejectsMarketingWithoutFinancialSignal() {
        val result = filter.filter(
            sms("PROMO", "Limited period offer! Flat 50% off. Click here to unsubscribe"),
            knownSenders,
        )
        assertThat(result).isEqualTo(FilterResult.Reject)
    }

    @Test
    fun rejectsDeliveryNotification() {
        val result = filter.filter(
            sms("SHIP", "Your package is out for delivery. Tracking id 99"),
            knownSenders,
        )
        assertThat(result).isEqualTo(FilterResult.Reject)
    }

    @Test
    fun rejectsPersonalChat() {
        val result = filter.filter(
            sms("MOM", "Are you coming home for dinner tonight?"),
            knownSenders,
        )
        assertThat(result).isEqualTo(FilterResult.Reject)
    }

    @Test
    fun acceptToString_doesNotIncludeBody() {
        val body = "Rs.100.00 debited at PRIVATE_SHOP"
        val result = filter.filter(sms("EX-BANK", body), knownSenders)
        assertThat(result.toString()).doesNotContain(body)
        assertThat(result.toString()).doesNotContain("PRIVATE_SHOP")
        assertThat(result.toString()).doesNotContain("EX-BANK")
    }

    private fun sms(sender: String, body: String): RawSmsMessage =
        RawSmsMessage(
            sourceMessageId = "1",
            sender = sender,
            receivedAt = EpochMillis.of(1L),
            body = body,
        )
}

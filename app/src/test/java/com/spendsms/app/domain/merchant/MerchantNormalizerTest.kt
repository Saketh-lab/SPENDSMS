package com.spendsms.app.domain.merchant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MerchantNormalizerTest {

    private val normalizer = MerchantNormalizer()

    @Test
    fun collapsesCaseAndPunctuation() {
        val merchant = normalizer.normalize("  SWIGGY!!!  ")
        assertThat(merchant!!.key.value).isEqualTo("swiggy")
        assertThat(merchant.displayName).isEqualTo("Swiggy")
        assertThat(merchant.rawNormalized).isEqualTo("SWIGGY!!!")
    }

    @Test
    fun stripsUpiHandle() {
        val merchant = normalizer.normalize("coffee@oksbi")
        assertThat(merchant!!.key.value).isEqualTo("coffee")
        assertThat(merchant.displayName).isEqualTo("coffee")
    }

    @Test
    fun stripsPaymentProcessorPrefix() {
        val merchant = normalizer.normalize("PAYTM*SWIGGY")
        assertThat(merchant!!.key.value).isEqualTo("swiggy")
        assertThat(merchant.displayName).isEqualTo("Swiggy")
    }

    @Test
    fun mapsAliasTokens() {
        val merchant = normalizer.normalize("amzn")
        assertThat(merchant!!.key.value).isEqualTo("amazon")
        assertThat(merchant.displayName).isEqualTo("Amazon")
    }

    @Test
    fun unknownMerchantFallsBackToCleanedText() {
        val merchant = normalizer.normalize("LOCAL KIRANA-12")
        assertThat(merchant!!.key.value).isEqualTo("local kirana 12")
        assertThat(merchant.displayName).isEqualTo("LOCAL KIRANA-12")
    }

    @Test
    fun blankRawReturnsNull() {
        assertThat(normalizer.normalize("   ")).isNull()
        assertThat(normalizer.normalize(null)).isNull()
    }

    @Test
    fun keyDoesNotIncludeInstitution() {
        val merchant = normalizer.normalize("New Place", institution = "Example Bank")
        assertThat(merchant!!.key.value).isEqualTo("new place")
    }
}

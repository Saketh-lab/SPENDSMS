package com.spendsms.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Retained smoke coverage for the domain layer marker introduced in Prompt 1.
 */
class DomainLayerSmokeTest {

    @Test
    fun domainLayerMarker_isAvailable() {
        assertThat(DomainLayer::class.java.simpleName).isEqualTo("DomainLayer")
    }
}

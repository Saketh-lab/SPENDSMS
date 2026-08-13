package com.spendsms.app.presentation.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpendSmsRoutesTest {

    @Test
    fun mainTabs_includeCoreDestinations() {
        assertThat(MainTabRoutes).containsExactly(
            SpendSmsRoutes.DASHBOARD,
            SpendSmsRoutes.TRANSACTIONS,
            SpendSmsRoutes.SUBSCRIPTIONS,
            SpendSmsRoutes.SETTINGS,
        ).inOrder()
    }

    @Test
    fun detailRoutes_encodeIds() {
        assertThat(SpendSmsRoutes.transactionDetail("tx-1")).isEqualTo("transaction/tx-1")
        assertThat(SpendSmsRoutes.categoryDetail("food_and_dining"))
            .isEqualTo("category/food_and_dining")
        assertThat(SpendSmsRoutes.merchantDetail("netflix")).isEqualTo("merchant/netflix")
    }

    @Test
    fun bootstrapAndOnboardingRoutes_areDistinctFromTabs() {
        assertThat(MainTabRoutes).doesNotContain(SpendSmsRoutes.ONBOARDING)
        assertThat(MainTabRoutes).doesNotContain(SpendSmsRoutes.SMS_DISCLOSURE)
        assertThat(MainTabRoutes).doesNotContain(SpendSmsRoutes.SCAN_PERIOD)
        assertThat(MainTabRoutes).doesNotContain(SpendSmsRoutes.PRIVACY_DELETION)
    }
}

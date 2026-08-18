package com.spendsms.app.presentation.navigation

object SpendSmsRoutes {
    const val BOOTSTRAP = "bootstrap"
    const val ONBOARDING = "onboarding"
    const val SAMPLE_DASHBOARD = "sample_dashboard"
    const val SMS_DISCLOSURE = "sms_disclosure"
    const val SCAN_PERIOD = "scan_period"
    const val SCAN_PROGRESS = "scan_progress"
    const val SCAN_SUMMARY = "scan_summary"
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val TRANSACTION_DETAIL = "transaction/{transactionId}"
    const val CATEGORY_DETAIL = "category/{categoryId}"
    const val MERCHANT_DETAIL = "merchant/{merchantKey}"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SETTINGS = "settings"
    const val SUPPORT = "support"
    const val PRIVACY_DELETION = "privacy_deletion"

    fun transactionDetail(id: String) = "transaction/$id"
    fun categoryDetail(id: String) = "category/$id"
    fun merchantDetail(key: String) = "merchant/$key"
}

val MainTabRoutes = listOf(
    SpendSmsRoutes.DASHBOARD,
    SpendSmsRoutes.TRANSACTIONS,
    SpendSmsRoutes.SUBSCRIPTIONS,
    SpendSmsRoutes.SETTINGS,
)

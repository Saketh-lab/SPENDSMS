package com.spendsms.app.application.port.sms

/**
 * Runtime SMS-read permission for the scan pipeline (Step-3 SMS Source).
 *
 * Does not request the system prompt — that stays in a later UI/onboarding step.
 */
fun interface SmsPermissionPort {
    fun hasReadSmsPermission(): Boolean
}

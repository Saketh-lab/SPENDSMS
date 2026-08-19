package com.spendsms.app.platform.security

import android.view.Window
import android.view.WindowManager

/**
 * Recents/screenshot protection for financial UI (Step-2 §4.1).
 */
object SensitiveWindowPolicy {

    fun disableScreenshots(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
}

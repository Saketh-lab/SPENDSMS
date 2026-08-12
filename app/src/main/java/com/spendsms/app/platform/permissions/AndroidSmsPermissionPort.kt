package com.spendsms.app.platform.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.spendsms.app.application.port.sms.SmsPermissionPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSmsPermissionPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsPermissionPort {

    override fun hasReadSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
}

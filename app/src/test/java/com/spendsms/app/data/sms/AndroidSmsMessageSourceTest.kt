package com.spendsms.app.data.sms

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.application.port.sms.SmsAccessException
import com.spendsms.app.application.port.sms.SmsBatchQuery
import com.spendsms.app.application.port.sms.SmsPermissionPort
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidSmsMessageSourceTest {

    @Test
    fun readBatch_throwsPermissionDeniedWithoutQueryingProvider() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val source = AndroidSmsMessageSource(
            context = context,
            permissionPort = SmsPermissionPort { false },
        )
        try {
            source.readBatch(
                SmsBatchQuery(
                    period = AnalysisPeriod(EpochMillis.of(1L), EpochMillis.of(2L)),
                    limit = 10,
                ),
            )
            throw AssertionError("expected PermissionDenied")
        } catch (e: SmsAccessException.PermissionDenied) {
            assertThat(e).isInstanceOf(SmsAccessException.PermissionDenied::class.java)
        }
    }
}

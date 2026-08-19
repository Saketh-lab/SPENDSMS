package com.spendsms.app.platform.security

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.spendsms.app.data.room.entity.CategoryEntity
import com.spendsms.app.data.room.entity.DashboardCacheEntity
import com.spendsms.app.data.room.entity.ParserMetadataEntity
import com.spendsms.app.data.room.entity.ScanStateEntity
import com.spendsms.app.data.room.entity.SubscriptionEntity
import com.spendsms.app.data.room.entity.TransactionEntity
import com.spendsms.app.data.room.entity.UserCorrectionEntity
import com.spendsms.app.data.telemetry.NoOpTelemetryPort
import com.spendsms.app.di.AppModule
import com.spendsms.app.domain.privacy.SensitivePayloadPatterns
import com.spendsms.app.platform.work.ScanWorkContract
import com.spendsms.app.application.analysis.ScanWorkInput
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.EpochMillis
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PrivacySecurityAuditTest {

    @Test
    fun backupDisabled_andNoDangerousSmsWritePermissions() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertThat(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)

        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            .orEmpty()
        assertThat(requested).contains(Manifest.permission.READ_SMS)
        assertThat(requested).doesNotContain(Manifest.permission.RECEIVE_SMS)
        assertThat(requested).doesNotContain(Manifest.permission.SEND_SMS)
        assertThat(requested).doesNotContain("android.permission.WRITE_SMS")
        assertThat(requested).doesNotContain(Manifest.permission.READ_CONTACTS)
        assertThat(requested).doesNotContain(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun onlyLauncherActivityIsExported() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS,
        )
        val exportedOurs = { info: android.content.pm.ComponentInfo ->
            info.exported && info.name.startsWith("com.spendsms.app.")
        }
        val exportedActivities = packageInfo.activities.orEmpty().filter(exportedOurs)
        assertThat(exportedActivities.map { it.name }).containsExactly("com.spendsms.app.MainActivity")
        assertThat(packageInfo.receivers.orEmpty().none(exportedOurs)).isTrue()
        assertThat(packageInfo.services.orEmpty().none(exportedOurs)).isTrue()
        assertThat(packageInfo.providers.orEmpty().none(exportedOurs)).isTrue()
    }

    @Test
    fun sensitiveWindowPolicy_setsFlagSecure() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        SensitiveWindowPolicy.disableScreenshots(activity.window)
        assertThat(
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE,
        ).isNotEqualTo(0)
    }

    @Test
    fun roomEntities_haveNoRawSmsColumns() {
        val entities = listOf(
            TransactionEntity::class.java,
            UserCorrectionEntity::class.java,
            SubscriptionEntity::class.java,
            ScanStateEntity::class.java,
            DashboardCacheEntity::class.java,
            ParserMetadataEntity::class.java,
            CategoryEntity::class.java,
        )
        for (type in entities) {
            val names = type.declaredFields.map { it.name.lowercase() }
            assertThat(names).doesNotContain("body")
            assertThat(names).doesNotContain("smsbody")
            assertThat(names).doesNotContain("rawsms")
            assertThat(names).doesNotContain("sender")
            assertThat(names).doesNotContain("address")
        }
    }

    @Test
    fun okHttpClient_hasNoLoggingInterceptor() {
        val client = AppModule.provideOkHttpClient()
        val interceptors = client.interceptors + client.networkInterceptors
        assertThat(interceptors.map { it.javaClass.name }.none { it.contains("HttpLogging") }).isTrue()
    }

    @Test
    fun telemetryPort_isNoOpAndDoesNotRetainEvents() = runBlocking {
        val port = NoOpTelemetryPort()
        port.enqueue(emptyList())
        port.flush()
    }

    @Test
    fun workManagerInput_doesNotLookLikeSmsOrAmounts() {
        val data = ScanWorkContract.toInputData(
            ScanWorkInput(
                period = AnalysisPeriod(EpochMillis.of(1_000L), EpochMillis.of(2_000L)),
            ),
        )
        val serialized = data.keyValueMap.values.joinToString(" ") { it.toString() }
        assertThat(SensitivePayloadPatterns.looksLikeUnredactedSmsOrFinancialSecret(serialized)).isFalse()
        assertThat(data.keyValueMap.keys.joinToString()).doesNotContain("body")
    }

    @Test
    fun sensitivePayloadPatterns_flagSmsLikeText_butAllowPlaceholders() {
        assertThat(
            SensitivePayloadPatterns.looksLikeUnredactedSmsOrFinancialSecret(
                "Rs.1,250.50 debited at SWIGGY",
            ),
        ).isTrue()
        assertThat(
            SensitivePayloadPatterns.looksLikeUnredactedSmsOrFinancialSecret(
                "Debited [AMOUNT] at [MERCHANT]",
            ),
        ).isFalse()
    }

    @Test
    fun parserAssets_doNotShipPrivateKeys() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val names = context.assets.list("parser").orEmpty()
        assertThat(names.none { it.contains("private", ignoreCase = true) }).isTrue()
        for (name in names) {
            val text = context.assets.open("parser/$name").bufferedReader().use { it.readText() }
            assertThat(text.uppercase()).doesNotContain("BEGIN PRIVATE KEY")
            assertThat(text.uppercase()).doesNotContain("BEGIN EC PRIVATE")
        }
    }

    @Test
    fun presentationLayer_doesNotImportRawSmsTypes() {
        val roots = listOf(
            File("src/main/java/com/spendsms/app/presentation"),
            File("app/src/main/java/com/spendsms/app/presentation"),
        )
        val root = roots.firstOrNull { it.isDirectory } ?: return
        val sources = root.walkTopDown().filter { it.extension == "kt" }.toList()
        assertThat(sources).isNotEmpty()
        for (file in sources) {
            val text = file.readText()
            assertThat(text).doesNotContain("RawSmsMessage")
            assertThat(text).doesNotContain("EphemeralSmsMessage")
            assertThat(text).doesNotContain("android.provider.Telephony")
        }
    }
}

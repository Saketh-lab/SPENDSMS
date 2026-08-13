package com.spendsms.app.presentation.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendsms.app.R
import com.spendsms.app.presentation.common.MessageState
import com.spendsms.app.presentation.common.SectionTitle
import com.spendsms.app.presentation.common.UiFormatters

@Composable
fun OnboardingScreen(
    onSeeSample: () -> Unit,
    onAnalyse: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        SectionTitle("Privacy summary")
        listOf(
            R.string.onboarding_privacy_point_1,
            R.string.onboarding_privacy_point_2,
            R.string.onboarding_privacy_point_3,
            R.string.onboarding_privacy_point_4,
            R.string.onboarding_privacy_point_5,
        ).forEach { id ->
            Text("• ${stringResource(id)}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = stringResource(R.string.onboarding_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onSeeSample, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_see_sample))
        }
        Button(onClick = onAnalyse, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_analyse))
        }
    }
}

@Composable
fun SampleDashboardScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.sample_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.sample_body), style = MaterialTheme.typography.bodyLarge)
        SectionTitle("Example totals")
        Text("Gross spending  ${UiFormatters.money(com.spendsms.app.domain.model.Money.ofMinorUnits(24_500_00L, com.spendsms.app.domain.model.CurrencyCode.INR))}")
        Text("Net spending    ${UiFormatters.money(com.spendsms.app.domain.model.Money.ofMinorUnits(23_200_00L, com.spendsms.app.domain.model.CurrencyCode.INR))}")
        Text("Top category    Food and dining")
        Text("Suspected subs  2 · ~${UiFormatters.money(com.spendsms.app.domain.model.Money.ofMinorUnits(899_00L, com.spendsms.app.domain.model.CurrencyCode.INR))}/mo")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sample_continue))
        }
    }
}

@Composable
fun SmsDisclosureScreen(
    onPermissionGranted: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val granted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        viewModel.refreshPermission()
        if (isGranted) {
            viewModel.completeOnboarding(onPermissionGranted)
        }
    }

    LaunchedEffect(granted) {
        if (granted) {
            viewModel.completeOnboarding(onPermissionGranted)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.disclosure_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.disclosure_body), style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { launcher.launch(Manifest.permission.READ_SMS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.disclosure_allow))
        }
        OutlinedButton(
            onClick = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.disclosure_open_settings))
        }
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.disclosure_not_now))
        }
    }
}

@Composable
fun PermissionDeniedScreen(
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    MessageState(
        title = stringResource(R.string.permission_denied_title),
        body = stringResource(R.string.permission_denied_body),
        primaryActionLabel = stringResource(R.string.disclosure_open_settings),
        onPrimaryAction = onOpenSettings,
        secondaryActionLabel = "Back",
        onSecondaryAction = onBack,
    )
}

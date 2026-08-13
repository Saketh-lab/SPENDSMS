package com.spendsms.app.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendsms.app.R
import com.spendsms.app.presentation.common.LoadingState
import com.spendsms.app.presentation.common.MessageState
import com.spendsms.app.presentation.common.SectionTitle

@Composable
fun SettingsScreen(
    onOpenPrivacyDeletion: () -> Unit,
    onStartScan: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        SectionTitle(stringResource(R.string.settings_permission))
        Text(
            if (ui.permissionGranted) {
                stringResource(R.string.settings_permission_granted)
            } else {
                stringResource(R.string.settings_permission_denied)
            },
        )
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_open_permission))
        }

        SectionTitle(stringResource(R.string.settings_privacy))
        Button(onClick = onOpenPrivacyDeletion, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.deletion_title))
        }

        SectionTitle("Analysis")
        OutlinedButton(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_rescan))
        }

        SectionTitle(stringResource(R.string.settings_privacy_policy))
        Text(stringResource(R.string.settings_privacy_policy_body), style = MaterialTheme.typography.bodyMedium)
        Text("${stringResource(R.string.settings_about)} · v${ui.appVersionLabel}")
    }
}

@Composable
fun PrivacyDeletionScreen(
    onDoneNavigateHome: () -> Unit,
    onCancel: () -> Unit,
    viewModel: DataDeletionViewModel = hiltViewModel(),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val parserStillPresent by viewModel.parserStillPresent.collectAsStateWithLifecycle()

    when (val current = phase) {
        DeletionPhase.Confirm -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.deletion_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.deletion_body), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = viewModel::deleteAnalysedData, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.deletion_confirm))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.deletion_cancel))
            }
        }
        DeletionPhase.Deleting -> LoadingState()
        DeletionPhase.Done -> MessageState(
            title = stringResource(R.string.deletion_done_title),
            body = buildString {
                append(stringResource(R.string.deletion_done_body))
                if (parserStillPresent) {
                    append("\n\nParser rule packages were retained as designed.")
                }
            },
            primaryActionLabel = "Continue",
            onPrimaryAction = onDoneNavigateHome,
        )
        is DeletionPhase.Failed -> MessageState(
            title = "Deletion failed",
            body = current.message,
            primaryActionLabel = "Try again",
            onPrimaryAction = viewModel::reset,
            secondaryActionLabel = "Cancel",
            onSecondaryAction = onCancel,
        )
    }
}

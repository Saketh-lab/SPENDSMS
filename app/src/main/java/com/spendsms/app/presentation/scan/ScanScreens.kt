package com.spendsms.app.presentation.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendsms.app.R
import com.spendsms.app.presentation.common.MessageState
import com.spendsms.app.presentation.common.PeriodPreset
import com.spendsms.app.presentation.common.UiFormatters

@Composable
fun ScanFlowScreen(
    onFinished: () -> Unit,
    onNeedPermission: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val preset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val resumable by viewModel.resumable.collectAsStateWithLifecycle()

    when (val current = phase) {
        ScanPhase.SelectPeriod -> ScanPeriodContent(
            selected = preset,
            resumableLabel = resumable?.let {
                "Resume ${UiFormatters.periodLabel(it.period)} · ${it.processedCount} processed"
            },
            onSelect = viewModel::selectPreset,
            onStart = viewModel::startSelectedPeriod,
            onResume = viewModel::resumeIfPossible,
        )
        is ScanPhase.Running -> ScanProgressContent(
            progress = current.progress,
            onCancel = viewModel::cancelScan,
        )
        is ScanPhase.Completed -> ScanSummaryContent(
            completed = current,
            onContinue = onFinished,
        )
        is ScanPhase.Failed -> MessageState(
            title = stringResource(R.string.scan_error_title),
            body = current.message,
            primaryActionLabel = if (current.canResume) {
                stringResource(R.string.scan_resume)
            } else {
                stringResource(R.string.scan_retry)
            },
            onPrimaryAction = {
                if (current.canResume) viewModel.resumeIfPossible() else viewModel.resetToPeriodSelection()
            },
            secondaryActionLabel = "Choose another period",
            onSecondaryAction = viewModel::resetToPeriodSelection,
        )
        is ScanPhase.PermissionRequired -> {
            LaunchedEffect(current) { onNeedPermission() }
            MessageState(
                title = stringResource(R.string.permission_denied_title),
                body = current.message,
                primaryActionLabel = stringResource(R.string.disclosure_allow),
                onPrimaryAction = onNeedPermission,
            )
        }
    }
}

@Composable
private fun ScanPeriodContent(
    selected: PeriodPreset,
    resumableLabel: String?,
    onSelect: (PeriodPreset) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.scan_period_title), style = MaterialTheme.typography.headlineSmall)
        PeriodPreset.entries.forEach { preset ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = preset == selected,
                        onClick = { onSelect(preset) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
            ) {
                androidx.compose.foundation.layout.Row {
                    RadioButton(selected = preset == selected, onClick = { onSelect(preset) })
                    Text(preset.label, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scan_start))
        }
        if (resumableLabel != null) {
            OutlinedButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scan_resume))
            }
            Text(resumableLabel, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScanProgressContent(
    progress: ScanUiProgress,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.scan_progress_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))
        Text("Status: ${progress.statusLabel}")
        Text("Messages processed: ${progress.processedCount}")
        Text("Transactions accepted: ${progress.acceptedCount}")
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scan_cancel))
        }
    }
}

@Composable
private fun ScanSummaryContent(
    completed: ScanPhase.Completed,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.scan_summary_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Period: ${UiFormatters.periodLabel(completed.period)}")
        Text("Messages examined: ${completed.processedCount}")
        Text("Transactions found: ${completed.acceptedCount}")
        Text("Isolated parse issues: ${completed.isolatedFailures}")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scan_go_dashboard))
        }
    }
}

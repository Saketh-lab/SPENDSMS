package com.spendsms.app.presentation.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendsms.app.R
import com.spendsms.app.presentation.common.LoadingState
import com.spendsms.app.presentation.common.MessageState
import com.spendsms.app.presentation.common.SectionTitle

@Composable
fun SupportScreen(
    onBack: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        SupportUiState.Loading -> LoadingState()
        is SupportUiState.Unavailable -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.support_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.support_unavailable_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            SectionTitle(stringResource(R.string.settings_control_plane))
            Text(stringResource(R.string.settings_cloud_sync_off))
            Text(
                stringResource(
                    R.string.settings_parser_source,
                    current.controlPlane.parserSourceLabel,
                ),
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.support_back))
            }
        }
        is SupportUiState.Ready -> MessageState(
            title = stringResource(R.string.support_title),
            body = stringResource(R.string.support_ready_body),
            primaryActionLabel = stringResource(R.string.support_submit_sample),
            onPrimaryAction = viewModel::submitSampleRedactedTemplate,
            secondaryActionLabel = stringResource(R.string.support_back),
            onSecondaryAction = onBack,
        )
        SupportUiState.Submitting -> LoadingState()
        is SupportUiState.Submitted -> MessageState(
            title = stringResource(R.string.support_submitted_title),
            body = stringResource(R.string.support_submitted_body),
            primaryActionLabel = stringResource(R.string.support_back),
            onPrimaryAction = onBack,
        )
        SupportUiState.ServiceUnavailable -> MessageState(
            title = stringResource(R.string.support_service_unavailable_title),
            body = stringResource(R.string.support_service_unavailable_body),
            primaryActionLabel = stringResource(R.string.support_back),
            onPrimaryAction = onBack,
        )
    }
}

package com.spendsms.app.presentation.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendsms.app.R
import com.spendsms.app.domain.subscriptions.Subscription
import com.spendsms.app.presentation.common.AsyncUiState
import com.spendsms.app.presentation.common.LoadingState
import com.spendsms.app.presentation.common.MessageState
import com.spendsms.app.presentation.common.SectionTitle
import com.spendsms.app.presentation.common.UiFormatters

@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.subscriptions_title), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = viewModel::refresh) { Text("Refresh") }
        }
        Text(
            stringResource(R.string.subscriptions_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when (val current = state) {
            AsyncUiState.Loading -> LoadingState()
            is AsyncUiState.Empty -> MessageState(
                title = stringResource(R.string.subscriptions_title),
                body = stringResource(R.string.subscriptions_empty),
            )
            is AsyncUiState.Error -> MessageState(title = "Error", body = current.message)
            is AsyncUiState.Ready -> {
                SubscriptionSection(
                    title = stringResource(R.string.subscriptions_suspected),
                    items = current.value.suspected,
                    onConfirm = viewModel::confirm,
                    onDismiss = viewModel::dismiss,
                )
                SubscriptionSection(
                    title = stringResource(R.string.subscriptions_confirmed),
                    items = current.value.confirmed,
                    onConfirm = null,
                    onDismiss = viewModel::dismiss,
                )
                SubscriptionSection(
                    title = stringResource(R.string.subscriptions_inactive),
                    items = current.value.inactive,
                    onConfirm = viewModel::confirm,
                    onDismiss = viewModel::dismiss,
                )
                SubscriptionSection(
                    title = stringResource(R.string.subscriptions_dismissed),
                    items = current.value.dismissed,
                    onConfirm = viewModel::confirm,
                    onDismiss = null,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionSection(
    title: String,
    items: List<Subscription>,
    onConfirm: ((String) -> Unit)?,
    onDismiss: ((String) -> Unit)?,
) {
    SectionTitle(title)
    if (items.isEmpty()) {
        Text("None", style = MaterialTheme.typography.bodyMedium)
        return
    }
    items.forEach { sub ->
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(sub.merchantDisplayName ?: sub.merchantKey.value, style = MaterialTheme.typography.titleMedium)
            Text(
                "${sub.frequency.name} · " +
                    (sub.estimatedAmount?.let(UiFormatters::money) ?: "—") +
                    " · confidence ${UiFormatters.confidencePercent(sub.confidence.value)}",
            )
            Text("Evidence payments: ${sub.evidenceTransactionIds.size}")
            Text("Last: ${UiFormatters.dateTime(sub.lastPaymentDate)} · Next: ${UiFormatters.dateTime(sub.estimatedNextDate)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onConfirm != null) {
                    OutlinedButton(onClick = { onConfirm(sub.id.value) }) {
                        Text(stringResource(R.string.subscriptions_confirm))
                    }
                }
                if (onDismiss != null) {
                    OutlinedButton(onClick = { onDismiss(sub.id.value) }) {
                        Text(stringResource(R.string.subscriptions_dismiss))
                    }
                }
            }
        }
    }
}

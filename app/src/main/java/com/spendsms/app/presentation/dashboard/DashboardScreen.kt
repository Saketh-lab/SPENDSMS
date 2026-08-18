package com.spendsms.app.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.presentation.common.AsyncUiState
import com.spendsms.app.presentation.common.LoadingState
import com.spendsms.app.presentation.common.MessageState
import com.spendsms.app.presentation.common.RefreshOnResume
import com.spendsms.app.presentation.common.SectionTitle
import com.spendsms.app.presentation.common.UiFormatters

@Composable
fun DashboardScreen(
    onOpenTransaction: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenMerchant: (String) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onStartScan: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    RefreshOnResume { viewModel.refresh(useCache = false) }

    when (val current = state) {
        AsyncUiState.Loading -> LoadingState()
        is AsyncUiState.Empty -> MessageState(
            title = stringResource(R.string.dashboard_empty_title),
            body = stringResource(R.string.dashboard_empty_body),
            primaryActionLabel = stringResource(R.string.dashboard_rescan),
            onPrimaryAction = onStartScan,
        )
        is AsyncUiState.Error -> MessageState(
            title = "Dashboard unavailable",
            body = current.message,
            primaryActionLabel = "Retry",
            onPrimaryAction = viewModel::refresh,
            secondaryActionLabel = stringResource(R.string.dashboard_rescan),
            onSecondaryAction = onStartScan,
        )
        is AsyncUiState.Ready -> DashboardContent(
            result = current.value,
            periodLabel = period?.let(UiFormatters::periodLabel).orEmpty(),
            onOpenTransaction = onOpenTransaction,
            onOpenCategory = onOpenCategory,
            onOpenMerchant = onOpenMerchant,
            onOpenSubscriptions = onOpenSubscriptions,
            onRefresh = viewModel::refresh,
            onStartScan = onStartScan,
        )
    }
}

@Composable
private fun DashboardContent(
    result: DashboardResult,
    periodLabel: String,
    onOpenTransaction: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenMerchant: (String) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onRefresh: () -> Unit,
    onStartScan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        if (periodLabel.isNotBlank()) {
            Text(periodLabel, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "${stringResource(R.string.dashboard_last_analysis)}: ${UiFormatters.dateTime(result.lastAnalysisAt)}",
            style = MaterialTheme.typography.bodySmall,
        )
        MetricCard(stringResource(R.string.dashboard_gross), UiFormatters.money(result.grossSpending))
        MetricCard(stringResource(R.string.dashboard_net), UiFormatters.money(result.netSpending))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                stringResource(R.string.dashboard_credits),
                UiFormatters.money(result.credits),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                stringResource(R.string.dashboard_refunds),
                UiFormatters.money(result.refunds),
                modifier = Modifier.weight(1f),
            )
        }
        Text("${stringResource(R.string.dashboard_transactions)}: ${result.transactionCount}")

        SectionTitle(stringResource(R.string.dashboard_categories))
        result.categoryTotals.take(6).forEach { row ->
            Text(
                text = "${row.categoryId.value.replace('_', ' ')}  ${UiFormatters.money(row.total)} (${row.transactionCount})",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCategory(row.categoryId.value) }
                    .padding(vertical = 4.dp),
            )
        }

        SectionTitle(stringResource(R.string.dashboard_merchants))
        result.merchantTotals.take(5).forEach { row ->
            Text(
                text = "${row.displayName}  ${UiFormatters.money(row.total)}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMerchant(row.merchantKey.value) }
                    .padding(vertical = 4.dp),
            )
        }

        SectionTitle(stringResource(R.string.dashboard_monthly))
        result.monthlyTotals.forEach { row ->
            Text("${row.yearMonth.value}  ${UiFormatters.money(row.total)}")
        }

        SectionTitle(stringResource(R.string.dashboard_subscriptions))
        Text(
            "Est. monthly ${UiFormatters.money(result.subscriptionTotals.estimatedMonthly)} · " +
                "${result.subscriptionTotals.activeOrSuspectedCount} active/suspected",
            modifier = Modifier.clickable(onClick = onOpenSubscriptions),
        )
        result.suspectedSubscriptions.take(3).forEach { sub ->
            Text("${sub.merchantDisplayName ?: sub.id.value} · ${sub.status.name}")
        }

        SectionTitle(stringResource(R.string.dashboard_recent))
        result.recentTransactions.forEach { tx ->
            Text(
                text = "${tx.merchant?.displayName ?: "Unknown"}  ${UiFormatters.money(tx.amount)}",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTransaction(tx.id.value) }
                    .padding(vertical = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.dashboard_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        TextButton(onClick = onStartScan) {
            Text(stringResource(R.string.dashboard_rescan))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

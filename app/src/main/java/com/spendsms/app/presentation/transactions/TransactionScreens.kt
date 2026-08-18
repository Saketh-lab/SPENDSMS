package com.spendsms.app.presentation.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendsms.app.R
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.presentation.common.AsyncUiState
import com.spendsms.app.presentation.common.LoadingState
import com.spendsms.app.presentation.common.MessageState
import com.spendsms.app.presentation.common.RefreshOnResume
import com.spendsms.app.presentation.common.SectionTitle
import com.spendsms.app.presentation.common.UiFormatters

@Composable
fun TransactionListScreen(
    onOpenTransaction: (String) -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    RefreshOnResume { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.transactions_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text(stringResource(R.string.transactions_search)) },
            singleLine = true,
        )
        when (val current = state) {
            AsyncUiState.Loading -> LoadingState()
            is AsyncUiState.Empty -> MessageState(
                title = stringResource(R.string.transactions_title),
                body = stringResource(R.string.transactions_empty),
            )
            is AsyncUiState.Error -> MessageState(
                title = "Error",
                body = current.message,
                primaryActionLabel = "Retry",
                onPrimaryAction = viewModel::refresh,
            )
            is AsyncUiState.Ready -> LazyColumn {
                items(current.value, key = { it.id.value }) { tx ->
                    TransactionRow(tx) { onOpenTransaction(tx.id.value) }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(tx.merchant?.displayName ?: "Unknown merchant", style = MaterialTheme.typography.titleMedium)
        Text(
            "${UiFormatters.money(tx.amount)} · ${tx.direction.name} · ${UiFormatters.date(tx.timestamp)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        AsyncUiState.Loading -> LoadingState()
        is AsyncUiState.Empty -> MessageState(title = "Missing", body = current.message)
        is AsyncUiState.Error -> MessageState(title = "Error", body = current.message)
        is AsyncUiState.Ready -> {
            val ui = current.value
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.transaction_detail_title), style = MaterialTheme.typography.headlineSmall)
                Text(UiFormatters.money(ui.transaction.amount), style = MaterialTheme.typography.headlineMedium)
                Text("${UiFormatters.dateTime(ui.transaction.timestamp)} · ${ui.transaction.paymentMethod.name}")
                Text(
                    "${stringResource(R.string.transaction_confidence)}: " +
                        UiFormatters.confidencePercent(ui.transaction.confidence.value),
                )

                OutlinedTextField(
                    value = ui.merchantDraft,
                    onValueChange = viewModel::updateMerchant,
                    label = { Text(stringResource(R.string.transaction_merchant)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                EnumDropdown(
                    label = stringResource(R.string.transaction_category),
                    options = ui.categories.map { it.id.value to it.name },
                    selectedKey = ui.categoryId,
                    onSelected = viewModel::updateCategory,
                )
                EnumDropdown(
                    label = stringResource(R.string.transaction_direction),
                    options = TransactionDirection.entries.map { it.name to it.name },
                    selectedKey = ui.direction.name,
                    onSelected = { viewModel.updateDirection(TransactionDirection.valueOf(it)) },
                )
                EnumDropdown(
                    label = stringResource(R.string.transaction_duplicate),
                    options = DuplicateStatus.entries.map { it.name to it.name },
                    selectedKey = ui.duplicateStatus.name,
                    onSelected = { viewModel.updateDuplicate(DuplicateStatus.valueOf(it)) },
                )
                EnumDropdown(
                    label = stringResource(R.string.transaction_transfer),
                    options = TransferStatus.entries.map { it.name to it.name },
                    selectedKey = ui.transferStatus.name,
                    onSelected = { viewModel.updateTransfer(TransferStatus.valueOf(it)) },
                )

                RowCheckbox(
                    checked = ui.applyToFuture,
                    label = stringResource(R.string.transaction_apply_future),
                    onCheckedChange = viewModel::updateApplyFuture,
                )

                Button(onClick = viewModel::saveCorrections, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.transaction_correct))
                }
                OutlinedButton(onClick = viewModel::markNotATransaction, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.transaction_not_a_transaction))
                }
                ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun RowCheckbox(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedKey }?.second ?: selectedKey
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun CategoryDetailScreen(
    onOpenTransaction: (String) -> Unit,
    viewModel: CategoryDetailViewModel = hiltViewModel(),
) {
    GroupedTransactionScreen(
        title = "${stringResource(R.string.category_detail_title)}: ${viewModel.title}",
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onOpenTransaction = onOpenTransaction,
    )
}

@Composable
fun MerchantDetailScreen(
    onOpenTransaction: (String) -> Unit,
    viewModel: MerchantDetailViewModel = hiltViewModel(),
) {
    GroupedTransactionScreen(
        title = "${stringResource(R.string.merchant_detail_title)}: ${viewModel.title}",
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onOpenTransaction = onOpenTransaction,
    )
}

@Composable
private fun GroupedTransactionScreen(
    title: String,
    state: AsyncUiState<List<Transaction>>,
    onOpenTransaction: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        SectionTitle("Transactions")
        when (state) {
            AsyncUiState.Loading -> LoadingState()
            is AsyncUiState.Empty -> Text(state.message)
            is AsyncUiState.Error -> Text(state.message)
            is AsyncUiState.Ready -> LazyColumn {
                items(state.value, key = { it.id.value }) { tx ->
                    TransactionRow(tx) { onOpenTransaction(tx.id.value) }
                }
            }
        }
    }
}

package com.spendsms.app.presentation.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendsms.app.application.corrections.ApplyCorrectionRequest
import com.spendsms.app.application.corrections.ApplyCorrectionResult
import com.spendsms.app.application.corrections.UserCorrectionService
import com.spendsms.app.application.port.CategoryRepository
import com.spendsms.app.application.port.TransactionQuery
import com.spendsms.app.application.port.TransactionRepository
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.model.Category
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.presentation.common.AsyncUiState
import com.spendsms.app.presentation.common.PeriodPreset
import com.spendsms.app.presentation.common.toAnalysisPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val preferences: UserPreferencesStore,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<AsyncUiState<List<Transaction>>>(AsyncUiState.Loading)
    val state: StateFlow<AsyncUiState<List<Transaction>>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) {
        _query.value = value
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = AsyncUiState.Loading
            val period = preferences.lastAnalysisPeriod.first()
                ?: PeriodPreset.LAST_30_DAYS.toAnalysisPeriod()
            val rows = transactionRepository.query(
                TransactionQuery(
                    period = period,
                    merchantSearch = _query.value.takeIf { it.isNotBlank() },
                ),
            ).sortedByDescending { it.timestamp.toEpochMillis }
            _state.value = if (rows.isEmpty()) {
                AsyncUiState.Empty("No transactions in this period.")
            } else {
                AsyncUiState.Ready(rows)
            }
        }
    }
}

data class TransactionDetailUi(
    val transaction: Transaction,
    val categories: List<Category>,
    val merchantDraft: String,
    val categoryId: String,
    val direction: TransactionDirection,
    val duplicateStatus: DuplicateStatus,
    val transferStatus: TransferStatus,
    val applyToFuture: Boolean,
    val message: String? = null,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val correctionService: UserCorrectionService,
    private val preferences: UserPreferencesStore,
) : ViewModel() {

    private val transactionId = TransactionId.of(
        checkNotNull(savedStateHandle["transactionId"]) { "transactionId required" },
    )

    private val _state = MutableStateFlow<AsyncUiState<TransactionDetailUi>>(AsyncUiState.Loading)
    val state: StateFlow<AsyncUiState<TransactionDetailUi>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val tx = transactionRepository.findById(transactionId)
            if (tx == null) {
                _state.value = AsyncUiState.Error("Transaction not found")
                return@launch
            }
            val categories = categoryRepository.getAll()
            _state.value = AsyncUiState.Ready(
                TransactionDetailUi(
                    transaction = tx,
                    categories = categories,
                    merchantDraft = tx.merchant?.displayName.orEmpty(),
                    categoryId = tx.categoryId.value,
                    direction = tx.direction,
                    duplicateStatus = tx.duplicateStatus,
                    transferStatus = tx.transferStatus,
                    applyToFuture = false,
                ),
            )
        }
    }

    fun updateMerchant(value: String) = updateDraft { it.copy(merchantDraft = value) }
    fun updateCategory(value: String) = updateDraft { it.copy(categoryId = value) }
    fun updateDirection(value: TransactionDirection) = updateDraft { it.copy(direction = value) }
    fun updateDuplicate(value: DuplicateStatus) = updateDraft { it.copy(duplicateStatus = value) }
    fun updateTransfer(value: TransferStatus) = updateDraft { it.copy(transferStatus = value) }
    fun updateApplyFuture(value: Boolean) = updateDraft { it.copy(applyToFuture = value) }

    fun saveCorrections() {
        val current = (_state.value as? AsyncUiState.Ready)?.value ?: return
        viewModelScope.launch {
            val period = preferences.lastAnalysisPeriod.first()
            val now = EpochMillis.of(System.currentTimeMillis())
            val ops = buildList {
                val tx = current.transaction
                if (current.merchantDraft.isNotBlank() &&
                    current.merchantDraft != tx.merchant?.displayName
                ) {
                    add(
                        ApplyCorrectionRequest(
                            transactionId = tx.id,
                            field = CorrectionField.MERCHANT,
                            newValue = current.merchantDraft,
                            applyToFuture = current.applyToFuture,
                            merchantMatchKey = tx.merchant?.key
                                ?: MerchantKey.of(current.merchantDraft.lowercase()),
                            dashboardPeriod = period,
                        ),
                    )
                }
                if (current.categoryId != tx.categoryId.value) {
                    add(
                        ApplyCorrectionRequest(
                            transactionId = tx.id,
                            field = CorrectionField.CATEGORY,
                            newValue = current.categoryId,
                            applyToFuture = current.applyToFuture,
                            merchantMatchKey = tx.merchant?.key,
                            dashboardPeriod = period,
                        ),
                    )
                }
                if (current.direction != tx.direction) {
                    add(
                        ApplyCorrectionRequest(
                            transactionId = tx.id,
                            field = CorrectionField.DIRECTION,
                            newValue = current.direction.name,
                            dashboardPeriod = period,
                        ),
                    )
                }
                if (current.duplicateStatus != tx.duplicateStatus) {
                    add(
                        ApplyCorrectionRequest(
                            transactionId = tx.id,
                            field = CorrectionField.DUPLICATE_STATUS,
                            newValue = current.duplicateStatus.name,
                            dashboardPeriod = period,
                        ),
                    )
                }
                if (current.transferStatus != tx.transferStatus) {
                    add(
                        ApplyCorrectionRequest(
                            transactionId = tx.id,
                            field = CorrectionField.TRANSFER_STATUS,
                            newValue = current.transferStatus.name,
                            dashboardPeriod = period,
                        ),
                    )
                }
            }
            var message = "No changes"
            for (op in ops) {
                when (val result = correctionService.apply(op, now)) {
                    is ApplyCorrectionResult.Applied -> message = "Corrections saved"
                    is ApplyCorrectionResult.Failed -> {
                        message = result.detail
                        break
                    }
                }
            }
            load()
            val ready = (_state.value as? AsyncUiState.Ready)?.value
            if (ready != null) {
                _state.value = AsyncUiState.Ready(ready.copy(message = message))
            }
        }
    }

    fun markNotATransaction() {
        viewModelScope.launch {
            val period = preferences.lastAnalysisPeriod.first()
            correctionService.apply(
                ApplyCorrectionRequest(
                    transactionId = transactionId,
                    field = CorrectionField.NOT_A_TRANSACTION,
                    newValue = "true",
                    dashboardPeriod = period,
                ),
                now = EpochMillis.of(System.currentTimeMillis()),
            )
            load()
        }
    }

    private fun updateDraft(transform: (TransactionDetailUi) -> TransactionDetailUi) {
        val ready = (_state.value as? AsyncUiState.Ready)?.value ?: return
        _state.value = AsyncUiState.Ready(transform(ready))
    }
}

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val preferences: UserPreferencesStore,
) : ViewModel() {
    private val categoryId = CategoryId.of(checkNotNull(savedStateHandle["categoryId"]))
    private val _state = MutableStateFlow<AsyncUiState<List<Transaction>>>(AsyncUiState.Loading)
    val state: StateFlow<AsyncUiState<List<Transaction>>> = _state.asStateFlow()
    val title: String = categoryId.value

    init {
        viewModelScope.launch {
            val period = preferences.lastAnalysisPeriod.first()
                ?: PeriodPreset.LAST_30_DAYS.toAnalysisPeriod()
            val rows = transactionRepository.query(
                TransactionQuery(period = period, categoryId = categoryId),
            )
            _state.value = if (rows.isEmpty()) AsyncUiState.Empty("No transactions") else AsyncUiState.Ready(rows)
        }
    }
}

@HiltViewModel
class MerchantDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val preferences: UserPreferencesStore,
) : ViewModel() {
    private val merchantKey = MerchantKey.of(
        android.net.Uri.decode(checkNotNull(savedStateHandle["merchantKey"])),
    )
    private val _state = MutableStateFlow<AsyncUiState<List<Transaction>>>(AsyncUiState.Loading)
    val state: StateFlow<AsyncUiState<List<Transaction>>> = _state.asStateFlow()
    val title: String = merchantKey.value

    init {
        viewModelScope.launch {
            val period = preferences.lastAnalysisPeriod.first()
            val rows = transactionRepository.findByMerchantKey(merchantKey, period)
            _state.value = if (rows.isEmpty()) AsyncUiState.Empty("No transactions") else AsyncUiState.Ready(rows)
        }
    }
}

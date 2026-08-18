package com.spendsms.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.ScanStateRepository
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.dashboard.DashboardResult
import com.spendsms.app.domain.model.AnalysisPeriod
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
class DashboardViewModel @Inject constructor(
    private val dashboardService: DashboardService,
    private val preferences: UserPreferencesStore,
    private val scanStateRepository: ScanStateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AsyncUiState<DashboardResult>>(AsyncUiState.Loading)
    val state: StateFlow<AsyncUiState<DashboardResult>> = _state.asStateFlow()

    private val _period = MutableStateFlow<AnalysisPeriod?>(null)
    val period: StateFlow<AnalysisPeriod?> = _period.asStateFlow()

    init {
        refresh(useCache = true)
    }

    fun refresh(useCache: Boolean = false) {
        viewModelScope.launch {
            _state.value = AsyncUiState.Loading
            val period = preferences.lastAnalysisPeriod.first()
                ?: scanStateRepository.findLatestCompleted()?.period
                ?: PeriodPreset.LAST_30_DAYS.toAnalysisPeriod()
            _period.value = period
            runCatching {
                dashboardService.getDashboard(period, useCache = useCache)
            }.onSuccess { result ->
                _state.value = if (result.transactionCount == 0 && result.lastAnalysisAt == null) {
                    AsyncUiState.Empty("No analysed data yet.")
                } else {
                    AsyncUiState.Ready(result)
                }
            }.onFailure { error ->
                _state.value = AsyncUiState.Error(error.message ?: "Could not load dashboard")
            }
        }
    }
}

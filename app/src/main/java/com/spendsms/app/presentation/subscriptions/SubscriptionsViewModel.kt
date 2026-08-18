package com.spendsms.app.presentation.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendsms.app.application.dashboard.DashboardService
import com.spendsms.app.application.port.SubscriptionRepository
import com.spendsms.app.application.subscriptions.SubscriptionDetectionService
import com.spendsms.app.data.preferences.UserPreferencesStore
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.subscriptions.Subscription
import com.spendsms.app.presentation.common.AsyncUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SubscriptionsUi(
    val suspected: List<Subscription>,
    val confirmed: List<Subscription>,
    val dismissed: List<Subscription>,
    val inactive: List<Subscription>,
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionDetectionService: SubscriptionDetectionService,
    private val dashboardService: DashboardService,
    private val preferences: UserPreferencesStore,
) : ViewModel() {

    private val _state = MutableStateFlow<AsyncUiState<SubscriptionsUi>>(AsyncUiState.Loading)
    val state: StateFlow<AsyncUiState<SubscriptionsUi>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = AsyncUiState.Loading
            val all = subscriptionRepository.listAll()
            if (all.isEmpty()) {
                _state.value = AsyncUiState.Empty("No subscription suggestions yet.")
                return@launch
            }
            _state.value = AsyncUiState.Ready(
                SubscriptionsUi(
                    suspected = all.filter { it.status == SubscriptionStatus.SUSPECTED },
                    confirmed = all.filter { it.status == SubscriptionStatus.CONFIRMED },
                    dismissed = all.filter { it.status == SubscriptionStatus.DISMISSED },
                    inactive = all.filter { it.status == SubscriptionStatus.POSSIBLY_INACTIVE },
                ),
            )
        }
    }

    fun confirm(id: String) = updateStatus(id, SubscriptionStatus.CONFIRMED)

    fun dismiss(id: String) = updateStatus(id, SubscriptionStatus.DISMISSED)

    private fun updateStatus(id: String, status: SubscriptionStatus) {
        viewModelScope.launch {
            subscriptionDetectionService.updateStatus(SubscriptionId.of(id), status)
            dashboardService.invalidateCache()
            preferences.lastAnalysisPeriod.first()?.let { period ->
                dashboardService.recomputeAndCache(period)
            }
            refresh()
        }
    }
}

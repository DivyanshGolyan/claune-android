package com.divyanshgolyan.claune.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divyanshgolyan.claune.android.app.ClauneContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClauneViewModel(private val container: ClauneContainer) : ViewModel() {
    val uiState: StateFlow<ClauneUiState> =
        combine(
            container.sessionCoordinator.uiState,
            container.settingsStore.state,
        ) { sessionState, settingsState ->
            ClauneUiState(
                sessionState = sessionState,
                settingsState = settingsState,
                historyEntries = sessionHistoryEntries(sessionState.recentSessions),
                sessionDetail = container.codingSessionStore.loadSessionDetail(sessionState.selectedSessionPath),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
            ClauneUiState(
                sessionState = container.sessionCoordinator.uiState.value,
                settingsState = container.settingsStore.state.value,
                historyEntries = sessionHistoryEntries(container.sessionCoordinator.uiState.value.recentSessions),
                sessionDetail = container.codingSessionStore.loadSessionDetail(
                    container.sessionCoordinator.uiState.value.selectedSessionPath,
                ),
            ),
        )

    private val effectChannel = Channel<ClauneUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onEvent(event: ClauneUiEvent) {
        when (event) {
            ClauneUiEvent.CreateSession -> container.sessionCoordinator.createSession("")
            ClauneUiEvent.OpenAccessibilitySettings -> sendEffect(ClauneUiEffect.OpenAccessibilitySettings)
            is ClauneUiEvent.SelectSession -> container.sessionCoordinator.selectSession(event.path)
            is ClauneUiEvent.SendGoal -> {
                val goal = event.goal.trim()
                if (goal.isNotBlank()) {
                    sendEffect(ClauneUiEffect.StartSession(goal))
                }
            }
            ClauneUiEvent.StopSession -> sendEffect(ClauneUiEffect.StopSession)
            is ClauneUiEvent.UpdateAnthropicKey -> container.settingsStore.updateAnthropicApiKey(event.value)
            is ClauneUiEvent.SetDebugOverlayVisible -> container.overlayController.setDebugOverlayVisible(event.visible)
        }
    }

    private fun sendEffect(effect: ClauneUiEffect) {
        viewModelScope.launch {
            effectChannel.send(effect)
        }
    }

    class Factory(private val container: ClauneContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ClauneViewModel::class.java)) {
                return ClauneViewModel(container) as T
            }
            error("Unsupported ViewModel class: ${modelClass.name}")
        }
    }
}

sealed interface ClauneUiEvent {
    data object CreateSession : ClauneUiEvent

    data class SelectSession(val path: String) : ClauneUiEvent

    data class SendGoal(val goal: String) : ClauneUiEvent

    data object StopSession : ClauneUiEvent

    data object OpenAccessibilitySettings : ClauneUiEvent

    data class UpdateAnthropicKey(val value: String) : ClauneUiEvent

    data class SetDebugOverlayVisible(val visible: Boolean) : ClauneUiEvent
}

sealed interface ClauneUiEffect {
    data class StartSession(val goal: String) : ClauneUiEffect

    data object StopSession : ClauneUiEffect

    data object OpenAccessibilitySettings : ClauneUiEffect
}

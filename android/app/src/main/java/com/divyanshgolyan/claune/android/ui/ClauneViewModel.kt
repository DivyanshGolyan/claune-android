package com.divyanshgolyan.claune.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divyanshgolyan.claune.android.app.ClauneContainer
import com.divyanshgolyan.claune.android.data.local.ClauneModel
import com.divyanshgolyan.claune.android.data.local.PersistedSessionDetail
import java.io.File
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
                sessionDetail = loadSessionDetail(sessionState.selectedSessionPath),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
            ClauneUiState(
                sessionState = container.sessionCoordinator.uiState.value,
                settingsState = container.settingsStore.state.value,
                historyEntries = sessionHistoryEntries(container.sessionCoordinator.uiState.value.recentSessions),
                sessionDetail = loadSessionDetail(
                    container.sessionCoordinator.uiState.value.selectedSessionPath,
                ),
            ),
        )

    private val effectChannel = Channel<ClauneUiEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var cachedSessionDetail: CachedSessionDetail? = null

    fun onEvent(event: ClauneUiEvent) {
        when (event) {
            ClauneUiEvent.CreateSession -> {
                val created = container.sessionCoordinator.createSession("")
                sendEffect(ClauneUiEffect.NavigateToSession(created.path))
            }
            ClauneUiEvent.OpenAccessibilitySettings -> sendEffect(ClauneUiEffect.OpenAccessibilitySettings)
            is ClauneUiEvent.SelectSession -> {
                val selected = container.sessionCoordinator.selectSession(event.path)
                if (selected != null) {
                    sendEffect(ClauneUiEffect.NavigateToSession(selected.path))
                }
            }
            is ClauneUiEvent.SubmitMessage -> {
                val message = event.message.trim()
                if (message.isNotBlank()) {
                    sendEffect(ClauneUiEffect.SubmitMessage(message))
                }
            }
            ClauneUiEvent.StopSession -> sendEffect(ClauneUiEffect.StopSession)
            is ClauneUiEvent.UpdateSelectedModel -> viewModelScope.launch { container.settingsStore.updateSelectedModel(event.value) }
            is ClauneUiEvent.UpdateAnthropicKey -> viewModelScope.launch { container.settingsStore.updateAnthropicApiKey(event.value) }
            is ClauneUiEvent.UpdateGeminiKey -> viewModelScope.launch { container.settingsStore.updateGeminiApiKey(event.value) }
            is ClauneUiEvent.SetDebugOverlayVisible -> container.overlayController.setDebugOverlayVisible(event.visible)
        }
    }

    private fun sendEffect(effect: ClauneUiEffect) {
        viewModelScope.launch {
            effectChannel.send(effect)
        }
    }

    private fun loadSessionDetail(path: String?): PersistedSessionDetail? {
        if (path.isNullOrBlank()) {
            cachedSessionDetail = null
            return null
        }
        val modifiedAtMillis = File(path).lastModified()
        cachedSessionDetail
            ?.takeIf { it.path == path && it.modifiedAtMillis == modifiedAtMillis }
            ?.let { return it.detail }

        val detail = container.codingSessionStore.loadSessionDetail(path)
        cachedSessionDetail = CachedSessionDetail(path, modifiedAtMillis, detail)
        return detail
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

    private data class CachedSessionDetail(val path: String, val modifiedAtMillis: Long, val detail: PersistedSessionDetail?)
}

sealed interface ClauneUiEvent {
    data object CreateSession : ClauneUiEvent

    data class SelectSession(val path: String) : ClauneUiEvent

    data class SubmitMessage(val message: String) : ClauneUiEvent

    data object StopSession : ClauneUiEvent

    data object OpenAccessibilitySettings : ClauneUiEvent

    data class UpdateSelectedModel(val value: ClauneModel) : ClauneUiEvent

    data class UpdateAnthropicKey(val value: String) : ClauneUiEvent

    data class UpdateGeminiKey(val value: String) : ClauneUiEvent

    data class SetDebugOverlayVisible(val visible: Boolean) : ClauneUiEvent
}

sealed interface ClauneUiEffect {
    data class NavigateToSession(val path: String) : ClauneUiEffect

    data class SubmitMessage(val message: String) : ClauneUiEffect

    data object StopSession : ClauneUiEffect

    data object OpenAccessibilitySettings : ClauneUiEffect
}

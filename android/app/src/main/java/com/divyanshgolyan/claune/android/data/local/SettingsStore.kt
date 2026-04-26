package com.divyanshgolyan.claune.android.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ClauneModel {
    Haiku,
    GeminiFlashLite,
    ;

    companion object {
        fun fromStorage(value: String?): ClauneModel = entries.firstOrNull { it.name == value } ?: Haiku
    }
}

enum class ClauneThinkingLevel {
    Off,
    Minimal,
    Low,
    Medium,
    High,
    XHigh,
    ;

    companion object {
        fun fromStorage(value: String?): ClauneThinkingLevel = entries.firstOrNull { it.name == value } ?: Medium
    }
}

const val DEFAULT_HAIKU_THINKING_BUDGET = 4096
const val MIN_HAIKU_THINKING_BUDGET = 1024
const val MAX_HAIKU_THINKING_BUDGET = 32_000

data class SettingsState(
    val selectedModel: ClauneModel = ClauneModel.Haiku,
    val anthropicApiKey: String = "",
    val geminiApiKey: String = "",
    val thinkingLevel: ClauneThinkingLevel = ClauneThinkingLevel.Medium,
    val haikuThinkingBudget: Int = DEFAULT_HAIKU_THINKING_BUDGET,
)

interface SettingsStore {
    val state: StateFlow<SettingsState>

    suspend fun updateSelectedModel(value: ClauneModel)

    suspend fun updateAnthropicApiKey(value: String)

    suspend fun updateGeminiApiKey(value: String)

    suspend fun updateThinkingLevel(value: ClauneThinkingLevel)

    suspend fun updateHaikuThinkingBudget(value: Int)
}

private const val SETTINGS_PREFERENCES_NAME = "claune_settings"

private val Context.settingsDataStore by preferencesDataStore(
    name = SETTINGS_PREFERENCES_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, SETTINGS_PREFERENCES_NAME))
    },
)

class DataStoreSettingsStore(
    context: Context,
    defaultAnthropicApiKey: String,
    defaultGeminiApiKey: String,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SettingsStore {
    private val dataStore = context.applicationContext.settingsDataStore

    override val state: StateFlow<SettingsState> =
        dataStore.data
            .map { preferences ->
                SettingsState(
                    selectedModel = ClauneModel.fromStorage(preferences[KEY_SELECTED_MODEL]),
                    anthropicApiKey = preferences[KEY_ANTHROPIC_API_KEY].orEmpty(),
                    geminiApiKey = preferences[KEY_GEMINI_API_KEY].orEmpty(),
                    thinkingLevel = ClauneThinkingLevel.fromStorage(preferences[KEY_THINKING_LEVEL]),
                    haikuThinkingBudget =
                    preferences[KEY_HAIKU_THINKING_BUDGET]
                        ?.coerceIn(MIN_HAIKU_THINKING_BUDGET, MAX_HAIKU_THINKING_BUDGET)
                        ?: DEFAULT_HAIKU_THINKING_BUDGET,
                )
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = SettingsState(),
            )

    init {
        if (defaultAnthropicApiKey.isNotBlank()) {
            scope.launch {
                dataStore.edit { preferences ->
                    if (!preferences.contains(KEY_ANTHROPIC_API_KEY)) {
                        preferences[KEY_ANTHROPIC_API_KEY] = defaultAnthropicApiKey
                    }
                }
            }
        }
        if (defaultGeminiApiKey.isNotBlank()) {
            scope.launch {
                dataStore.edit { preferences ->
                    if (!preferences.contains(KEY_GEMINI_API_KEY)) {
                        preferences[KEY_GEMINI_API_KEY] = defaultGeminiApiKey
                    }
                }
            }
        }
    }

    override suspend fun updateSelectedModel(value: ClauneModel) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_MODEL] = value.name
        }
    }

    override suspend fun updateAnthropicApiKey(value: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ANTHROPIC_API_KEY] = value.trim()
        }
    }

    override suspend fun updateGeminiApiKey(value: String) {
        dataStore.edit { preferences ->
            preferences[KEY_GEMINI_API_KEY] = value.trim()
        }
    }

    override suspend fun updateThinkingLevel(value: ClauneThinkingLevel) {
        dataStore.edit { preferences ->
            preferences[KEY_THINKING_LEVEL] = value.name
        }
    }

    override suspend fun updateHaikuThinkingBudget(value: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_HAIKU_THINKING_BUDGET] = value.coerceIn(
                MIN_HAIKU_THINKING_BUDGET,
                MAX_HAIKU_THINKING_BUDGET,
            )
        }
    }

    private companion object {
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_THINKING_LEVEL = stringPreferencesKey("thinking_level")
        private val KEY_HAIKU_THINKING_BUDGET = intPreferencesKey("haiku_thinking_budget")
    }
}

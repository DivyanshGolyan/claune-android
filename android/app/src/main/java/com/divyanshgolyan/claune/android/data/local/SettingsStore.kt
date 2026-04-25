package com.divyanshgolyan.claune.android.data.local

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
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

data class SettingsState(
    val selectedModel: ClauneModel = ClauneModel.Haiku,
    val anthropicApiKey: String = "",
    val geminiApiKey: String = "",
)

interface SettingsStore {
    val state: StateFlow<SettingsState>

    suspend fun updateSelectedModel(value: ClauneModel)

    suspend fun updateAnthropicApiKey(value: String)

    suspend fun updateGeminiApiKey(value: String)
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

    private companion object {
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }
}

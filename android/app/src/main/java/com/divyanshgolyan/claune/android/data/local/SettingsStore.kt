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

data class SettingsState(val anthropicApiKey: String = "")

interface SettingsStore {
    val state: StateFlow<SettingsState>

    suspend fun updateAnthropicApiKey(value: String)
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
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SettingsStore {
    private val dataStore = context.applicationContext.settingsDataStore

    override val state: StateFlow<SettingsState> =
        dataStore.data
            .map { preferences ->
                SettingsState(
                    anthropicApiKey = preferences[KEY_ANTHROPIC_API_KEY].orEmpty(),
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
    }

    override suspend fun updateAnthropicApiKey(value: String) {
        dataStore.edit { preferences ->
            preferences[KEY_ANTHROPIC_API_KEY] = value.trim()
        }
    }

    private companion object {
        private val KEY_ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
    }
}

package com.divyanshgolyan.claune.android.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(val anthropicApiKey: String = "")

interface SettingsStore {
    val state: StateFlow<SettingsState>

    fun updateAnthropicApiKey(value: String)
}

class SharedPreferencesSettingsStore(context: Context, defaultAnthropicApiKey: String) : SettingsStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(readState())

    override val state: StateFlow<SettingsState> = mutableState.asStateFlow()

    init {
        if (!preferences.contains(KEY_ANTHROPIC_API_KEY) && defaultAnthropicApiKey.isNotBlank()) {
            preferences.edit().putString(KEY_ANTHROPIC_API_KEY, defaultAnthropicApiKey).apply()
            mutableState.value = readState()
        }
    }

    override fun updateAnthropicApiKey(value: String) {
        preferences.edit().putString(KEY_ANTHROPIC_API_KEY, value.trim()).apply()
        mutableState.value = readState()
    }

    private fun readState(): SettingsState = SettingsState(
        anthropicApiKey = preferences.getString(KEY_ANTHROPIC_API_KEY, "").orEmpty(),
    )

    private companion object {
        private const val PREFS_NAME = "claune_settings"
        private const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
    }
}

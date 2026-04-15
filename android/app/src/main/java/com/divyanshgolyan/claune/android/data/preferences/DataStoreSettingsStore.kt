package com.divyanshgolyan.claune.android.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prototypeSettingsDataStore by preferencesDataStore(name = "prototype_settings")

data class PrototypeSettings(val screenshotsEnabled: Boolean = false)

class DataStoreSettingsStore(private val context: Context) {
    val settings: Flow<PrototypeSettings> =
        context.prototypeSettingsDataStore.data.map { preferences ->
            PrototypeSettings(
                screenshotsEnabled = preferences[SCREENSHOTS_ENABLED] ?: false,
            )
        }

    suspend fun setScreenshotsEnabled(enabled: Boolean) {
        context.prototypeSettingsDataStore.edit { preferences ->
            preferences[SCREENSHOTS_ENABLED] = enabled
        }
    }

    private companion object {
        val SCREENSHOTS_ENABLED = booleanPreferencesKey("screenshots_enabled")
    }
}

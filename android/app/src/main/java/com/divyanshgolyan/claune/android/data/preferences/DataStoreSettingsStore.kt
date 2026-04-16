package com.divyanshgolyan.claune.android.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prototypeSettingsDataStore by preferencesDataStore(name = "prototype_settings")

data class PrototypeSettings(val screenshotsEnabled: Boolean = false, val demoPhoneEnabled: Boolean = false)

class DataStoreSettingsStore(private val context: Context) {
    val settings: Flow<PrototypeSettings> =
        context.prototypeSettingsDataStore.data.map { preferences ->
            PrototypeSettings(
                screenshotsEnabled = preferences[SCREENSHOTS_ENABLED] ?: false,
                demoPhoneEnabled = preferences[DEMO_PHONE_ENABLED] ?: false,
            )
        }

    suspend fun setScreenshotsEnabled(enabled: Boolean) {
        context.prototypeSettingsDataStore.edit { preferences ->
            preferences[SCREENSHOTS_ENABLED] = enabled
        }
    }

    suspend fun setDemoPhoneEnabled(enabled: Boolean) {
        context.prototypeSettingsDataStore.edit { preferences ->
            preferences[DEMO_PHONE_ENABLED] = enabled
        }
    }

    private companion object {
        val SCREENSHOTS_ENABLED = booleanPreferencesKey("screenshots_enabled")
        val DEMO_PHONE_ENABLED = booleanPreferencesKey("demo_phone_enabled")
    }
}

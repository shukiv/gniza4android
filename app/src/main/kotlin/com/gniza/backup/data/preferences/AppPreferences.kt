package com.gniza.backup.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gniza_preferences")

class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val RSYNC_BINARY_PATH = stringPreferencesKey("rsync_binary_path")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DARK_THEME_MODE = stringPreferencesKey("dark_theme_mode")
    }

    val rsyncBinaryPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.RSYNC_BINARY_PATH] ?: ""
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WIFI_ONLY] ?: true
    }

    val logRetentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOG_RETENTION_DAYS] ?: 30
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
    }

    val darkThemeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_THEME_MODE] ?: "system"
    }

    suspend fun setRsyncBinaryPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RSYNC_BINARY_PATH] = path
        }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIFI_ONLY] = enabled
        }
    }

    suspend fun setLogRetentionDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOG_RETENTION_DAYS] = days
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setDarkThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME_MODE] = mode
        }
    }
}

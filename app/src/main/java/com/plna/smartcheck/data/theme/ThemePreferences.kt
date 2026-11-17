package com.plna.smartcheck.data.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AppThemeMode(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromCode(code: String?): AppThemeMode =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_theme",
)

class ThemePreferences(private val context: Context) {
    private val key = stringPreferencesKey("theme_mode")

    val modeFlow: Flow<AppThemeMode> = context.themeDataStore.data.map { prefs ->
        AppThemeMode.fromCode(prefs[key])
    }

    suspend fun current(): AppThemeMode = modeFlow.first()

    suspend fun setMode(mode: AppThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[key] = mode.code
        }
    }
}

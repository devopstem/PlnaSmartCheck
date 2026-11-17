package com.plna.smartcheck.data.locale

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AppLanguage(val code: String, val nativeLabel: String) {
    RU("ru", "Русский"),
    BE("be", "Беларуская"),
    EN("en", "English"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: RU
    }
}

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_language",
)

class LanguageStore(private val context: Context) {
    private val key = stringPreferencesKey("language_code")

    val languageCodeFlow: Flow<String> = context.languageDataStore.data.map { prefs ->
        prefs[key] ?: AppLanguage.RU.code
    }

    suspend fun getLanguageCode(): String = languageCodeFlow.first()

    suspend fun setLanguageCode(code: String) {
        context.languageDataStore.edit { prefs ->
            prefs[key] = code
        }
    }
}

class LanguageController(
    private val application: Application,
    private val store: LanguageStore,
) {
    val languageFlow: Flow<AppLanguage> = store.languageCodeFlow.map { AppLanguage.fromCode(it) }

    suspend fun current(): AppLanguage = AppLanguage.fromCode(store.getLanguageCode())

    suspend fun setLanguage(language: AppLanguage) {
        store.setLanguageCode(language.code)
        // Locales API лучше вызывать с главного потока
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            apply(language)
        }
    }

    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.code),
        )
    }

    suspend fun restoreOnStart() {
        apply(current())
    }
}

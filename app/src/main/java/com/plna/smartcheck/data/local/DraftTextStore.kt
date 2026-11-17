package com.plna.smartcheck.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "receipt_drafts",
)

/**
 * Запоминает черновик текста чека между сессиями.
 */
class DraftTextStore(private val context: Context) {

    private val draftKey = stringPreferencesKey("receipt_text_draft")

    val draftText: Flow<String> = context.draftDataStore.data.map { prefs ->
        prefs[draftKey].orEmpty()
    }

    suspend fun save(text: String) {
        context.draftDataStore.edit { prefs ->
            prefs[draftKey] = text
        }
    }

    suspend fun clear() {
        context.draftDataStore.edit { prefs ->
            prefs.remove(draftKey)
        }
    }
}

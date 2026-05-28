package com.example.ledge.data.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsService(private val context: Context) {

    private val USE_WORD_SPACES = booleanPreferencesKey("use_word_spaces")
    private val SELECTED_DECK_ID = longPreferencesKey("selected_deck_id")

    val useWordSpaces: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_WORD_SPACES] ?: true
    }

    val selectedDeckId: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_DECK_ID]
    }

    suspend fun setUseWordSpaces(value: Boolean) {
        context.dataStore.edit { settings ->
            settings[USE_WORD_SPACES] = value
        }
    }

    suspend fun setSelectedDeckId(id: Long) {
        context.dataStore.edit { settings ->
            settings[SELECTED_DECK_ID] = id
        }
    }
}

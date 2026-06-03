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
    private val SHOW_ALL_PINYIN = booleanPreferencesKey("show_all_pinyin")
    private val SELECTED_DECK_ID = longPreferencesKey("selected_deck_id")
    private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    private val SPEECH_SPEED = floatPreferencesKey("speech_speed")

    val useWordSpaces: Flow<Boolean> = context.dataStore.data.map { it[USE_WORD_SPACES] ?: true }
    val showAllPinyin: Flow<Boolean> = context.dataStore.data.map { it[SHOW_ALL_PINYIN] ?: false }
    val selectedDeckId: Flow<Long?> = context.dataStore.data.map { it[SELECTED_DECK_ID] }
    val isDarkMode: Flow<Boolean?> = context.dataStore.data.map { it[IS_DARK_MODE] }
    val speechSpeed: Flow<Float> = context.dataStore.data.map { it[SPEECH_SPEED] ?: 1.0f }

    suspend fun setUseWordSpaces(value: Boolean) {
        context.dataStore.edit { it[USE_WORD_SPACES] = value }
    }

    suspend fun setShowAllPinyin(value: Boolean) {
        context.dataStore.edit { it[SHOW_ALL_PINYIN] = value }
    }

    suspend fun setSelectedDeckId(id: Long) {
        context.dataStore.edit { it[SELECTED_DECK_ID] = id }
    }

    suspend fun setDarkMode(value: Boolean) {
        context.dataStore.edit { it[IS_DARK_MODE] = value }
    }

    suspend fun setSpeechSpeed(value: Float) {
        context.dataStore.edit { it[SPEECH_SPEED] = value }
    }
}

package com.remover.background.AI.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    
    companion object {
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val THEME_KEY = stringPreferencesKey("theme")
    }
    
    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "en" // Default to English
    }
    
    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: "system" // Default to system theme
    }
    
    suspend fun setLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
        }
    }
    
    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }
}

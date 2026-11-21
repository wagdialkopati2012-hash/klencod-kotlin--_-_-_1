package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tender_prefs")

enum class AppThemeStyle(val arabicName: String) {
  LIQUID_ROYAL("الأزرق الملكي الزجاجي"),
  DEEP_NAVY("الكحلي المؤسسي"),
  OCEAN_TEAL("الأزرق الفيروزي");

  companion object {
    fun fromString(value: String?): AppThemeStyle =
      entries.firstOrNull { it.name == value } ?: LIQUID_ROYAL
  }
}

class AppPreferences(private val context: Context) {
  private object Keys {
    val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    val THEME_STYLE = stringPreferencesKey("theme_style")
    val REMEMBER_USERNAME = booleanPreferencesKey("remember_username")
    val SAVED_USERNAME = stringPreferencesKey("saved_username")
    val LOGGED_IN_USER_ID = longPreferencesKey("logged_in_user_id")
  }

  val isDarkMode: Flow<Boolean> =
    context.dataStore.data.map { prefs -> prefs[Keys.IS_DARK_MODE] ?: false }

  val themeStyle: Flow<AppThemeStyle> =
    context.dataStore.data.map { prefs -> AppThemeStyle.fromString(prefs[Keys.THEME_STYLE]) }

  val rememberUsername: Flow<Boolean> =
    context.dataStore.data.map { prefs -> prefs[Keys.REMEMBER_USERNAME] ?: true }

  val savedUsername: Flow<String> =
    context.dataStore.data.map { prefs -> prefs[Keys.SAVED_USERNAME] ?: "" }

  val loggedInUserId: Flow<Long?> =
    context.dataStore.data.map { prefs ->
      val id = prefs[Keys.LOGGED_IN_USER_ID] ?: -1L
      if (id > 0L) id else null
    }

  suspend fun setDarkMode(enabled: Boolean) {
    context.dataStore.edit { it[Keys.IS_DARK_MODE] = enabled }
  }

  suspend fun setThemeStyle(style: AppThemeStyle) {
    context.dataStore.edit { it[Keys.THEME_STYLE] = style.name }
  }

  suspend fun saveLoginPreference(remember: Boolean, username: String) {
    context.dataStore.edit { prefs ->
      prefs[Keys.REMEMBER_USERNAME] = remember
      prefs[Keys.SAVED_USERNAME] = if (remember) username else ""
    }
  }

  suspend fun setLoggedInUserId(userId: Long?) {
    context.dataStore.edit { prefs ->
      if (userId != null && userId > 0L) {
        prefs[Keys.LOGGED_IN_USER_ID] = userId
      } else {
        prefs.remove(Keys.LOGGED_IN_USER_ID)
      }
    }
  }
}

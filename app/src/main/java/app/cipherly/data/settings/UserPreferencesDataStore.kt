package app.cipherly.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.cipherly.domain.model.SecurityDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cipherlySettings: DataStore<Preferences> by preferencesDataStore(
    name = "cipherly_settings",
)

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM;

    companion object {
        fun fromStorage(value: String): ThemeMode =
            entries.firstOrNull { it.name == value } ?: DARK
    }
}

enum class AppLanguage {
    SYSTEM,
    PORTUGUESE,
    ENGLISH;

    companion object {
        fun fromStorage(value: String): AppLanguage =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val autoLockSeconds: Int = SecurityDefaults.DEFAULT_AUTO_LOCK_SECONDS,
    val clipboardClearSeconds: Int = SecurityDefaults.DEFAULT_CLIPBOARD_CLEAR_SECONDS,
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.cipherlySettings

    val preferences: Flow<UserPreferences> = dataStore.data.map { stored ->
        UserPreferences(
            themeMode = ThemeMode.fromStorage(stored[Keys.THEME] ?: ThemeMode.DARK.name),
            language = AppLanguage.fromStorage(stored[Keys.LANGUAGE] ?: AppLanguage.SYSTEM.name),
            autoLockSeconds = (stored[Keys.AUTO_LOCK_SECONDS]
                ?: SecurityDefaults.DEFAULT_AUTO_LOCK_SECONDS).coerceIn(15, 3_600),
            clipboardClearSeconds = (stored[Keys.CLIPBOARD_CLEAR_SECONDS]
                ?: SecurityDefaults.DEFAULT_CLIPBOARD_CLEAR_SECONDS).coerceIn(5, 300),
        )
    }

    suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = value.name }
    }

    suspend fun setLanguage(value: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = value.name }
    }

    suspend fun setAutoLockSeconds(value: Int) {
        require(value in 15..3_600) { "Auto-lock must be between 15 and 3600 seconds." }
        dataStore.edit { it[Keys.AUTO_LOCK_SECONDS] = value }
    }

    suspend fun setClipboardClearSeconds(value: Int) {
        require(value in 5..300) { "Clipboard timeout must be between 5 and 300 seconds." }
        dataStore.edit { it[Keys.CLIPBOARD_CLEAR_SECONDS] = value }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_LOCK_SECONDS = intPreferencesKey("auto_lock_seconds")
        val CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("clipboard_clear_seconds")
    }
}

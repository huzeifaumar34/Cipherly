package app.cipherly

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.cipherly.data.settings.ThemeMode
import app.cipherly.data.settings.UserPreferences
import app.cipherly.data.settings.UserPreferencesDataStore
import app.cipherly.ui.vault.LockScreen
import app.cipherly.ui.vault.OnboardingScreen
import app.cipherly.ui.vault.UnlockedScreen
import app.cipherly.ui.vault.VaultViewModel
import app.cipherly.vault.VaultState
import android.os.SystemClock
import androidx.activity.viewModels
import app.cipherly.ui.theme.CipherlyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferencesDataStore

    private val vaultViewModel: VaultViewModel by viewModels()
    private var backgroundedAt = 0L
    private var autoLockSeconds = UserPreferences().autoLockSeconds

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        if (backgroundedAt != 0L && away >= autoLockSeconds * 1_000L) vaultViewModel.lock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }

        setContent {
            val prefs by userPreferences.preferences.collectAsState(initial = UserPreferences())
            autoLockSeconds = prefs.autoLockSeconds
            val vaultState by vaultViewModel.vaultState.collectAsState()
            val ui by vaultViewModel.ui.collectAsState()
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            CipherlyTheme(darkTheme = darkTheme) {
                when (vaultState) {
                    VaultState.NO_VAULT -> OnboardingScreen(ui, vaultViewModel::createVault)
                    VaultState.LOCKED -> LockScreen(ui, vaultViewModel::unlock)
                    VaultState.UNLOCKED -> UnlockedScreen(vaultViewModel::lock)
                }
            }
        }
    }
}

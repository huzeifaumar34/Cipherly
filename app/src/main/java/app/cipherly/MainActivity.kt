package app.cipherly

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import app.cipherly.biometric.BiometricUnlock
import app.cipherly.ui.backup.BackupSection
import app.cipherly.ui.backup.BackupViewModel
import java.time.LocalDate
import app.cipherly.clipboard.ClipboardHelper
import app.cipherly.domain.model.Choices
import kotlinx.coroutines.launch
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
import androidx.activity.compose.BackHandler
import app.cipherly.ui.items.ItemEditScreen
import app.cipherly.ui.items.ItemListScreen
import app.cipherly.ui.items.ItemsScreen
import app.cipherly.ui.items.ItemsViewModel
import app.cipherly.ui.vault.VaultViewModel
import app.cipherly.vault.VaultState
import android.os.SystemClock
import androidx.activity.viewModels
import app.cipherly.ui.theme.CipherlyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferencesDataStore

    @Inject
    lateinit var biometric: BiometricUnlock

    @Inject
    lateinit var clipboard: ClipboardHelper

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vaultViewModel.lock()
        }
    }

    private fun copyToClipboard(text: String) {
        clipboard.copy(text)
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    private val vaultViewModel: VaultViewModel by viewModels()
    private val itemsViewModel: ItemsViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()

    private val createBackupFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { backupViewModel.exportTo(it) }

    private val openBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { backupViewModel.chooseImport(it) }
    private var backgroundedAt = 0L
    private var autoLockSeconds = UserPreferences().autoLockSeconds

    private fun enableBiometric() {
        val key = vaultViewModel.keyCopy() ?: return
        biometric.enable(
            this, key,
            getString(R.string.biometric_prompt_title),
            getString(R.string.action_cancel),
        ) { }
    }

    private fun unlockWithBiometric() {
        biometric.unlock(
            this,
            getString(R.string.biometric_prompt_title),
            getString(R.string.action_cancel),
            onKey = vaultViewModel::unlockWithKey,
            onFailure = { },
        )
    }

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

        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            val prefs by userPreferences.preferences.collectAsState(initial = UserPreferences())
            autoLockSeconds = prefs.autoLockSeconds
            val vaultState by vaultViewModel.vaultState.collectAsState()
            val ui by vaultViewModel.ui.collectAsState()
            val pinEnabled by vaultViewModel.pinEnabled.collectAsState()
            val biometricEnabled by biometric.enabled.collectAsState()
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            CipherlyTheme(darkTheme = darkTheme) {
                when (vaultState) {
                    VaultState.NO_VAULT -> OnboardingScreen(ui, vaultViewModel::createVault)
                    VaultState.LOCKED -> LockScreen(
                        state = ui,
                        pinEnabled = pinEnabled,
                        biometricEnabled = biometricEnabled,
                        onUnlock = vaultViewModel::unlock,
                        onUnlockPin = vaultViewModel::unlockWithPin,
                        onBiometric = ::unlockWithBiometric,
                    )
                    VaultState.UNLOCKED -> {
                        val screen by itemsViewModel.screen.collectAsState()
                        val items by itemsViewModel.items.collectAsState()
                        val query by itemsViewModel.query.collectAsState()
                        val editing by itemsViewModel.editing.collectAsState()
                        val itemError by itemsViewModel.error.collectAsState()
                        val backupState by backupViewModel.state.collectAsState()
                        BackHandler(enabled = screen != ItemsScreen.List) { itemsViewModel.back() }
                        when (screen) {
                            ItemsScreen.List -> ItemListScreen(
                                items = items,
                                query = query,
                                onQuery = itemsViewModel::setQuery,
                                onAdd = itemsViewModel::openNew,
                                onOpen = itemsViewModel::openEdit,
                                onSecurity = itemsViewModel::openSecurity,
                                onLock = vaultViewModel::lock,
                            )
                            ItemsScreen.Edit -> ItemEditScreen(
                                initial = editing,
                                error = itemError,
                                onSave = itemsViewModel::save,
                                onDelete = itemsViewModel::delete,
                                onCopy = ::copyToClipboard,
                                onBack = itemsViewModel::back,
                            )
                            ItemsScreen.Security -> UnlockedScreen(
                                state = ui,
                                pinEnabled = pinEnabled,
                                biometricEnabled = biometricEnabled,
                                biometricAvailable = biometric.isAvailable(),
                                autoLockSeconds = prefs.autoLockSeconds,
                                clipboardSeconds = prefs.clipboardClearSeconds,
                                onCycleAutoLock = {
                                    lifecycleScope.launch {
                                        userPreferences.setAutoLockSeconds(
                                            Choices.next(Choices.AUTO_LOCK_SECONDS, prefs.autoLockSeconds),
                                        )
                                    }
                                },
                                onCycleClipboard = {
                                    lifecycleScope.launch {
                                        userPreferences.setClipboardClearSeconds(
                                            Choices.next(Choices.CLIPBOARD_SECONDS, prefs.clipboardClearSeconds),
                                        )
                                    }
                                },
                                onLock = vaultViewModel::lock,
                                onBack = itemsViewModel::back,
                                extra = {
                                    BackupSection(
                                        state = backupState,
                                        onExport = { pw, confirm ->
                                            if (backupViewModel.prepareExport(pw, confirm)) {
                                                createBackupFile.launch("cipherly-backup-${LocalDate.now()}.cpbk")
                                            }
                                        },
                                        onImport = { openBackupFile.launch(arrayOf("*/*")) },
                                        onImportPassword = backupViewModel::importWith,
                                        onCancelImport = backupViewModel::cancelImport,
                                    )
                                },
                                onEnablePin = { pin, confirm -> vaultViewModel.enablePin(pin, confirm) { } },
                                onDisablePin = vaultViewModel::disablePin,
                                onEnableBiometric = ::enableBiometric,
                                onDisableBiometric = biometric::disable,
                            )
                        }
                    }
                }
            }
        }
    }
}

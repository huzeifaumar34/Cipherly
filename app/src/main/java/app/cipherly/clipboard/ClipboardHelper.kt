package app.cipherly.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import app.cipherly.data.settings.UserPreferencesDataStore
import app.cipherly.vault.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies secrets to the clipboard, marked as sensitive, and clears them after the configured
 * delay or as soon as the vault locks. If the user has copied something else in the meantime,
 * that newer content is left alone. The timer lives in the app process, so if Android kills the
 * process before it fires the clipboard is cleared the next time the vault locks.
 */
@Singleton
class ClipboardHelper @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: UserPreferencesDataStore,
    vault: VaultRepository,
) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timer: Job? = null

    init {
        vault.addOnLockListener { clearNow() }
    }

    @Synchronized
    fun copy(text: String) {
        val clip = ClipData.newPlainText(LABEL, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        timer?.cancel()
        timer = scope.launch {
            val seconds = preferences.preferences.first().clipboardClearSeconds
            delay(seconds * 1_000L)
            clearNow()
        }
    }

    @Synchronized
    fun clearNow() {
        val pending = timer ?: return
        pending.cancel()
        timer = null
        // If the description can't be read (app in background), clear anyway: safer for secrets.
        val label = try {
            manager.primaryClipDescription?.label
        } catch (e: SecurityException) {
            null
        }
        if (label != null && label != LABEL) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private companion object {
        const val LABEL = "cipherly"
    }
}

package app.cipherly.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cipherly.backup.BackupCodec
import app.cipherly.backup.BackupError
import app.cipherly.backup.BackupException
import app.cipherly.data.db.VaultSession
import app.cipherly.vault.PasswordCheck
import app.cipherly.vault.PasswordPolicy
import app.cipherly.vault.VaultRepository
import app.cipherly.vault.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

sealed interface BackupMessage {
    data class ExportOk(val count: Int) : BackupMessage
    data class ImportOk(val added: Int, val updated: Int, val skipped: Int) : BackupMessage
    data object TooShort : BackupMessage
    data object Mismatch : BackupMessage
    data object WrongPassword : BackupMessage
    data object BadFile : BackupMessage
    data object TooLarge : BackupMessage
    data object Failed : BackupMessage
}

data class BackupUiState(
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    val askImportPassword: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val session: VaultSession,
    @ApplicationContext private val context: Context,
    vault: VaultRepository,
) : ViewModel() {

    private val codec = BackupCodec()
    private var pendingExportPassword: CharArray? = null
    private var pendingImportUri: Uri? = null

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vault.state.collect { if (it != VaultState.UNLOCKED) reset() }
        }
    }

    /** Validates the backup password. When it returns true, the caller must ask for a destination file. */
    fun prepareExport(password: CharArray, confirmation: CharArray): Boolean {
        val message = when (PasswordPolicy.check(password, confirmation)) {
            PasswordCheck.TOO_SHORT -> BackupMessage.TooShort
            PasswordCheck.MISMATCH -> BackupMessage.Mismatch
            PasswordCheck.OK -> null
        }
        confirmation.fill('\u0000')
        if (message != null) {
            password.fill('\u0000')
            _state.value = _state.value.copy(message = message)
            return false
        }
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = password
        _state.value = _state.value.copy(message = null)
        return true
    }

    fun exportTo(uri: Uri?) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri == null || password == null) {
            password?.fill('\u0000')
            return
        }
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    val items = session.allItems()
                    val bytes = codec.encode(items, password)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: throw IOException("Could not open the destination file")
                    BackupMessage.ExportOk(items.size)
                }
            } catch (e: Exception) {
                BackupMessage.Failed
            } finally {
                password.fill('\u0000')
            }
            _state.value = _state.value.copy(busy = false, message = message)
        }
    }

    fun chooseImport(uri: Uri?) {
        if (uri == null) return
        pendingImportUri = uri
        _state.value = _state.value.copy(askImportPassword = true, message = null)
    }

    fun cancelImport() {
        pendingImportUri = null
        _state.value = _state.value.copy(askImportPassword = false)
    }

    fun importWith(password: CharArray) {
        val uri = pendingImportUri
        pendingImportUri = null
        if (uri == null) {
            password.fill('\u0000')
            return
        }
        _state.value = _state.value.copy(busy = true, askImportPassword = false, message = null)
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    val bytes = readLimited(uri) ?: return@withContext BackupMessage.TooLarge
                    val items = codec.decode(bytes, password)
                    val plan = session.importItems(items)
                    BackupMessage.ImportOk(plan.added, plan.updated, plan.skipped)
                }
            } catch (e: BackupException) {
                when (e.error) {
                    BackupError.WRONG_PASSWORD_OR_CORRUPT -> BackupMessage.WrongPassword
                    BackupError.BAD_FILE -> BackupMessage.BadFile
                }
            } catch (e: Exception) {
                BackupMessage.Failed
            } finally {
                password.fill('\u0000')
            }
            _state.value = _state.value.copy(busy = false, message = message)
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Reads at most [MAX_BYTES]; returns null if the file is bigger. */
    private fun readLimited(uri: Uri): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the file")
        input.use {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) return null
            }
            return out.toByteArray()
        }
    }

    private fun reset() {
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = null
        pendingImportUri = null
        _state.value = BackupUiState()
    }

    private companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}

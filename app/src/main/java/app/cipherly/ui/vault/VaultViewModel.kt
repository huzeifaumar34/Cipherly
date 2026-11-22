package app.cipherly.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cipherly.vault.PasswordCheck
import app.cipherly.vault.PasswordPolicy
import app.cipherly.vault.PinResult
import app.cipherly.vault.PinUnlockStore
import app.cipherly.vault.UnlockResult
import app.cipherly.vault.VaultRepository
import app.cipherly.vault.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VaultError { TOO_SHORT, MISMATCH, WRONG_PASSWORD, THROTTLED, CORRUPTED, SAVE_FAILED, PIN_INVALID, PIN_WRONG, PIN_DISABLED }

data class VaultUiState(
    val busy: Boolean = false,
    val error: VaultError? = null,
    val retrySeconds: Long = 0,
    val pinRemaining: Int = 0,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
) : ViewModel() {

    val vaultState: StateFlow<VaultState> = repository.state
    val pinEnabled: StateFlow<Boolean> = repository.pinEnabled

    private val _ui = MutableStateFlow(VaultUiState())
    val ui: StateFlow<VaultUiState> = _ui.asStateFlow()

    fun createVault(password: CharArray, confirmation: CharArray) {
        when (PasswordPolicy.check(password, confirmation)) {
            PasswordCheck.TOO_SHORT -> return fail(VaultError.TOO_SHORT)
            PasswordCheck.MISMATCH -> return fail(VaultError.MISMATCH)
            PasswordCheck.OK -> Unit
        }
        launchBusy {
            try {
                repository.create(password)
                _ui.value = VaultUiState()
            } catch (e: java.io.IOException) {
                fail(VaultError.SAVE_FAILED)
            } finally {
                password.fill('\u0000')
                confirmation.fill('\u0000')
            }
        }
    }

    fun unlock(password: CharArray) {
        launchBusy {
            try {
                when (val result = repository.unlock(password)) {
                    UnlockResult.Success -> _ui.value = VaultUiState()
                    UnlockResult.WrongPassword -> fail(VaultError.WRONG_PASSWORD)
                    UnlockResult.Corrupted -> fail(VaultError.CORRUPTED)
                    is UnlockResult.Throttled -> _ui.value = VaultUiState(
                        error = VaultError.THROTTLED,
                        retrySeconds = (result.retryAfterMillis + 999) / 1000,
                    )
                }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun lock() = repository.lock()

    fun enablePin(pin: CharArray, confirmation: CharArray, onSuccess: () -> Unit) {
        if (!PinUnlockStore.isValidPin(pin)) {
            wipe(pin, confirmation)
            return fail(VaultError.PIN_INVALID)
        }
        if (!pin.contentEquals(confirmation)) {
            wipe(pin, confirmation)
            return fail(VaultError.MISMATCH)
        }
        launchBusy {
            try {
                if (repository.enablePin(pin)) {
                    _ui.value = VaultUiState()
                    onSuccess()
                } else {
                    fail(VaultError.SAVE_FAILED)
                }
            } catch (e: Exception) {
                fail(VaultError.SAVE_FAILED)
            } finally {
                wipe(pin, confirmation)
            }
        }
    }

    fun unlockWithPin(pin: CharArray) {
        launchBusy {
            try {
                when (val result = repository.unlockWithPin(pin)) {
                    is PinResult.Success -> _ui.value = VaultUiState()
                    is PinResult.Wrong -> _ui.value =
                        VaultUiState(error = VaultError.PIN_WRONG, pinRemaining = result.remaining)
                    PinResult.Disabled -> fail(VaultError.PIN_DISABLED)
                }
            } finally {
                wipe(pin)
            }
        }
    }

    fun disablePin() = repository.disablePin()

    fun keyCopy(): ByteArray? = repository.keyCopy()

    fun unlockWithKey(key: ByteArray) = repository.unlockWithKey(key)

    private fun wipe(vararg arrays: CharArray) = arrays.forEach { it.fill('\u0000') }

    private fun fail(error: VaultError) {
        _ui.value = VaultUiState(error = error)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch { block() }
    }
}

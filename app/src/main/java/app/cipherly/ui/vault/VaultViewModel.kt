package app.cipherly.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cipherly.vault.PasswordCheck
import app.cipherly.vault.PasswordPolicy
import app.cipherly.vault.UnlockResult
import app.cipherly.vault.VaultRepository
import app.cipherly.vault.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VaultError { TOO_SHORT, MISMATCH, WRONG_PASSWORD, THROTTLED, CORRUPTED, SAVE_FAILED }

data class VaultUiState(
    val busy: Boolean = false,
    val error: VaultError? = null,
    val retrySeconds: Long = 0,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
) : ViewModel() {

    val vaultState: StateFlow<VaultState> = repository.state

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

    private fun fail(error: VaultError) {
        _ui.value = VaultUiState(error = error)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch { block() }
    }
}

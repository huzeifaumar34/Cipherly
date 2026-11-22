package app.cipherly.vault

import android.content.Context
import app.cipherly.crypto.CryptoException
import app.cipherly.crypto.KeystoreSecretBox
import app.cipherly.crypto.VaultKeyWrapper
import app.cipherly.crypto.WrappedVaultKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

enum class VaultState { NO_VAULT, LOCKED, UNLOCKED }

sealed interface UnlockResult {
    data object Success : UnlockResult
    data object WrongPassword : UnlockResult
    data class Throttled(val retryAfterMillis: Long) : UnlockResult
    data object Corrupted : UnlockResult
}

/**
 * Owns the wrapped vault key on disk and the unlocked key in memory.
 * The master password is never persisted; the vault key only lives in RAM while unlocked.
 */
@Singleton
class VaultRepository internal constructor(
    private val file: File,
    private val wrapper: VaultKeyWrapper,
    private val clock: () -> Long,
    private val pin: PinUnlockStore? = null,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        file = File(context.filesDir, KEY_FILE_NAME),
        wrapper = VaultKeyWrapper(),
        clock = System::currentTimeMillis,
        pin = PinUnlockStore(
            bundleFile = File(context.filesDir, "pin_unlock.bin"),
            counterFile = File(context.filesDir, "pin_attempts.txt"),
            box = KeystoreSecretBox("cipherly_pin_box"),
            wrapper = VaultKeyWrapper(),
        ),
    )

    private val _state = MutableStateFlow(if (file.exists()) VaultState.LOCKED else VaultState.NO_VAULT)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _pinEnabled = MutableStateFlow(pin?.isEnabled == true)
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()

    /** Called synchronously every time the vault locks, so dependants can drop their key. */
    fun addOnLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    @Volatile private var vaultKey: ByteArray? = null
    private var failures = 0
    private var blockedUntil = 0L

    suspend fun create(password: CharArray) = withContext(Dispatchers.Default) {
        val (bundle, key) = wrapper.create(password)
        writeAtomically(bundle.toBytes())
        vaultKey = key
        failures = 0
        _state.value = VaultState.UNLOCKED
    }

    suspend fun unlock(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val now = clock()
        if (now < blockedUntil) return@withContext UnlockResult.Throttled(blockedUntil - now)
        val bundle = try {
            WrappedVaultKey.fromBytes(file.readBytes())
        } catch (e: CryptoException) {
            return@withContext UnlockResult.Corrupted
        } catch (e: IOException) {
            return@withContext UnlockResult.Corrupted
        }
        try {
            vaultKey = wrapper.unlock(password, bundle)
            failures = 0
            _state.value = VaultState.UNLOCKED
            UnlockResult.Success
        } catch (e: CryptoException) {
            failures++
            blockedUntil = clock() + backoffMillis(failures)
            UnlockResult.WrongPassword
        }
    }

    /** Sets up a PIN for quick unlock. Requires the vault to be unlocked. Returns false otherwise. */
    suspend fun enablePin(code: CharArray): Boolean = withContext(Dispatchers.Default) {
        val key = vaultKey ?: return@withContext false
        val store = pin ?: return@withContext false
        store.enable(code, key)
        _pinEnabled.value = true
        true
    }

    suspend fun unlockWithPin(code: CharArray): PinResult = withContext(Dispatchers.Default) {
        val store = pin ?: return@withContext PinResult.Disabled
        val result = store.unlock(code)
        when (result) {
            is PinResult.Success -> {
                vaultKey = result.vaultKey
                failures = 0
                _state.value = VaultState.UNLOCKED
            }
            PinResult.Disabled -> _pinEnabled.value = false
            is PinResult.Wrong -> Unit
        }
        result
    }

    fun disablePin() {
        pin?.disable()
        _pinEnabled.value = false
    }

    /** A copy of the live vault key for a quick-unlock method to wrap; the caller must wipe it. */
    fun keyCopy(): ByteArray? = vaultKey?.copyOf()

    /** Unlocks with a key recovered by a quick-unlock method (e.g. biometrics). */
    fun unlockWithKey(key: ByteArray) {
        vaultKey = key
        failures = 0
        _state.value = VaultState.UNLOCKED
    }

    /** Wipes the in-memory key. The vault must be unlocked again with the master password. */
    fun lock() {
        vaultKey?.fill(0)
        vaultKey = null
        lockListeners.forEach { it() }
        if (_state.value == VaultState.UNLOCKED) _state.value = VaultState.LOCKED
    }

    private fun writeAtomically(bytes: ByteArray) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Could not save the vault key")
        }
    }

    internal companion object {
        const val KEY_FILE_NAME = "vault_key.bin"

        /** 0 for the first 2 failures, then 2s, 4s, 8s ... capped at 5 minutes. */
        fun backoffMillis(failures: Int): Long {
            if (failures <= 2) return 0
            val seconds = 1L shl (failures - 2).coerceAtMost(20)
            return (seconds * 1_000).coerceAtMost(300_000)
        }
    }
}

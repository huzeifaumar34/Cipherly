package app.cipherly.vault

import app.cipherly.crypto.CryptoException
import app.cipherly.crypto.SecretBox
import app.cipherly.crypto.VaultKeyWrapper
import app.cipherly.crypto.WrappedVaultKey
import app.cipherly.domain.model.SecurityDefaults
import java.io.File
import java.io.IOException

sealed interface PinResult {
    class Success(val vaultKey: ByteArray) : PinResult
    data class Wrong(val remaining: Int) : PinResult

    /** The PIN is not set up, or it was wiped after too many failures. */
    data object Disabled : PinResult
}

/**
 * Quick unlock with a 6-digit PIN. A PIN alone is too weak to protect a key, so the wrapped
 * vault key is also encrypted by a hardware-backed Keystore key ([SecretBox]), and the number
 * of failed attempts is persisted. After [SecurityDefaults.MAX_PIN_FAILURES] failures the PIN
 * is wiped and only the master password works. The counter is incremented before each check.
 */
class PinUnlockStore(
    private val bundleFile: File,
    private val counterFile: File,
    private val box: SecretBox,
    private val wrapper: VaultKeyWrapper,
) {
    val isEnabled: Boolean get() = bundleFile.exists()

    @Synchronized
    fun enable(pin: CharArray, vaultKey: ByteArray) {
        require(isValidPin(pin)) { "PIN must be ${SecurityDefaults.PIN_LENGTH} digits." }
        val bundle = wrapper.wrapKey(vaultKey, pin)
        // Counter first: a bundle without a counter is treated as tampering.
        writeAtomically(counterFile, "0".toByteArray())
        writeAtomically(bundleFile, box.seal(bundle.toBytes()))
    }

    @Synchronized
    fun unlock(pin: CharArray): PinResult {
        if (!bundleFile.exists()) return PinResult.Disabled
        val used = readCounter()
        if (used >= MAX) {
            disable()
            return PinResult.Disabled
        }
        writeAtomically(counterFile, (used + 1).toString().toByteArray())
        val key = try {
            wrapper.unlock(pin, WrappedVaultKey.fromBytes(box.open(bundleFile.readBytes())))
        } catch (e: CryptoException) {
            return failed(used + 1)
        } catch (e: IOException) {
            return failed(used + 1)
        }
        writeAtomically(counterFile, "0".toByteArray())
        return PinResult.Success(key)
    }

    @Synchronized
    fun disable() {
        bundleFile.delete()
        counterFile.delete()
        box.destroy()
    }

    private fun failed(total: Int): PinResult {
        val remaining = MAX - total
        if (remaining <= 0) {
            disable()
            return PinResult.Disabled
        }
        return PinResult.Wrong(remaining)
    }

    /** A missing or unreadable counter counts as exhausted. */
    private fun readCounter(): Int = try {
        counterFile.readText().trim().toInt()
    } catch (e: IOException) {
        MAX
    } catch (e: NumberFormatException) {
        MAX
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Could not save ${target.name}")
        }
    }

    companion object {
        private const val MAX = SecurityDefaults.MAX_PIN_FAILURES

        fun isValidPin(pin: CharArray): Boolean =
            pin.size == SecurityDefaults.PIN_LENGTH && pin.all { it in '0'..'9' }
    }
}

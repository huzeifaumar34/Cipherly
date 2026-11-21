package app.cipherly.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VaultKeyWrapperTest {
    private val wrapper = VaultKeyWrapper(defaultParams = Argon2Params(memoryKiB = 64, iterations = 1))
    private val password = "uma-senha-mestra-longa".toCharArray()

    @Test
    fun `unlock returns the vault key created with the same password`() {
        val (bundle, key) = wrapper.create(password)
        assertEquals(32, key.size)
        assertArrayEquals(key, wrapper.unlock(password, bundle))
    }

    @Test
    fun `wrong password is rejected`() {
        val (bundle, _) = wrapper.create(password)
        assertThrows(CryptoException::class.java) { wrapper.unlock("errada-errada-1".toCharArray(), bundle) }
    }

    @Test
    fun `change password keeps the same vault key and invalidates the old password`() {
        val (bundle, key) = wrapper.create(password)
        val newPassword = "nova-senha-mestra-1".toCharArray()
        val changed = wrapper.changePassword(password, newPassword, bundle)
        assertArrayEquals(key, wrapper.unlock(newPassword, changed))
        assertThrows(CryptoException::class.java) { wrapper.unlock(password, changed) }
        assertFalse(bundle.salt.contentEquals(changed.salt))
    }

    @Test
    fun `bundle survives serialization`() {
        val (bundle, key) = wrapper.create(password)
        val restored = WrappedVaultKey.fromBytes(bundle.toBytes())
        assertEquals(bundle.params, restored.params)
        assertArrayEquals(key, wrapper.unlock(password, restored))
    }

    @Test
    fun `corrupted or truncated bundles are rejected`() {
        val bytes = wrapper.create(password).first.toBytes()
        assertThrows(CryptoException::class.java) { WrappedVaultKey.fromBytes(bytes.copyOf(10)) }
        val tampered = bytes.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        assertThrows(CryptoException::class.java) {
            wrapper.unlock(password, WrappedVaultKey.fromBytes(tampered))
        }
        val badVersion = bytes.copyOf().also { it[0] = 9 }
        assertThrows(CryptoException::class.java) { WrappedVaultKey.fromBytes(badVersion) }
    }
}

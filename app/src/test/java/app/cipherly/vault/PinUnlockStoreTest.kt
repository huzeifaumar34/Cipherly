package app.cipherly.vault

import app.cipherly.crypto.AesGcmCipher
import app.cipherly.crypto.Argon2Params
import app.cipherly.crypto.SecretBox
import app.cipherly.crypto.VaultKeyWrapper
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PinUnlockStoreTest {
    @TempDir
    lateinit var dir: File

    private class FakeBox : SecretBox {
        private val cipher = AesGcmCipher()
        private val key = ByteArray(32) { 5 }
        var destroyed = false
        override fun seal(plain: ByteArray) = cipher.encrypt(key, plain)
        override fun open(sealed: ByteArray) = cipher.decrypt(key, sealed)
        override fun destroy() { destroyed = true }
    }

    private val vaultKey = ByteArray(32) { (it * 3).toByte() }
    private val box = FakeBox()

    private fun store() = PinUnlockStore(
        bundleFile = File(dir, "pin.bin"),
        counterFile = File(dir, "attempts.txt"),
        box = box,
        wrapper = VaultKeyWrapper(defaultParams = Argon2Params(memoryKiB = 64, iterations = 1)),
    )

    @Test
    fun `correct pin returns the vault key`() {
        val store = store()
        store.enable("123456".toCharArray(), vaultKey)
        assertTrue(store.isEnabled)
        val result = store.unlock("123456".toCharArray())
        assertTrue(result is PinResult.Success)
        assertArrayEquals(vaultKey, (result as PinResult.Success).vaultKey)
    }

    @Test
    fun `wrong pins count down and the fifth wipes the pin`() {
        val store = store()
        store.enable("123456".toCharArray(), vaultKey)
        for (expected in 4 downTo 1) {
            assertEquals(PinResult.Wrong(expected), store.unlock("000000".toCharArray()))
        }
        assertEquals(PinResult.Disabled, store.unlock("000000".toCharArray()))
        assertFalse(store.isEnabled)
        assertTrue(box.destroyed)
        assertEquals(PinResult.Disabled, store.unlock("123456".toCharArray()))
    }

    @Test
    fun `failures persist across instances and success resets them`() {
        store().apply {
            enable("123456".toCharArray(), vaultKey)
            unlock("000000".toCharArray())
            unlock("000000".toCharArray())
        }
        val reopened = store()
        assertEquals(PinResult.Wrong(2), reopened.unlock("000000".toCharArray()))
        assertTrue(reopened.unlock("123456".toCharArray()) is PinResult.Success)
        assertEquals(PinResult.Wrong(4), reopened.unlock("000000".toCharArray()))
    }

    @Test
    fun `a deleted attempts counter is treated as tampering`() {
        val store = store()
        store.enable("123456".toCharArray(), vaultKey)
        File(dir, "attempts.txt").delete()
        assertEquals(PinResult.Disabled, store.unlock("123456".toCharArray()))
        assertFalse(store.isEnabled)
    }

    @Test
    fun `invalid pins are rejected`() {
        val store = store()
        assertThrows(IllegalArgumentException::class.java) { store.enable("12345".toCharArray(), vaultKey) }
        assertThrows(IllegalArgumentException::class.java) { store.enable("12345a".toCharArray(), vaultKey) }
    }
}

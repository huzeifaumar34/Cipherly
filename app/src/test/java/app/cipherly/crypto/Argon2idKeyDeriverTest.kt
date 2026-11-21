package app.cipherly.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class Argon2idKeyDeriverTest {
    private val deriver = Argon2idKeyDeriver()
    private val fast = Argon2Params(memoryKiB = 64, iterations = 1, parallelism = 1)
    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun `derivation is deterministic and 32 bytes long`() {
        val a = deriver.derive("senha-longa-123".toCharArray(), salt, fast)
        val b = deriver.derive("senha-longa-123".toCharArray(), salt, fast)
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
    }

    @Test
    fun `different password or salt changes the key`() {
        val base = deriver.derive("senha-longa-123".toCharArray(), salt, fast)
        assertFalse(base.contentEquals(deriver.derive("outra-senha-123".toCharArray(), salt, fast)))
        assertFalse(base.contentEquals(deriver.derive("senha-longa-123".toCharArray(), ByteArray(16) { 7 }, fast)))
    }

    @Test
    fun `new salts are random`() {
        assertFalse(deriver.newSalt().contentEquals(deriver.newSalt()))
    }
}

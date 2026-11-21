package app.cipherly.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AesGcmCipherTest {
    private val cipher = AesGcmCipher()
    private val key = ByteArray(32) { it.toByte() }
    private val plaintext = "segredo".toByteArray()

    @Test
    fun `round trip returns the original plaintext`() {
        val sealed = cipher.encrypt(key, plaintext)
        assertArrayEquals(plaintext, cipher.decrypt(key, sealed))
    }

    @Test
    fun `same input produces different ciphertexts`() {
        assertFalse(cipher.encrypt(key, plaintext).contentEquals(cipher.encrypt(key, plaintext)))
    }

    @Test
    fun `wrong key is rejected`() {
        val sealed = cipher.encrypt(key, plaintext)
        assertThrows(CryptoException::class.java) { cipher.decrypt(ByteArray(32) { 9 }, sealed) }
    }

    @Test
    fun `tampered payload is rejected`() {
        val sealed = cipher.encrypt(key, plaintext)
        sealed[sealed.size - 1] = (sealed.last().toInt() xor 1).toByte()
        assertThrows(CryptoException::class.java) { cipher.decrypt(key, sealed) }
    }

    @Test
    fun `mismatched aad is rejected`() {
        val sealed = cipher.encrypt(key, plaintext, "a".toByteArray())
        assertThrows(CryptoException::class.java) { cipher.decrypt(key, sealed, "b".toByteArray()) }
    }

    @Test
    fun `short payload and bad version are rejected`() {
        assertThrows(CryptoException::class.java) { cipher.decrypt(key, ByteArray(5)) }
        val sealed = cipher.encrypt(key, plaintext).also { it[0] = 2 }
        assertThrows(CryptoException::class.java) { cipher.decrypt(key, sealed) }
    }

    @Test
    fun `invalid key length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { cipher.encrypt(ByteArray(16), plaintext) }
    }
}

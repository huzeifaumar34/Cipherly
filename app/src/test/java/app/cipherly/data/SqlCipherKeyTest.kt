package app.cipherly.data

import app.cipherly.data.db.SqlCipherKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SqlCipherKeyTest {
    @Test
    fun `key is formatted as a raw hex literal`() {
        val key = ByteArray(32) { it.toByte() }
        val text = String(SqlCipherKey.passphrase(key), Charsets.US_ASCII)
        assertEquals("x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'", text)
    }

    @Test
    fun `high bytes use lowercase hex`() {
        val key = ByteArray(32) { 0xAB.toByte() }
        assertEquals("ab".repeat(32), String(SqlCipherKey.passphrase(key), Charsets.US_ASCII).drop(2).dropLast(1))
    }

    @Test
    fun `wrong key length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SqlCipherKey.passphrase(ByteArray(16)) }
    }
}

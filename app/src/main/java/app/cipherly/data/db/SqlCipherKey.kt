package app.cipherly.data.db

/**
 * Formats a 256-bit key as the SQLCipher raw-key literal `x'<64 hex digits>'`, which skips
 * SQLCipher's own password KDF (the key is already random). Built without intermediate Strings
 * so the returned array can be wiped.
 */
object SqlCipherKey {
    private val HEX = "0123456789abcdef".toByteArray(Charsets.US_ASCII)

    fun passphrase(key: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes." }
        val out = ByteArray(2 + key.size * 2 + 1)
        out[0] = 'x'.code.toByte()
        out[1] = '\''.code.toByte()
        key.forEachIndexed { i, b ->
            val v = b.toInt() and 0xff
            out[2 + i * 2] = HEX[v ushr 4]
            out[3 + i * 2] = HEX[v and 0x0f]
        }
        out[out.size - 1] = '\''.code.toByte()
        return out
    }
}

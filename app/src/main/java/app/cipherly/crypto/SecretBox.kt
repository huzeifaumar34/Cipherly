package app.cipherly.crypto

/** Encrypts small blobs with a key that never leaves secure hardware (Android Keystore). */
interface SecretBox {
    fun seal(plain: ByteArray): ByteArray

    /** @throws CryptoException if the data was altered or the key is gone. */
    fun open(sealed: ByteArray): ByteArray

    /** Deletes the underlying key. */
    fun destroy()
}

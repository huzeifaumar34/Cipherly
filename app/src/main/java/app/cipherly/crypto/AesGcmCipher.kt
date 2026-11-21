package app.cipherly.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM authenticated encryption.
 *
 * Output layout: `version (1 byte) || iv (12 bytes) || ciphertext || tag (16 bytes)`.
 * A fresh random 96-bit IV is generated for every call. The version byte and the
 * caller-supplied [aad] are authenticated, so tampering with either is detected.
 */
class AesGcmCipher(
    private val random: SecureRandom = SecureRandom(),
) {

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        requireKey(key)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(headerAad(aad))
            val sealed = cipher.doFinal(plaintext)
            return ByteArray(HEADER_BYTES + sealed.size).also {
                it[0] = FORMAT_VERSION
                iv.copyInto(it, destinationOffset = 1)
                sealed.copyInto(it, destinationOffset = HEADER_BYTES)
            }
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Encryption failed", e)
        }
    }

    fun decrypt(key: ByteArray, payload: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        requireKey(key)
        if (payload.size < HEADER_BYTES + TAG_BYTES) throw CryptoException("Payload is too short")
        if (payload[0] != FORMAT_VERSION) throw CryptoException("Unsupported payload version")
        try {
            val iv = payload.copyOfRange(1, HEADER_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(headerAad(aad))
            return cipher.doFinal(payload, HEADER_BYTES, payload.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            // Wrong key, wrong AAD and tampering are deliberately indistinguishable.
            throw CryptoException("Decryption failed", e)
        }
    }

    private fun requireKey(key: ByteArray) {
        require(key.size == KEY_BYTES) { "Key must be $KEY_BYTES bytes." }
    }

    private fun headerAad(aad: ByteArray): ByteArray = byteArrayOf(FORMAT_VERSION) + aad

    companion object {
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        private const val TAG_BITS = TAG_BYTES * 8
        private const val HEADER_BYTES = 1 + IV_BYTES
        private const val FORMAT_VERSION: Byte = 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

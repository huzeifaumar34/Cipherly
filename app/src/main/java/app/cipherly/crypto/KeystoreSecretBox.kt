package app.cipherly.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM with a non-extractable Android Keystore key. Output: `iv (12) || ciphertext+tag`. */
class KeystoreSecretBox(private val alias: String) : SecretBox {

    override fun seal(plain: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            return cipher.iv + cipher.doFinal(plain)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Keystore encryption failed", e)
        } catch (e: ProviderException) {
            throw CryptoException("Keystore encryption failed", e)
        }
    }

    override fun open(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw CryptoException("Sealed data is too short")
        try {
            val key = existingKey() ?: throw CryptoException("Keystore key is missing")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, IV_BYTES))
            return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw CryptoException("Keystore decryption failed", e)
        } catch (e: ProviderException) {
            throw CryptoException("Keystore decryption failed", e)
        }
    }

    override fun destroy() {
        try {
            keyStore().deleteEntry(alias)
        } catch (e: GeneralSecurityException) {
            // Nothing else to clean up.
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

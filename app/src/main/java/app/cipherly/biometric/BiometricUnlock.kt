package app.cipherly.biometric

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Biometric unlock. The vault key is encrypted with a Keystore key that requires a strong
 * biometric for every use and is invalidated when a new fingerprint/face is enrolled.
 * The master password remains the fallback.
 */
@Singleton
class BiometricUnlock @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val _enabled = MutableStateFlow(file.exists())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Takes ownership of [vaultKey] (a copy) and wipes it when done. */
    fun enable(
        activity: FragmentActivity,
        vaultKey: ByteArray,
        title: String,
        cancel: String,
        onResult: (Boolean) -> Unit,
    ) {
        val cipher = try {
            deleteKey()
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
        } catch (e: GeneralSecurityException) {
            vaultKey.fill(0)
            return onResult(false)
        }
        authenticate(
            activity, cipher, title, cancel,
            onSuccess = { c ->
                val ok = try {
                    val blob = c.iv + c.doFinal(vaultKey)
                    file.writeBytes(blob)
                    true
                } catch (e: GeneralSecurityException) {
                    false
                } catch (e: IOException) {
                    false
                }
                vaultKey.fill(0)
                _enabled.value = ok
                onResult(ok)
            },
            onFailure = {
                vaultKey.fill(0)
                onResult(false)
            },
        )
    }

    fun unlock(
        activity: FragmentActivity,
        title: String,
        cancel: String,
        onKey: (ByteArray) -> Unit,
        onFailure: () -> Unit,
    ) {
        val blob = try {
            file.readBytes()
        } catch (e: IOException) {
            return onFailure()
        }
        if (blob.size <= IV_BYTES) return onFailure()
        val cipher = try {
            val key = existingKey() ?: run {
                disable()
                return onFailure()
            }
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, IV_BYTES))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            return onFailure()
        } catch (e: GeneralSecurityException) {
            return onFailure()
        }
        authenticate(
            activity, cipher, title, cancel,
            onSuccess = { c ->
                try {
                    onKey(c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES))
                } catch (e: GeneralSecurityException) {
                    onFailure()
                }
            },
            onFailure = onFailure,
        )
    }

    fun disable() {
        file.delete()
        deleteKey()
        _enabled.value = false
    }

    private fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        cancel: String,
        onSuccess: (Cipher) -> Unit,
        onFailure: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlocked = result.cryptoObject?.cipher
                    if (unlocked == null) onFailure() else onSuccess(unlocked)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancel)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    private fun deleteKey() {
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (e: GeneralSecurityException) {
            // Already gone.
        }
    }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(spec.build())
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "biometric_unlock.bin"
        const val ALIAS = "cipherly_biometric_key"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

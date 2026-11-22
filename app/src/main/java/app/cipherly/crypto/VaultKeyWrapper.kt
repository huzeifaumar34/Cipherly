package app.cipherly.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.SecureRandom

/**
 * Persistable key material: the random vault key encrypted with a key derived from the
 * master password. Contains no secret in the clear, so it is safe to store on disk.
 */
class WrappedVaultKey(
    val salt: ByteArray,
    val params: Argon2Params,
    val wrappedKey: ByteArray,
) {
    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use {
            it.writeByte(FORMAT_VERSION)
            it.writeInt(params.memoryKiB)
            it.writeInt(params.iterations)
            it.writeInt(params.parallelism)
            it.writeByte(salt.size)
            it.write(salt)
            it.writeShort(wrappedKey.size)
            it.write(wrappedKey)
        }
        return out.toByteArray()
    }

    companion object {
        private const val FORMAT_VERSION = 1

        fun fromBytes(bytes: ByteArray): WrappedVaultKey {
            try {
                DataInputStream(ByteArrayInputStream(bytes)).use {
                    if (it.readUnsignedByte() != FORMAT_VERSION) {
                        throw CryptoException("Unsupported key bundle version")
                    }
                    val params = Argon2Params(
                        memoryKiB = it.readInt(),
                        iterations = it.readInt(),
                        parallelism = it.readInt(),
                    )
                    val salt = ByteArray(it.readUnsignedByte()).also(it::readFully)
                    val wrapped = ByteArray(it.readUnsignedShort()).also(it::readFully)
                    return WrappedVaultKey(salt, params, wrapped)
                }
            } catch (e: IOException) {
                throw CryptoException("Corrupted key bundle", e)
            } catch (e: IllegalArgumentException) {
                throw CryptoException("Invalid key bundle parameters", e)
            }
        }
    }
}

/**
 * Envelope encryption for the vault: a random 256-bit vault key encrypts the data, and is
 * itself stored encrypted ("wrapped") by a key-encryption key derived from the master password.
 * Changing the password only re-wraps the vault key; vault data is never re-encrypted.
 *
 * Callers own the returned vault key and should wipe it (`fill(0)`) when the vault locks.
 */
class VaultKeyWrapper(
    private val deriver: Argon2idKeyDeriver = Argon2idKeyDeriver(),
    private val cipher: AesGcmCipher = AesGcmCipher(),
    private val random: SecureRandom = SecureRandom(),
    private val defaultParams: Argon2Params = Argon2Params(),
) {

    /** Creates a new vault key protected by [password]. Returns the bundle to store and the live key. */
    fun create(password: CharArray): Pair<WrappedVaultKey, ByteArray> {
        val vaultKey = ByteArray(AesGcmCipher.KEY_BYTES).also(random::nextBytes)
        return wrapKey(vaultKey, password) to vaultKey
    }

    /** Returns the vault key, or throws [CryptoException] if the password is wrong or data was altered. */
    fun unlock(password: CharArray, bundle: WrappedVaultKey): ByteArray {
        val kek = deriver.derive(password, bundle.salt, bundle.params)
        try {
            return cipher.decrypt(kek, bundle.wrappedKey, AAD)
        } finally {
            kek.fill(0)
        }
    }

    /** Re-wraps the same vault key under a new password, with a fresh salt. */
    fun changePassword(
        oldPassword: CharArray,
        newPassword: CharArray,
        bundle: WrappedVaultKey,
    ): WrappedVaultKey {
        val vaultKey = unlock(oldPassword, bundle)
        try {
            return wrapKey(vaultKey, newPassword)
        } finally {
            vaultKey.fill(0)
        }
    }

    /** Wraps an existing [vaultKey] under [password] with a fresh salt. */
    fun wrapKey(vaultKey: ByteArray, password: CharArray): WrappedVaultKey {
        val salt = deriver.newSalt()
        val kek = deriver.derive(password, salt, defaultParams)
        try {
            return WrappedVaultKey(salt, defaultParams, cipher.encrypt(kek, vaultKey, AAD))
        } finally {
            kek.fill(0)
        }
    }

    private companion object {
        val AAD = "cipherly.vault-key.v1".toByteArray(Charsets.UTF_8)
    }
}

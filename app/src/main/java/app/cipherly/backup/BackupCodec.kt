package app.cipherly.backup

import app.cipherly.crypto.AesGcmCipher
import app.cipherly.crypto.Argon2Params
import app.cipherly.crypto.Argon2idKeyDeriver
import app.cipherly.crypto.CryptoException
import app.cipherly.domain.model.ItemType
import app.cipherly.domain.model.VaultItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

enum class BackupError { WRONG_PASSWORD_OR_CORRUPT, BAD_FILE }

class BackupException(val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * Encrypted backup file format (all integers big-endian):
 *
 *     "CPBK" | version (1) | memoryKiB (4) | iterations (4) | parallelism (1) | salt (16) | payload
 *
 * The payload is AES-256-GCM of the serialized items, keyed by Argon2id(backup password, salt).
 * The whole header is authenticated as AAD, so changing the parameters or salt is detected.
 * The backup password is independent of the master password and is never stored.
 */
class BackupCodec(
    private val deriver: Argon2idKeyDeriver = Argon2idKeyDeriver(),
    private val cipher: AesGcmCipher = AesGcmCipher(),
    private val params: Argon2Params = Argon2Params(),
) {

    fun encode(items: List<VaultItem>, password: CharArray): ByteArray {
        val salt = deriver.newSalt()
        val header = header(params, salt)
        val plain = serialize(items)
        val key = deriver.derive(password, salt, params)
        try {
            return header + cipher.encrypt(key, plain, header)
        } finally {
            key.fill(0)
            plain.fill(0)
        }
    }

    fun decode(bytes: ByteArray, password: CharArray): List<VaultItem> {
        if (bytes.size < HEADER_SIZE + MIN_PAYLOAD) throw BackupException(BackupError.BAD_FILE)
        val header = bytes.copyOfRange(0, HEADER_SIZE)
        val salt = ByteArray(Argon2idKeyDeriver.SALT_BYTES)
        val fileParams = try {
            DataInputStream(ByteArrayInputStream(header)).use {
                val magic = ByteArray(MAGIC.size).also(it::readFully)
                if (!magic.contentEquals(MAGIC) || it.readUnsignedByte() != VERSION) {
                    throw BackupException(BackupError.BAD_FILE)
                }
                val memory = it.readInt()
                val iterations = it.readInt()
                val parallelism = it.readUnsignedByte()
                it.readFully(salt)
                // Reject absurd costs from a crafted file before spending memory on them.
                if (memory !in 8..MAX_MEMORY_KIB || iterations !in 1..MAX_ITERATIONS ||
                    parallelism !in 1..MAX_PARALLELISM || memory < 8 * parallelism
                ) {
                    throw BackupException(BackupError.BAD_FILE)
                }
                Argon2Params(memory, iterations, parallelism)
            }
        } catch (e: IOException) {
            throw BackupException(BackupError.BAD_FILE, e)
        }

        val key = deriver.derive(password, salt, fileParams)
        val plain = try {
            cipher.decrypt(key, bytes.copyOfRange(HEADER_SIZE, bytes.size), header)
        } catch (e: CryptoException) {
            throw BackupException(BackupError.WRONG_PASSWORD_OR_CORRUPT, e)
        } finally {
            key.fill(0)
        }
        try {
            return deserialize(plain)
        } finally {
            plain.fill(0)
        }
    }

    private fun header(params: Argon2Params, salt: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use {
            it.write(MAGIC)
            it.writeByte(VERSION)
            it.writeInt(params.memoryKiB)
            it.writeInt(params.iterations)
            it.writeByte(params.parallelism)
            it.write(salt)
        }
        return out.toByteArray()
    }

    private fun serialize(items: List<VaultItem>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use {
            it.writeByte(PLAIN_VERSION)
            it.writeInt(items.size)
            for (item in items) {
                it.writeUTF(item.id)
                it.writeUTF(item.type.name)
                it.writeUTF(item.title)
                it.writeUTF(item.username)
                it.writeUTF(item.secret)
                it.writeUTF(item.url)
                it.writeUTF(item.notes)
                it.writeLong(item.createdAt)
                it.writeLong(item.updatedAt)
            }
        }
        return out.toByteArray()
    }

    private fun deserialize(plain: ByteArray): List<VaultItem> = try {
        DataInputStream(ByteArrayInputStream(plain)).use {
            if (it.readUnsignedByte() != PLAIN_VERSION) throw BackupException(BackupError.BAD_FILE)
            val count = it.readInt()
            if (count !in 0..MAX_ITEMS) throw BackupException(BackupError.BAD_FILE)
            List(count) {
                val id = it.readUTF()
                val type = ItemType.entries.firstOrNull { t -> t.name == it.readUTF() }
                    ?: throw BackupException(BackupError.BAD_FILE)
                VaultItem(
                    id = id,
                    type = type,
                    title = it.readUTF(),
                    username = it.readUTF(),
                    secret = it.readUTF(),
                    url = it.readUTF(),
                    notes = it.readUTF(),
                    createdAt = it.readLong(),
                    updatedAt = it.readLong(),
                )
            }
        }
    } catch (e: IOException) {
        throw BackupException(BackupError.BAD_FILE, e)
    }

    companion object {
        private val MAGIC = "CPBK".toByteArray(Charsets.US_ASCII)
        private const val VERSION = 1
        private const val PLAIN_VERSION = 1
        private val HEADER_SIZE = MAGIC.size + 1 + 4 + 4 + 1 + Argon2idKeyDeriver.SALT_BYTES
        private const val MIN_PAYLOAD = 1 + 12 + 16
        private const val MAX_MEMORY_KIB = 262_144
        private const val MAX_ITERATIONS = 10
        private const val MAX_PARALLELISM = 4
        private const val MAX_ITEMS = 100_000
    }
}

package app.cipherly.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

/** Argon2id cost parameters. Store them next to the salt so they can be raised later. */
data class Argon2Params(
    val memoryKiB: Int = 65_536,
    val iterations: Int = 3,
    val parallelism: Int = 1,
) {
    init {
        require(memoryKiB >= 8 * parallelism) { "Memory is too low." }
        require(iterations >= 1) { "Iterations must be at least 1." }
        require(parallelism >= 1) { "Parallelism must be at least 1." }
    }
}

/**
 * Derives a 256-bit key from the master password with Argon2id.
 * The password is never stored; the temporary byte copy is wiped after use.
 */
class Argon2idKeyDeriver(
    private val random: SecureRandom = SecureRandom(),
) {

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun derive(
        password: CharArray,
        salt: ByteArray,
        params: Argon2Params = Argon2Params(),
    ): ByteArray {
        require(salt.size >= SALT_BYTES) { "Salt must be at least $SALT_BYTES bytes." }
        val passwordBytes = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(password)).let { buf ->
            ByteArray(buf.remaining()).also { buf.get(it) }
        }
        try {
            val argon = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .build()
            val generator = Argon2BytesGenerator().apply { init(argon) }
            return ByteArray(AesGcmCipher.KEY_BYTES).also {
                generator.generateBytes(passwordBytes, it)
            }
        } finally {
            passwordBytes.fill(0)
        }
    }

    companion object {
        const val SALT_BYTES = 16
    }
}

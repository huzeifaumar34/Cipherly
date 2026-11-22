package app.cipherly.domain.model

import java.security.SecureRandom

data class GeneratorOptions(
    val length: Int = 20,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = true,
) {
    val classCount: Int get() = listOf(lower, upper, digits, symbols).count { it }
    val isValid: Boolean get() = length in MIN_LENGTH..MAX_LENGTH && classCount > 0 && length >= classCount

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64
    }
}

/**
 * Random password generator backed by [SecureRandom]. Every selected character class appears at
 * least once; positions and characters are chosen without modulo bias.
 */
class PasswordGenerator(private val random: SecureRandom = SecureRandom()) {

    fun generate(options: GeneratorOptions): String {
        require(options.isValid) { "Invalid generator options." }
        val pools = buildList {
            if (options.lower) add(LOWER)
            if (options.upper) add(UPPER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }.map { pool -> if (options.avoidAmbiguous) pool.filter { it !in AMBIGUOUS } else pool }
        val all = pools.joinToString("")

        val chars = CharArray(options.length)
        pools.forEachIndexed { i, pool -> chars[i] = pool[random.nextInt(pool.length)] }
        for (i in pools.size until chars.size) chars[i] = all[random.nextInt(all.length)]
        // Fisher-Yates shuffle so the guaranteed characters are not always first.
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    companion object {
        const val LOWER = "abcdefghijklmnopqrstuvwxyz"
        const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
        const val AMBIGUOUS = "Il1O0o|"
    }
}

package app.cipherly.generator

import app.cipherly.domain.model.GeneratorOptions
import app.cipherly.domain.model.PasswordGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordGeneratorTest {
    private val generator = PasswordGenerator()

    @Test
    fun `length is respected and every selected class appears`() {
        repeat(300) {
            val p = generator.generate(GeneratorOptions(length = 12))
            assertEquals(12, p.length)
            assertTrue(p.any { it in PasswordGenerator.LOWER })
            assertTrue(p.any { it in PasswordGenerator.UPPER })
            assertTrue(p.any { it in PasswordGenerator.DIGITS })
            assertTrue(p.any { it in PasswordGenerator.SYMBOLS })
        }
    }

    @Test
    fun `ambiguous characters are excluded when requested`() {
        repeat(300) {
            val p = generator.generate(GeneratorOptions(length = 32, avoidAmbiguous = true))
            assertFalse(p.any { it in PasswordGenerator.AMBIGUOUS })
        }
    }

    @Test
    fun `only selected classes are used`() {
        val p = generator.generate(GeneratorOptions(length = 40, upper = false, symbols = false, digits = false))
        assertTrue(p.all { it in PasswordGenerator.LOWER })
    }

    @Test
    fun `two passwords differ`() {
        val o = GeneratorOptions(length = 24)
        assertNotEquals(generator.generate(o), generator.generate(o))
    }

    @Test
    fun `invalid options are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            generator.generate(GeneratorOptions(lower = false, upper = false, digits = false, symbols = false))
        }
        assertThrows(IllegalArgumentException::class.java) { generator.generate(GeneratorOptions(length = 4)) }
        assertThrows(IllegalArgumentException::class.java) { generator.generate(GeneratorOptions(length = 65)) }
    }
}

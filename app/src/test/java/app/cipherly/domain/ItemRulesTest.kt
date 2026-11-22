package app.cipherly.domain

import app.cipherly.domain.model.ItemCheck
import app.cipherly.domain.model.ItemRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemRulesTest {
    @Test
    fun `title is required`() {
        assertEquals(ItemCheck.TITLE_REQUIRED, ItemRules.check("   ", "x"))
    }

    @Test
    fun `oversized title or field is rejected`() {
        assertEquals(ItemCheck.TOO_LONG, ItemRules.check("a".repeat(101)))
        assertEquals(ItemCheck.TOO_LONG, ItemRules.check("ok", "b".repeat(10_001)))
    }

    @Test
    fun `valid item passes`() {
        assertEquals(ItemCheck.OK, ItemRules.check("GitHub", "segredo", ""))
    }

    @Test
    fun `like wildcards are escaped`() {
        assertEquals("50\\%\\_a\\\\b", ItemRules.escapeLike("50%_a\\b"))
    }
}

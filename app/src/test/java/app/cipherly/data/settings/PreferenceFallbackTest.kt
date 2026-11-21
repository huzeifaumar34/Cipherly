package app.cipherly.data.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferenceFallbackTest {

    @Test
    fun `unknown theme values fall back to dark`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("UNKNOWN"))
    }

    @Test
    fun `unknown language values fall back to system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorage("UNKNOWN"))
    }
}

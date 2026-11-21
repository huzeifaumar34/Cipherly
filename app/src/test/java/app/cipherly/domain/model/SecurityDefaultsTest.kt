package app.cipherly.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SecurityDefaultsTest {

    @Test
    fun `security defaults follow the product specification`() {
        assertEquals(12, SecurityDefaults.MASTER_PASSWORD_MIN_LENGTH)
        assertEquals(6, SecurityDefaults.PIN_LENGTH)
        assertEquals(5, SecurityDefaults.MAX_PIN_FAILURES)
        assertEquals(60, SecurityDefaults.DEFAULT_AUTO_LOCK_SECONDS)
        assertEquals(30, SecurityDefaults.DEFAULT_CLIPBOARD_CLEAR_SECONDS)
    }
}

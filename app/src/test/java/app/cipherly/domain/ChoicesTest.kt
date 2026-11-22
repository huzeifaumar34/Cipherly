package app.cipherly.domain

import app.cipherly.domain.model.Choices
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChoicesTest {
    @Test
    fun `next moves forward and wraps`() {
        assertEquals(30, Choices.next(Choices.AUTO_LOCK_SECONDS, 15))
        assertEquals(15, Choices.next(Choices.AUTO_LOCK_SECONDS, 900))
    }

    @Test
    fun `unknown value falls back to the first option`() {
        assertEquals(10, Choices.next(Choices.CLIPBOARD_SECONDS, 45))
    }
}

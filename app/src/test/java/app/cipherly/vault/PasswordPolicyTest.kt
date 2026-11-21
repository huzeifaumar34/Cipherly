package app.cipherly.vault

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PasswordPolicyTest {
    @Test
    fun `short password is rejected`() {
        assertEquals(PasswordCheck.TOO_SHORT, PasswordPolicy.check("curta".toCharArray(), "curta".toCharArray()))
    }

    @Test
    fun `mismatch is rejected`() {
        assertEquals(
            PasswordCheck.MISMATCH,
            PasswordPolicy.check("uma-senha-longa-1".toCharArray(), "uma-senha-longa-2".toCharArray()),
        )
    }

    @Test
    fun `valid password is accepted`() {
        assertEquals(
            PasswordCheck.OK,
            PasswordPolicy.check("uma-senha-longa-1".toCharArray(), "uma-senha-longa-1".toCharArray()),
        )
    }
}

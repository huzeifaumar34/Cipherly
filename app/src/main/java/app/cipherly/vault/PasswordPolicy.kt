package app.cipherly.vault

import app.cipherly.domain.model.SecurityDefaults

enum class PasswordCheck { OK, TOO_SHORT, MISMATCH }

object PasswordPolicy {
    fun check(password: CharArray, confirmation: CharArray): PasswordCheck = when {
        password.size < SecurityDefaults.MASTER_PASSWORD_MIN_LENGTH -> PasswordCheck.TOO_SHORT
        !password.contentEquals(confirmation) -> PasswordCheck.MISMATCH
        else -> PasswordCheck.OK
    }
}

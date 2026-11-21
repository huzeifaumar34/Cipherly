package app.cipherly.domain.model

/**
 * Product security defaults defined in the Cipherly design specification.
 * These constants do not mean authentication or vault storage is implemented yet.
 */
object SecurityDefaults {
    const val MASTER_PASSWORD_MIN_LENGTH = 12
    const val PIN_LENGTH = 6
    const val MAX_PIN_FAILURES = 5
    const val DEFAULT_AUTO_LOCK_SECONDS = 60
    const val DEFAULT_CLIPBOARD_CLEAR_SECONDS = 30
}

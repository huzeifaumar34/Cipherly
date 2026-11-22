package app.cipherly.domain.model

/** Preset values offered in settings, cycled with a single tap. */
object Choices {
    val AUTO_LOCK_SECONDS = listOf(15, 30, 60, 300, 900)
    val CLIPBOARD_SECONDS = listOf(10, 30, 60, 120)

    /** The value after [current] in [options], wrapping around; the first one if [current] is unknown. */
    fun next(options: List<Int>, current: Int): Int {
        val i = options.indexOf(current)
        return if (i < 0) options.first() else options[(i + 1) % options.size]
    }
}

package app.cipherly.domain.model

enum class ItemCheck { OK, TITLE_REQUIRED, TOO_LONG }

object ItemRules {
    const val MAX_TITLE = 100
    const val MAX_FIELD = 10_000

    fun check(title: String, vararg fields: String): ItemCheck = when {
        title.isBlank() -> ItemCheck.TITLE_REQUIRED
        title.length > MAX_TITLE || fields.any { it.length > MAX_FIELD } -> ItemCheck.TOO_LONG
        else -> ItemCheck.OK
    }

    /** Escapes LIKE wildcards so a search for "50%" matches the literal text. Use with `ESCAPE '\'`. */
    fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

package app.cipherly.backup

import app.cipherly.domain.model.ItemCheck
import app.cipherly.domain.model.ItemRules
import app.cipherly.domain.model.VaultItem

data class MergePlan(
    val toWrite: List<VaultItem>,
    val added: Int,
    val updated: Int,
    val skipped: Int,
)

/** Decides what an import changes. Nothing is ever deleted; the newer `updatedAt` wins. */
object BackupMerge {
    private const val MAX_ID = 64

    fun plan(existing: Map<String, VaultItem>, incoming: List<VaultItem>): MergePlan {
        val working = existing.toMutableMap()
        val written = LinkedHashMap<String, VaultItem>()
        var added = 0
        var updated = 0
        var skipped = 0
        for (item in incoming) {
            val valid = item.id.isNotBlank() && item.id.length <= MAX_ID &&
                ItemRules.check(item.title, item.username, item.secret, item.url, item.notes) == ItemCheck.OK
            if (!valid) {
                skipped++
                continue
            }
            val current = working[item.id]
            when {
                current == null -> added++
                item.updatedAt > current.updatedAt -> updated++
                else -> {
                    skipped++
                    continue
                }
            }
            working[item.id] = item
            written[item.id] = item
        }
        return MergePlan(written.values.toList(), added, updated, skipped)
    }
}

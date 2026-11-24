package app.cipherly.backup

import app.cipherly.domain.model.ItemType
import app.cipherly.domain.model.VaultItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupMergeTest {
    private fun item(id: String, updatedAt: Long, title: String = "T-$id") = VaultItem(
        id, ItemType.NOTE, title, "", "segredo", "", "", 0L, updatedAt,
    )

    @Test
    fun `new items are added, newer ones update, older or equal are skipped`() {
        val existing = mapOf("a" to item("a", 10), "b" to item("b", 10), "c" to item("c", 10))
        val plan = BackupMerge.plan(
            existing,
            listOf(item("a", 20, "novo"), item("b", 5), item("c", 10), item("d", 1)),
        )
        assertEquals(1, plan.updated)
        assertEquals(1, plan.added)
        assertEquals(2, plan.skipped)
        assertEquals(listOf("a", "d"), plan.toWrite.map { it.id })
    }

    @Test
    fun `invalid items are skipped`() {
        val plan = BackupMerge.plan(
            emptyMap(),
            listOf(item("", 1), item("x".repeat(65), 1), item("ok", 1, title = "  "), item("good", 1)),
        )
        assertEquals(1, plan.added)
        assertEquals(3, plan.skipped)
        assertEquals(listOf("good"), plan.toWrite.map { it.id })
    }

    @Test
    fun `duplicate ids inside one backup keep the newest`() {
        val plan = BackupMerge.plan(emptyMap(), listOf(item("a", 1), item("a", 5, "mais novo"), item("a", 3)))
        assertEquals(listOf("mais novo"), plan.toWrite.map { it.title })
        assertEquals(1, plan.added)
        assertEquals(1, plan.updated)
        assertEquals(1, plan.skipped)
    }
}

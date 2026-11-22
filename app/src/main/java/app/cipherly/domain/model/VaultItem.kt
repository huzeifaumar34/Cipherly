package app.cipherly.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ItemType { PASSWORD, API_KEY, NOTE }

/** One vault entry. The whole database file is encrypted, so every column is protected at rest. */
@Entity(tableName = "items")
data class VaultItem(
    @PrimaryKey val id: String,
    val type: ItemType,
    val title: String,
    val username: String,
    val secret: String,
    val url: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

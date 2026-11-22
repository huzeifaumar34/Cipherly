package app.cipherly.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.cipherly.domain.model.VaultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    /** [query] must already be escaped with `ItemRules.escapeLike`. */
    @Query(
        "SELECT * FROM items WHERE title LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR username LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR url LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY title COLLATE NOCASE ASC",
    )
    fun observe(query: String): Flow<List<VaultItem>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): VaultItem?

    @Upsert
    suspend fun upsert(item: VaultItem)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)
}

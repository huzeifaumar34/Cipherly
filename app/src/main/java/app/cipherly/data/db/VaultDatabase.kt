package app.cipherly.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.cipherly.domain.model.VaultItem

@Database(entities = [VaultItem::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
}

package app.cipherly.data.db

import android.content.Context
import androidx.room.Room
import app.cipherly.backup.BackupMerge
import app.cipherly.backup.MergePlan
import app.cipherly.domain.model.ItemRules
import app.cipherly.domain.model.VaultItem
import app.cipherly.vault.VaultRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The encrypted item database, open only while the vault is unlocked. It is built with the
 * vault key and closed (dropping the key) the moment the vault locks.
 */
@Singleton
class VaultSession @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VaultRepository,
) {
    private var database: VaultDatabase? = null

    init {
        System.loadLibrary("sqlcipher")
        repository.addOnLockListener { close() }
    }

    @Synchronized
    private fun db(): VaultDatabase {
        database?.let { return it }
        val key = repository.keyCopy() ?: throw IllegalStateException("The vault is locked")
        val passphrase = SqlCipherKey.passphrase(key)
        key.fill(0)
        // The factory wipes the passphrase after opening the database.
        val opened = Room.databaseBuilder(context, VaultDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
        database = opened
        return opened
    }

    @Synchronized
    fun close() {
        database?.close()
        database = null
    }

    fun observe(query: String): Flow<List<VaultItem>> =
        flow { emitAll(db().items().observe(ItemRules.escapeLike(query))) }

    suspend fun get(id: String): VaultItem? = db().items().get(id)

    suspend fun save(item: VaultItem) = db().items().upsert(item)

    suspend fun allItems(): List<VaultItem> = db().items().all()

    /** Merges backed-up items into the vault: new ones are added, newer ones replace older ones. */
    suspend fun importItems(incoming: List<VaultItem>): MergePlan {
        val dao = db().items()
        val plan = BackupMerge.plan(dao.all().associateBy { it.id }, incoming)
        if (plan.toWrite.isNotEmpty()) dao.upsertAll(plan.toWrite)
        return plan
    }

    suspend fun delete(id: String) = db().items().delete(id)

    private companion object {
        const val DB_NAME = "vault.db"
    }
}

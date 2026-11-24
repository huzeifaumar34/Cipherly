package app.cipherly.backup

import app.cipherly.crypto.Argon2Params
import app.cipherly.domain.model.ItemType
import app.cipherly.domain.model.VaultItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class BackupCodecTest {
    private val codec = BackupCodec(params = Argon2Params(memoryKiB = 64, iterations = 1))
    private val password = "senha-de-backup-longa".toCharArray()

    private fun item(id: String, title: String = "GitHub", secret: String = "s3gr€do ✓ ção") = VaultItem(
        id = id, type = ItemType.PASSWORD, title = title, username = "ana", secret = secret,
        url = "https://github.com", notes = "linha 1\nlinha 2", createdAt = 1L, updatedAt = 2L,
    )

    private fun errorOf(block: () -> Unit): BackupError =
        assertThrows(BackupException::class.java, block).error

    @Test
    fun `round trip keeps every field including unicode`() {
        val items = listOf(item("a"), item("b", "Outro", "x".repeat(10_000)))
        assertEquals(items, codec.decode(codec.encode(items, password), password))
    }

    @Test
    fun `empty backup round trips`() {
        assertEquals(emptyList<VaultItem>(), codec.decode(codec.encode(emptyList(), password), password))
    }

    @Test
    fun `wrong password is reported`() {
        val file = codec.encode(listOf(item("a")), password)
        assertEquals(
            BackupError.WRONG_PASSWORD_OR_CORRUPT,
            errorOf { codec.decode(file, "outra-senha-longa".toCharArray()) },
        )
    }

    @Test
    fun `tampering with ciphertext or header is detected`() {
        val file = codec.encode(listOf(item("a")), password)
        val body = file.copyOf().also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        assertEquals(BackupError.WRONG_PASSWORD_OR_CORRUPT, errorOf { codec.decode(body, password) })
        val salt = file.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertEquals(BackupError.WRONG_PASSWORD_OR_CORRUPT, errorOf { codec.decode(salt, password) })
    }

    @Test
    fun `files that are not backups are rejected`() {
        assertEquals(BackupError.BAD_FILE, errorOf { codec.decode(ByteArray(10), password) })
        val file = codec.encode(listOf(item("a")), password)
        assertEquals(BackupError.BAD_FILE, errorOf { codec.decode(file.copyOf().also { it[0] = 'X'.code.toByte() }, password) })
        assertEquals(BackupError.BAD_FILE, errorOf { codec.decode(file.copyOf().also { it[4] = 9 }, password) })
    }

    @Test
    fun `absurd argon2 parameters in a crafted file are rejected before deriving`() {
        val file = codec.encode(listOf(item("a")), password)
        ByteBuffer.wrap(file).putInt(5, Int.MAX_VALUE)
        assertEquals(BackupError.BAD_FILE, errorOf { codec.decode(file, password) })
    }

    @Test
    fun `backups are not deterministic`() {
        val items = listOf(item("a"))
        val a = codec.encode(items, password)
        val b = codec.encode(items, password)
        assert(!a.contentEquals(b))
    }
}

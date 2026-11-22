package app.cipherly.vault

import app.cipherly.crypto.Argon2Params
import app.cipherly.crypto.VaultKeyWrapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VaultRepositoryTest {
    @TempDir
    lateinit var dir: File

    private var now = 1_000L
    private val password = "uma-senha-mestra-longa".toCharArray()

    private fun repo() = VaultRepository(
        file = File(dir, "vault_key.bin"),
        wrapper = VaultKeyWrapper(defaultParams = Argon2Params(memoryKiB = 64, iterations = 1)),
        clock = { now },
    )

    @Test
    fun `create then lock then unlock`() = runBlocking {
        val repo = repo()
        assertEquals(VaultState.NO_VAULT, repo.state.value)
        repo.create(password.copyOf())
        assertEquals(VaultState.UNLOCKED, repo.state.value)
        repo.lock()
        assertEquals(VaultState.LOCKED, repo.state.value)
        assertEquals(UnlockResult.Success, repo.unlock(password.copyOf()))
        assertEquals(VaultState.UNLOCKED, repo.state.value)
    }

    @Test
    fun `a new repository sees the existing vault as locked`() = runBlocking {
        repo().create(password.copyOf())
        assertEquals(VaultState.LOCKED, repo().state.value)
    }

    @Test
    fun `wrong password fails and repeated failures are throttled`() = runBlocking {
        val repo = repo()
        repo.create(password.copyOf())
        repo.lock()
        val wrong = "senha-errada-123".toCharArray()
        assertEquals(UnlockResult.WrongPassword, repo.unlock(wrong.copyOf()))
        assertEquals(UnlockResult.WrongPassword, repo.unlock(wrong.copyOf()))
        assertEquals(UnlockResult.WrongPassword, repo.unlock(wrong.copyOf()))
        assertTrue(repo.unlock(password.copyOf()) is UnlockResult.Throttled)
        now += 60_000
        assertEquals(UnlockResult.Success, repo.unlock(password.copyOf()))
    }

    @Test
    fun `corrupted key file is reported`() = runBlocking {
        val repo = repo()
        repo.create(password.copyOf())
        repo.lock()
        File(dir, "vault_key.bin").writeBytes(ByteArray(4))
        assertEquals(UnlockResult.Corrupted, repo.unlock(password.copyOf()))
    }

    @Test
    fun `backoff grows and is capped`() {
        assertEquals(0L, VaultRepository.backoffMillis(2))
        assertEquals(2_000L, VaultRepository.backoffMillis(3))
        assertEquals(300_000L, VaultRepository.backoffMillis(40))
    }

    @Test
    fun `pin unlock works after locking`() = runBlocking {
        val box = object : app.cipherly.crypto.SecretBox {
            private val c = app.cipherly.crypto.AesGcmCipher()
            private val k = ByteArray(32) { 1 }
            override fun seal(plain: ByteArray) = c.encrypt(k, plain)
            override fun open(sealed: ByteArray) = c.decrypt(k, sealed)
            override fun destroy() = Unit
        }
        val wrapper = VaultKeyWrapper(defaultParams = Argon2Params(memoryKiB = 64, iterations = 1))
        val repo = VaultRepository(
            file = File(dir, "vault_key.bin"),
            wrapper = wrapper,
            clock = { now },
            pin = PinUnlockStore(File(dir, "pin.bin"), File(dir, "attempts.txt"), box, wrapper),
        )
        assertEquals(false, repo.enablePin("123456".toCharArray()))
        repo.create(password.copyOf())
        assertEquals(true, repo.enablePin("123456".toCharArray()))
        repo.lock()
        assertTrue(repo.unlockWithPin("123456".toCharArray()) is PinResult.Success)
        assertEquals(VaultState.UNLOCKED, repo.state.value)
    }
}

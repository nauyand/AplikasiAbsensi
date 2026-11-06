package com.teknosolvo.personalhub.vault

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teknosolvo.personalhub.FakeBiometricKeys
import com.teknosolvo.personalhub.FakeClock
import com.teknosolvo.personalhub.FakeKeyWrapper
import com.teknosolvo.personalhub.data.HubDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class VaultRepositoryTest {
    private lateinit var db: HubDatabase
    private lateinit var session: VaultSession
    private lateinit var vault: VaultRepository
    private val bio = FakeBiometricKeys()
    private val clock = FakeClock()
    private val scope = CoroutineScope(SupervisorJob())

    @Before fun setUp() {
        db = HubDatabase.inMemory(ApplicationProvider.getApplicationContext())
        session = VaultSession(scope, clock)
        vault = VaultRepository(db, session, FakeKeyWrapper(), bio, clock, pinIterations = 2_000)
    }

    @After fun tearDown() { db.close(); scope.cancel() }

    private val secret = VaultSecret("Bank Nusantara", "nauy", "nauy@example.com", "S3cret!Pa55word", "https://bank.example", "PIN ATM bukan di sini")

    @Test fun `default categories are seeded`() = runBlocking {
        val names = vault.categoryList().map { it.name }
        assertTrue(names.containsAll(listOf("Banking", "E-Wallet", "Social Media", "Email", "Gaming", "Marketplace", "Work",
            "Server / Hosting", "Developer Tools", "Other")))
    }

    @Test fun `pin setup, lock and unlock`() = runBlocking {
        assertFalse(vault.isSetUp())
        vault.setupPin("123456".toCharArray())
        assertTrue(session.unlocked.value)
        session.lock()
        assertFalse(session.unlocked.value)
        assertEquals(UnlockResult.Success, vault.unlockWithPin("123456".toCharArray()))
        assertTrue(session.unlocked.value)
    }

    @Test fun `wrong pins lead to an escalating lockout`() = runBlocking {
        vault.setupPin("123456".toCharArray()); session.lock()
        repeat(4) { i ->
            val r = vault.unlockWithPin("000000".toCharArray())
            assertEquals(UnlockResult.WrongPin(4 - i), r)
        }
        val locked = vault.unlockWithPin("000000".toCharArray())
        assertTrue(locked is UnlockResult.LockedOut)
        // Even the right PIN is refused during lockout.
        assertTrue(vault.unlockWithPin("123456".toCharArray()) is UnlockResult.LockedOut)
        clock.advance(31_000)
        assertEquals(UnlockResult.Success, vault.unlockWithPin("123456".toCharArray()))
        assertEquals(0, vault.settings()!!.failedAttempts)
    }

    @Test fun `accounts are stored encrypted, never as plaintext`() = runBlocking {
        vault.setupPin("1234".toCharArray())
        val cat = vault.categoryList().first { it.name == "Banking" }
        val id = vault.save(VaultAccount(categoryId = cat.id, secret = secret))
        val row = db.vault().account(id)!!
        val raw = String(row.payload, Charsets.ISO_8859_1)
        listOf(secret.title, secret.username, secret.email, secret.password, secret.url, secret.notes).forEach {
            assertFalse("'$it' leaked into the database", raw.contains(it))
        }
        // Also scan the raw SQLite row via a plain query.
        db.openHelper.readableDatabase.query("SELECT * FROM vault_accounts").use { c ->
            assertTrue(c.moveToFirst())
            for (i in 0 until c.columnCount) {
                val v = runCatching { c.getString(i) }.getOrNull() ?: continue
                assertFalse(v.contains(secret.password))
            }
        }
        assertEquals(secret, vault.account(id)!!.secret)
    }

    @Test fun `locked vault exposes no decrypted data`() = runBlocking {
        vault.setupPin("1234".toCharArray())
        vault.save(VaultAccount(categoryId = null, secret = secret))
        assertEquals(1, vault.accounts.first().size)
        session.lock()
        assertTrue(vault.accounts.first().isEmpty())
        try {
            vault.allDecrypted(); error("should throw")
        } catch (e: VaultLockedException) { /* expected */ }
    }

    @Test fun `edit, favourite and delete account`() = runBlocking {
        vault.setupPin("1234".toCharArray())
        val id = vault.save(VaultAccount(categoryId = null, secret = secret))
        clock.advance(1000)
        vault.save(vault.account(id)!!.let { it.copy(secret = it.secret.copy(password = "new-pass")) })
        val updated = vault.account(id)!!
        assertEquals("new-pass", updated.secret.password)
        assertTrue(updated.updatedAt > updated.createdAt)
        vault.setFavorite(id, true)
        assertTrue(vault.account(id)!!.favorite)
        vault.delete(id)
        assertNull(vault.account(id))
    }

    @Test fun `auto lock after inactivity and on background`() = runBlocking {
        vault.setupPin("1234".toCharArray())
        session.autoLockSeconds = 60
        clock.advance(30_000); session.checkTimeout()
        assertTrue(session.unlocked.value)
        session.touch()
        clock.advance(59_000); session.checkTimeout()
        assertTrue(session.unlocked.value)
        clock.advance(2_000); session.checkTimeout()
        assertFalse(session.unlocked.value)

        vault.unlockWithPin("1234".toCharArray())
        session.hold()           // e.g. file picker for backup is open
        session.onAppBackground()
        assertTrue(session.unlocked.value)
        session.release()
        session.onAppBackground()
        assertFalse(session.unlocked.value)
    }

    @Test fun `biometric unlock via crypto object and invalidation`() = runBlocking {
        vault.setupPin("1234".toCharArray())
        vault.completeBiometricEnroll(vault.biometricEnrollCipher()!!)
        assertTrue(vault.settings()!!.biometricEnabled)
        session.lock()
        val cipher = vault.biometricUnlockCipher()
        assertNotNull(cipher)
        assertEquals(UnlockResult.Success, vault.completeBiometricUnlock(cipher!!))
        assertTrue(session.unlocked.value)

        // New fingerprint enrolled → key invalidated → biometrics disabled, PIN still works.
        session.lock()
        bio.delete()
        assertNull(vault.biometricUnlockCipher())
        assertFalse(vault.settings()!!.biometricEnabled)
        assertEquals(UnlockResult.Success, vault.unlockWithPin("1234".toCharArray()))
    }

    @Test fun `change pin keeps the data readable`() = runBlocking {
        vault.setupPin("1234".toCharArray())
        val id = vault.save(VaultAccount(categoryId = null, secret = secret))
        assertTrue(vault.changePin("9999".toCharArray(), "567890".toCharArray()) is UnlockResult.WrongPin)
        assertEquals(UnlockResult.Success, vault.changePin("1234".toCharArray(), "567890".toCharArray()))
        session.lock()
        assertTrue(vault.unlockWithPin("1234".toCharArray()) is UnlockResult.WrongPin)
        assertEquals(UnlockResult.Success, vault.unlockWithPin("567890".toCharArray()))
        assertEquals(secret, vault.account(id)!!.secret)
        assertEquals(6, vault.settings()!!.pinLength)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pin must be numeric`() = runBlocking { vault.setupPin("12ab".toCharArray()) }

    @Test fun `wipe vault removes keys and accounts`() = runBlocking {
        vault.setupPin("1234".toCharArray())
        vault.save(VaultAccount(categoryId = null, secret = secret))
        vault.wipeVault()
        assertFalse(vault.isSetUp())
        assertEquals(0, vault.accountCount())
        assertFalse(session.unlocked.value)
    }
}

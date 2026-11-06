package com.teknosolvo.personalhub.backup

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.teknosolvo.personalhub.FakeClock
import com.teknosolvo.personalhub.FakeKeyWrapper
import com.teknosolvo.personalhub.data.ActivitySource
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.HubDatabase
import com.teknosolvo.personalhub.data.NoteTag
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.data.QuickNote
import com.teknosolvo.personalhub.tracking.BootReceiver
import com.teknosolvo.personalhub.vault.VaultAccount
import com.teknosolvo.personalhub.vault.VaultRepository
import com.teknosolvo.personalhub.vault.VaultSecret
import com.teknosolvo.personalhub.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class BackupAndBootTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob())
    private val clock = FakeClock()
    private val dbs = mutableListOf<HubDatabase>()

    @After fun tearDown() { dbs.forEach { it.close() }; scope.cancel() }

    private fun newDevice(): Triple<HubDatabase, VaultRepository, BackupManager> {
        val db = HubDatabase.inMemory(ctx).also { dbs += it }
        val vault = VaultRepository(db, VaultSession(scope, clock), FakeKeyWrapper(), null, clock, pinIterations = 1_000)
        return Triple(db, vault, BackupManager(db, vault, clock, iterations = 1_000))
    }

    private val secret = VaultSecret("GitHub", "nauy", "nauy@example.com", "gh-very-secret-123", "github.com", "2FA aktif")

    private suspend fun seed(db: HubDatabase, vault: VaultRepository) {
        val pid = db.places().insert(Place(name = "Kantor", kind = PlaceKind.OFFICE, latitude = -6.2, longitude = 106.8, radiusM = 120,
            colorArgb = 0xFF8B7CFF, iconKey = "office", createdAt = 1, updatedAt = 1))
        db.activities().insert(DailyActivity(placeId = pid, title = "Kantor", kind = PlaceKind.OFFICE, date = "2026-11-06",
            startAt = clock.now - 3_600_000, endAt = clock.now, source = ActivitySource.AUTO, note = "rapat pagi", createdAt = 1, updatedAt = 1))
        db.notes().upsert(QuickNote(title = "Ide", body = "Fitur baru", tag = NoteTag.IDEA, createdAt = 1, updatedAt = 1))
        vault.setupPin("1234".toCharArray())
        val cat = vault.categoryList().first { it.name == "Developer Tools" }
        vault.save(VaultAccount(categoryId = cat.id, favorite = true, secret = secret))
    }

    @Test fun `backup file is encrypted and restores on a new device`() = runBlocking {
        val (db, vault, backup) = newDevice()
        seed(db, vault)
        val file = backup.export("correct horse battery".toCharArray(), includeVault = true)
        val text = file.decodeToString()
        listOf(secret.password, secret.email, secret.notes, "rapat pagi", "Fitur baru", "Kantor").forEach {
            assertFalse("'$it' must not appear in the backup file", text.contains(it))
        }
        assertTrue(text.contains("AES-256-GCM"))

        // "New phone": different Keystore key, new PIN.
        val (db2, vault2, backup2) = newDevice()
        vault2.setupPin("8888".toCharArray())
        val payload = backup2.decrypt(file, "correct horse battery".toCharArray())
        assertEquals(BackupStats(1, 1, 1, 1), backup2.stats(payload))
        backup2.restore(payload, restoreVault = true)

        assertEquals(120, db2.places().all().single().radiusM)
        val act = db2.activities().all().single()
        assertEquals("rapat pagi", act.note)
        assertEquals(db2.places().all().single().id, act.placeId)
        assertEquals("Fitur baru", db2.notes().all().single().body)
        val restored = vault2.allDecrypted().single()
        assertEquals(secret, restored.secret)
        assertTrue(restored.favorite)
        assertEquals("Developer Tools", vault2.categoryList().first { it.id == restored.categoryId }.name)
    }

    @Test(expected = WrongBackupPasswordException::class)
    fun `wrong backup password is rejected`() = runBlocking<Unit> {
        val (db, vault, backup) = newDevice()
        seed(db, vault)
        val file = backup.export("password-123".toCharArray(), includeVault = true)
        backup.decrypt(file, "password-124".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `short backup password is refused`() = runBlocking<Unit> {
        val (_, _, backup) = newDevice()
        backup.export("short".toCharArray(), includeVault = false)
    }

    @Test fun `backup without vault keeps vault out of the file`() = runBlocking {
        val (db, vault, backup) = newDevice()
        seed(db, vault)
        val payload = backup.decrypt(backup.export("password-123".toCharArray(), includeVault = false), "password-123".toCharArray())
        assertEquals(null, payload.vault)
    }

    @Test fun `device reboot re-registers geofences via WorkManager`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx,
            Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build())
        // Do not actually run the worker (it needs the full app container); just verify it is enqueued.
        BootReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))
        val infos = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork("tracker-resync").get()
        assertEquals(1, infos.size)
        assertTrue(infos.single().state == WorkInfo.State.ENQUEUED || infos.single().state == WorkInfo.State.RUNNING ||
            infos.single().state.isFinished)
    }
}

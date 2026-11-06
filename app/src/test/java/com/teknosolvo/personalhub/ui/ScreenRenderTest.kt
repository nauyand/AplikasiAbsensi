package com.teknosolvo.personalhub.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teknosolvo.personalhub.AppContainer
import com.teknosolvo.personalhub.DevTools
import com.teknosolvo.personalhub.FakeBiometricKeys
import com.teknosolvo.personalhub.FakeKeyWrapper
import com.teknosolvo.personalhub.data.EventSource
import com.teknosolvo.personalhub.data.NoteTag
import com.teknosolvo.personalhub.data.QuickNote
import com.teknosolvo.personalhub.tracking.Fix
import com.teknosolvo.personalhub.tracking.engineConfig
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.dashboard.DashboardScreen
import com.teknosolvo.personalhub.ui.personal.PersonalHubScreen
import com.teknosolvo.personalhub.ui.settings.BackupScreen
import com.teknosolvo.personalhub.ui.settings.GpsSettingsScreen
import com.teknosolvo.personalhub.ui.settings.PrivacyDashboardScreen
import com.teknosolvo.personalhub.ui.settings.SettingsScreen
import com.teknosolvo.personalhub.ui.settings.SimulationScreen
import com.teknosolvo.personalhub.ui.theme.HubTheme
import com.teknosolvo.personalhub.ui.tracker.ActivityEditScreen
import com.teknosolvo.personalhub.ui.tracker.TrackerHubScreen
import com.teknosolvo.personalhub.ui.vault.AccountDetailScreen
import com.teknosolvo.personalhub.ui.vault.GeneratorScreen
import com.teknosolvo.personalhub.ui.vault.PinSetupScreen
import com.teknosolvo.personalhub.ui.vault.VaultGate
import com.teknosolvo.personalhub.ui.vault.VaultHomeScreen
import com.teknosolvo.personalhub.ui.vault.VaultSettingsScreen
import com.teknosolvo.personalhub.vault.VaultAccount
import com.teknosolvo.personalhub.vault.VaultSecret
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every main screen with realistic (simulated) data on Robolectric's native graphics,
 * which proves each composition runs without crashing, and writes PNGs to app/build/screenshots.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h860dp-xhdpi")
class ScreenRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun container(seed: Boolean = true, vault: Boolean = true): AppContainer = runBlocking {
        val c = AppContainer(ApplicationProvider.getApplicationContext(), FakeKeyWrapper() to FakeBiometricKeys())
        c.settings.updateNow { it.copy(onboardingDone = true, userName = "Nauy") }
        if (seed) {
            DevTools.seed(c)
            // Currently at the office (simulated), arrived 90 minutes ago.
            val office = c.db.places().all().first { it.name.startsWith("Kantor") }
            val instant = c.settings.current.engineConfig().copy(dwellMs = 0, exitDelayMs = 0)
            val now = System.currentTimeMillis()
            c.tracker.processFix(Fix(office.latitude, office.longitude, 12f, now - 90 * 60_000L), EventSource.SIMULATION, configOverride = instant)
            listOf(
                QuickNote(title = "Follow up vendor", body = "Kirim revisi penawaran sebelum Jumat.", tag = NoteTag.WORK, pinned = true, createdAt = now, updatedAt = now),
                QuickNote(title = "Ide fitur", body = "Widget layar utama untuk status lokasi.", tag = NoteTag.IDEA, createdAt = now, updatedAt = now - 3_600_000),
            ).forEach { c.db.notes().upsert(it) }
        }
        if (vault) {
            c.vault.setupPin("123456".toCharArray())
            val cats = c.vault.categoryList().associateBy { it.name }
            listOf(
                Triple("Bank Nusantara", "Banking", VaultSecret("Bank Nusantara", "nauy.d", "", "Kx9#mP2vL!qz", "https://ibank.example")),
                Triple("GitHub", "Developer Tools", VaultSecret("GitHub", "nauyand", "nauy@example.com", "gh_9Fq!2LmZ#t", "github.com")),
                Triple("Instagram", "Social Media", VaultSecret("Instagram", "@nauy", "", "Insta#2026!", "")),
                Triple("VPS Jakarta", "Server / Hosting", VaultSecret("VPS Jakarta", "root", "", "S3rv3r!Root#99", "203.0.113.10", "SSH port 2222")),
            ).forEachIndexed { i, (_, cat, s) -> c.vault.save(VaultAccount(categoryId = cats[cat]?.id, favorite = i == 1, secret = s)) }
        }
        c
    }

    private fun shot(name: String, c: AppContainer, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            HubTheme { CompositionLocalProvider(LocalContainer provides c) { content() } }
        }
        repeat(25) {
            Thread.sleep(40)
            shadowOf(Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeBy(64)
        }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun dashboard() { val c = container(); shot("01_dashboard", c) { DashboardScreen({}, {}, {}, {}, {}, {}) } }
    @Test fun trackerCurrent() { val c = container(); shot("02_tracker_current", c) { TrackerHubScreen(0, {}, { _, _ -> }, {}) } }
    @Test fun trackerPlaces() { val c = container(); shot("03_tracker_places", c) { TrackerHubScreen(1, {}, { _, _ -> }, {}) } }
    @Test fun trackerDaily() { val c = container(); shot("04_tracker_daily", c) { TrackerHubScreen(2, {}, { _, _ -> }, {}) } }
    @Test fun trackerMonthly() { val c = container(); shot("05_tracker_monthly", c) { TrackerHubScreen(3, {}, { _, _ -> }, {}) } }
    @Test fun trackerStats() { val c = container(); shot("06_tracker_stats", c) { TrackerHubScreen(4, {}, { _, _ -> }, {}) } }
    @Test fun activityEdit() {
        val c = container()
        val id = runBlocking { c.db.activities().all().last().id }
        shot("07_activity_edit", c) { ActivityEditScreen(id, "", {}) }
    }
    @Test fun vaultPinSetup() { val c = container(seed = false, vault = false); shot("08_vault_pin_setup", c) { PinSetupScreen() } }
    @Test fun vaultHome() { val c = container(); shot("09_vault_home", c) { VaultGate { VaultHomeScreen({}, {}, {}, {}) } } }
    @Test fun vaultDetail() {
        val c = container()
        val id = runBlocking { c.vault.allDecrypted().first { it.secret.title == "GitHub" }.id }
        shot("10_vault_detail", c) { VaultGate { AccountDetailScreen(id, {}, {}) } }
    }
    @Test fun vaultLocked() {
        val c = container(); c.session.lock()
        shot("11_vault_locked", c) { VaultGate { VaultHomeScreen({}, {}, {}, {}) } }
    }
    @Test fun generator() { val c = container(); shot("12_generator", c) { GeneratorScreen {} } }
    @Test fun vaultSettings() { val c = container(); shot("13_vault_settings", c) { VaultGate { VaultSettingsScreen {} } } }
    @Test fun personalNotes() { val c = container(); shot("14_personal_notes", c) { PersonalHubScreen(0, {}, {}, {}) } }
    @Test fun personalExport() { val c = container(); shot("15_personal_export", c) { PersonalHubScreen(2, {}, {}, {}) } }
    @Test fun settings() { val c = container(); shot("16_settings", c) { SettingsScreen {} } }
    @Test fun gpsSettings() { val c = container(); shot("17_gps_settings", c) { GpsSettingsScreen({}, {}) } }
    @Test fun privacy() { val c = container(); shot("18_privacy", c) { PrivacyDashboardScreen {} } }
    @Test fun backup() { val c = container(); shot("19_backup", c) { BackupScreen {} } }
    @Test fun simulation() { val c = container(); shot("20_simulation", c) { SimulationScreen {} } }
}

package com.teknosolvo.personalhub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.BuildConfig
import com.teknosolvo.personalhub.data.Accent
import com.teknosolvo.personalhub.data.AppPrefs
import com.teknosolvo.personalhub.data.ThemeMode
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.SettingRow
import com.teknosolvo.personalhub.ui.components.rememberPermissionActions
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.nav.Routes
import com.teknosolvo.personalhub.ui.theme.Electric
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.ui.theme.Violet
import com.teknosolvo.personalhub.ui.theme.Warning
import com.teknosolvo.personalhub.ui.tracker.syncLabel
import com.teknosolvo.personalhub.ui.vault.Toggle
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    val c = LocalContainer.current
    val prefs by c.settings.prefs.collectAsStateWithLifecycle()
    HubScaffold("Setelan") { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HubCard {
                SettingRow(Icons.Filled.Fingerprint, "PIN & Biometrik", "Vault, auto-lock, kunci aplikasi", Violet) { onNavigate(Routes.SET_SECURITY) }
                SettingRow(Icons.Filled.GpsFixed, "GPS & Tracker", if (prefs.trackingEnabled) "Aktif · dwell ${prefs.dwellMinutes} mnt" else "Nonaktif",
                    Electric) { onNavigate(Routes.SET_GPS) }
                SettingRow(Icons.Filled.Notifications, "Notifikasi", "Masuk/keluar lokasi, masalah GPS", Warning) { onNavigate(Routes.SET_NOTIF) }
                SettingRow(Icons.Filled.Palette, "Tema", themeLabel(prefs), Hub.extras.success) { onNavigate(Routes.SET_THEME) }
            }
            HubCard {
                SettingRow(Icons.Filled.Backup, "Backup & Restore", "Backup terenkripsi AES-256-GCM", Violet) { onNavigate(Routes.SET_BACKUP) }
                SettingRow(Icons.Filled.PrivacyTip, "Privacy & Security", "Status izin, keamanan, penyimpanan", Electric) { onNavigate(Routes.SET_PRIVACY) }
            }
            HubCard {
                SettingRow(Icons.Filled.Info, "NAUY Personal Hub", "Versi ${BuildConfig.VERSION_NAME} · data 100% lokal", Hub.extras.muted, onClick = null, trailing = null)
            }
        }
    }
}

private fun themeLabel(p: AppPrefs) = (when (p.themeMode) { ThemeMode.LIGHT -> "Terang"; ThemeMode.SYSTEM -> "Ikuti sistem"; else -> "Gelap" }) +
    " · aksen " + if (p.accent == Accent.BLUE) "electric blue" else "ungu"

@Composable
fun SecuritySettingsScreen(onBack: () -> Unit, onVaultSettings: () -> Unit) {
    val c = LocalContainer.current
    val security by c.vault.securityFlow.collectAsStateWithLifecycle(null)
    val unlocked by c.session.unlocked.collectAsStateWithLifecycle()
    HubScaffold("PIN & Biometrik", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HubCard {
                val s = security
                Text(if (s == null) "Vault belum dibuat" else "Vault aktif", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (s == null) "Buka tab Vault untuk membuat PIN. Biometrik dapat diaktifkan setelahnya."
                    else "PIN ${s.pinLength} digit · biometrik ${if (s.biometricEnabled) "aktif" else "nonaktif"} · auto-lock " +
                        (if (s.autoLockSeconds == 0) "mati" else "${s.autoLockSeconds} dtk") + " · status: " + if (unlocked) "terbuka" else "terkunci",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted,
                )
            }
            if (security != null) OutlinedButton(onClick = onVaultSettings, modifier = Modifier.fillMaxWidth()) { Text("Buka Vault Settings (perlu autentikasi)") }
            Text(
                "Pengaturan keamanan hanya bisa diubah setelah vault dibuka dengan PIN atau sidik jari. " +
                    "Kunci enkripsi disimpan di Android Keystore dan tidak pernah ditulis ke log.",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GpsSettingsScreen(onBack: () -> Unit, onSimulation: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle()
    fun set(t: (AppPrefs) -> AppPrefs, reschedule: Boolean = false, resync: Boolean = false) {
        scope.launch {
            c.settings.updateNow(t)
            if (reschedule) c.applyTrackingSchedule()
            if (resync) c.syncGeofencesNow()
        }
    }
    HubScaffold("GPS & Tracker", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HubCard {
                Toggle("Deteksi lokasi otomatis", prefs.trackingEnabled, "Geofencing + pengecekan berkala hemat baterai") { on ->
                    set({ it.copy(trackingEnabled = on) }, reschedule = true, resync = true)
                }
            }
            SectionLabel("Akurasi & anti-duplikasi")
            HubCard {
                NumberSlider("Dwell time (konfirmasi masuk)", prefs.dwellMinutes, 0..15, "menit") { v -> set({ it.copy(dwellMinutes = v) }) }
                NumberSlider("Tunda konfirmasi keluar", prefs.exitDelayMinutes, 0..15, "menit") { v -> set({ it.copy(exitDelayMinutes = v) }) }
                NumberSlider("Toleransi GPS (hysteresis)", prefs.gpsToleranceM, 0..150, "m", step = 5) { v -> set({ it.copy(gpsToleranceM = v) }, resync = true) }
                NumberSlider("Abaikan fix dengan akurasi lebih buruk dari", prefs.maxAccuracyM, 50..500, "m", step = 10) { v -> set({ it.copy(maxAccuracyM = v) }) }
                NumberSlider("Gabungkan kunjungan jika kembali dalam", prefs.mergeGapMinutes, 0..60, "menit") { v -> set({ it.copy(mergeGapMinutes = v) }) }
                Text(
                    "Masuk dikonfirmasi bila tetap di dalam radius selama dwell time; keluar dikonfirmasi bila berada di luar radius + toleransi " +
                        "selama waktu tunda. Waktu yang dicatat adalah saat pertama kali melewati batas. Fix palsu (mock) dan lompatan tidak wajar diabaikan.",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted,
                )
            }
            SectionLabel("Pengecekan berkala (cadangan geofence)")
            HubCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Mati", 15 to "15 mnt", 30 to "30 mnt", 60 to "1 jam", 120 to "2 jam").forEach { (m, l) ->
                        FilterChip(prefs.periodicCheckMinutes == m, { set({ it.copy(periodicCheckMinutes = m) }, reschedule = true) }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                SettingRow(Icons.Filled.BatteryChargingFull, "Hemat baterai",
                    "Tidak ada polling GPS terus-menerus. OS membangunkan aplikasi saat melewati geofence; pengecekan berkala memakai akurasi seimbang (Wi-Fi/seluler).",
                    Hub.extras.success, onClick = null, trailing = null)
            }
            SectionLabel("Pemeliharaan")
            HubCard {
                SettingRow(Icons.Filled.Sync, "Sinkronkan ulang geofence", "Daftarkan ulang semua lokasi aktif ke sistem", onClick = {
                    scope.launch { context.toast(syncLabel(c.syncGeofencesNow())) }
                })
                SettingRow(Icons.Filled.Science, "Simulasi & pengujian", "Uji masuk/keluar geofence tanpa berpindah tempat", Warning, onClick = onSimulation)
            }
            SectionLabel("Keterbatasan sistem")
            HubCard {
                Text(
                    "• Deteksi latar belakang memerlukan izin \"Izinkan sepanjang waktu\".\n" +
                        "• Saat GPS/Layanan lokasi mati, Android menghapus geofence; aplikasi memberi notifikasi dan mendaftarkannya lagi saat aktif.\n" +
                        "• Mode hemat daya/Doze dapat menunda notifikasi geofence beberapa menit; waktu tetap dicatat dari fix asli.\n" +
                        "• Beberapa vendor (Xiaomi, Oppo, Vivo, Samsung) membatasi aplikasi latar belakang — kecualikan NAUY Hub dari optimasi baterai.\n" +
                        "• Radius < 100 m kurang andal di dalam gedung; geofence OS minimal 100 m, sedangkan pencatatan memakai radius tepatmu.",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted,
                )
                Spacer(Modifier.height(8.dp))
                val actions = rememberPermissionActions()
                OutlinedButton(onClick = actions.openAppSettings, modifier = Modifier.fillMaxWidth()) { Text("Buka info aplikasi (baterai & izin)") }
            }
        }
    }
}

@Composable
fun NumberSlider(label: String, value: Int, range: IntRange, unit: String, step: Int = 1, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${v.toInt()} $unit", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = v, onValueChange = { v = ((it / step).toInt() * step).toFloat() },
            onValueChangeFinished = { onChange(v.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val prefs by c.settings.prefs.collectAsStateWithLifecycle()
    val actions = rememberPermissionActions()
    HubScaffold("Notifikasi", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!c.notifier.canPost()) HubCard {
                Text("Notifikasi sistem dimatikan", style = MaterialTheme.typography.titleSmall)
                Text("Semua kejadian tetap tercatat di Notification Center di dalam aplikasi.", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = actions.requestNotifications, modifier = Modifier.fillMaxWidth()) { Text("Izinkan notifikasi") }
            }
            HubCard {
                Toggle("Masuk & meninggalkan kantor", prefs.notifyOffice) { v -> c.settings.update { it.copy(notifyOffice = v) } }
                Toggle("Masuk & meninggalkan rumah", prefs.notifyHome) { v -> c.settings.update { it.copy(notifyHome = v) } }
                Toggle("Lokasi lain (istirahat, makan, dll.)", prefs.notifyOtherPlaces) { v -> c.settings.update { it.copy(notifyOtherPlaces = v) } }
                Toggle("GPS mati / izin dicabut / masalah deteksi", prefs.notifyIssues) { v -> c.settings.update { it.copy(notifyIssues = v) } }
            }
            Text("Notifikasi tidak pernah memuat data vault. Isi notifikasi disembunyikan di layar kunci (visibility: private).",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
    }
}

@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val prefs by c.settings.prefs.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(prefs.userName) }
    HubScaffold("Tema", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Mode")
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.DARK to "Gelap", ThemeMode.LIGHT to "Terang", ThemeMode.SYSTEM to "Sistem").forEach { (m, l) ->
                    FilterChip(prefs.themeMode == m, { c.settings.update { it.copy(themeMode = m) } }, label = { Text(l) })
                }
            }
            SectionLabel("Warna aksen")
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(prefs.accent == Accent.VIOLET, { c.settings.update { it.copy(accent = Accent.VIOLET) } }, label = { Text("Ungu") })
                FilterChip(prefs.accent == Accent.BLUE, { c.settings.update { it.copy(accent = Accent.BLUE) } }, label = { Text("Electric blue") })
            }
            SectionLabel("Nama panggilan")
            OutlinedTextField(name, { name = it.take(30); c.settings.update { p -> p.copy(userName = name.trim()) } }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

package com.teknosolvo.personalhub.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.backup.BackupManager
import com.teknosolvo.personalhub.backup.BackupPayload
import com.teknosolvo.personalhub.backup.WrongBackupPasswordException
import com.teknosolvo.personalhub.ui.components.ConfirmDialog
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.SettingRow
import com.teknosolvo.personalhub.ui.components.rememberPermissionActions
import com.teknosolvo.personalhub.ui.components.rememberTrackerHealth
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.ui.vault.PinField
import com.teknosolvo.personalhub.ui.vault.Toggle
import com.teknosolvo.personalhub.ui.vault.biometricStatusText
import com.teknosolvo.personalhub.ui.vault.unlockMessage
import com.teknosolvo.personalhub.util.dateTime
import com.teknosolvo.personalhub.util.formatBytes
import com.teknosolvo.personalhub.vault.UnlockResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Small dialog to unlock the vault with the PIN from anywhere (backup, search). */
@Composable
fun UnlockVaultDialog(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buka vault") },
        text = { Column { PinField(pin, { pin = it }, "PIN vault"); error?.let { Text(it, color = Hub.extras.danger) } } },
        confirmButton = {
            TextButton(enabled = pin.length >= 4, onClick = {
                val chars = pin.toCharArray(); pin = ""
                scope.launch {
                    val r = withContext(Dispatchers.Default) { c.vault.unlockWithPin(chars) }
                    if (r == UnlockResult.Success) onDismiss() else error = unlockMessage(r)
                }
            }) { Text("Buka") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle()
    val unlocked by c.session.unlocked.collectAsStateWithLifecycle()
    val security by c.vault.securityFlow.collectAsStateWithLifecycle(null)
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var includeVault by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var unlockDialog by remember { mutableStateOf(false) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<BackupPayload?>(null) }
    var wipeDialog by remember { mutableStateOf(false) }
    val vaultReady = security != null && unlocked

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        c.session.release()
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        val pw = password.toCharArray()
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.Default) { c.backup.export(pw, includeVault && vaultReady) }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Tidak bisa menulis file") }
                c.settings.updateNow { it.copy(lastBackupAt = System.currentTimeMillis()) }
            }.onSuccess { context.toast("Backup terenkripsi tersimpan"); password = ""; password2 = "" }
                .onFailure { context.toast("Backup gagal: ${it.message}") }
            busy = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        c.session.release()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importBytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (importBytes == null) context.toast("File tidak bisa dibaca")
        }
    }

    HubScaffold("Backup & Restore", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HubCard {
                Text("Backup terakhir", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                Text(if (prefs.lastBackupAt > 0) prefs.lastBackupAt.dateTime() else "Belum pernah", style = MaterialTheme.typography.titleMedium)
            }
            SectionLabel("Backup manual")
            HubCard {
                Text("File dienkripsi AES-256-GCM dengan kunci dari password backup (PBKDF2, 210.000 iterasi). Tidak ada data plaintext di dalam file.",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password backup (min. ${BackupManager.MIN_PASSWORD} karakter)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password2, { password2 = it }, label = { Text("Ulangi password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    isError = password2.isNotEmpty() && password2 != password)
                Toggle("Sertakan akun vault", includeVault && vaultReady,
                    subtitle = when {
                        security == null -> "Vault belum dibuat"
                        !unlocked -> "Vault terkunci — buka dulu untuk menyertakan akun"
                        else -> "Akun didekripsi lalu dienkripsi ulang dengan password backup"
                    }) { includeVault = it; if (it && security != null && !unlocked) unlockDialog = true }
                if (security != null && !unlocked) TextButton(onClick = { unlockDialog = true }) { Text("Buka vault") }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !busy && password.length >= BackupManager.MIN_PASSWORD && password == password2,
                    onClick = { c.session.hold(); exportLauncher.launch("nauy-hub-backup-${LocalDate.now()}.nhb") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.height(20.dp)) else { Icon(Icons.Filled.Backup, null); Spacer(Modifier.width(8.dp)); Text("Buat backup") }
                }
                Text("Simpan password backup di tempat aman. Tanpa password, backup tidak bisa dipulihkan.",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.warning)
            }
            SectionLabel("Pemulihan")
            HubCard {
                Text("Pulihkan dari file .nhb. Data tracker dan catatan di perangkat ini akan diganti.",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { c.session.hold(); importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Upload, null); Spacer(Modifier.width(8.dp)); Text("Pilih file backup")
                }
            }
            SectionLabel("Zona berbahaya")
            HubCard {
                SettingRow(Icons.Filled.DeleteForever, "Hapus seluruh data", "Lokasi, riwayat, catatan, vault, dan kunci enkripsi",
                    tint = Hub.extras.danger, onClick = { wipeDialog = true })
            }
        }
    }

    if (unlockDialog) UnlockVaultDialog { unlockDialog = false }

    importBytes?.let { bytes ->
        AlertDialog(
            onDismissRequest = { importBytes = null },
            title = { Text("Password backup") },
            text = {
                OutlinedTextField(importPassword, { importPassword = it }, singleLine = true, label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation())
            },
            confirmButton = {
                TextButton(onClick = {
                    val pw = importPassword.toCharArray(); importPassword = ""
                    scope.launch {
                        runCatching { withContext(Dispatchers.Default) { c.backup.decrypt(bytes, pw) } }
                            .onSuccess { pending = it; importBytes = null }
                            .onFailure { context.toast(if (it is WrongBackupPasswordException) it.message!! else "Gagal: ${it.message}") }
                    }
                }) { Text("Dekripsi") }
            },
            dismissButton = { TextButton(onClick = { importBytes = null }) { Text("Batal") } },
        )
    }

    pending?.let { p ->
        val st = c.backup.stats(p)
        val canVault = st.accounts != null && security != null && unlocked
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Pulihkan backup?") },
            text = {
                Text(
                    "Backup ${p.createdAt.dateTime()}\n• ${st.places} lokasi\n• ${st.activities} aktivitas\n• ${st.notes} catatan\n" +
                        "• ${st.accounts?.let { "$it akun vault" } ?: "tanpa data vault"}\n\n" +
                        "Data lokal akan DIGANTI." +
                        when {
                            st.accounts == null -> ""
                            security == null -> "\n\nBuat PIN vault terlebih dahulu (tab Vault) agar akun ikut dipulihkan."
                            !unlocked -> "\n\nBuka vault terlebih dahulu agar akun ikut dipulihkan."
                            else -> "\n\nAkun vault lokal juga akan diganti."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { c.backup.restore(p, restoreVault = canVault) }
                            .onSuccess { context.toast("Data dipulihkan"); c.syncGeofencesNow() }
                            .onFailure { context.toast("Gagal memulihkan: ${it.message}") }
                        pending = null
                    }
                }) { Text("Pulihkan", color = Hub.extras.danger) }
            },
            dismissButton = {
                Row {
                    if (st.accounts != null && security != null && !unlocked) TextButton(onClick = { unlockDialog = true }) { Text("Buka vault") }
                    TextButton(onClick = { pending = null }) { Text("Batal") }
                }
            },
        )
    }

    if (wipeDialog) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { wipeDialog = false },
            title = { Text("Hapus seluruh data?") },
            text = {
                Column {
                    Text("Semua data di aplikasi ini akan dihapus permanen dan tidak dapat dipulihkan tanpa backup. Ketik HAPUS untuk konfirmasi.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(typed, { typed = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == "HAPUS", onClick = {
                    scope.launch { c.wipeEverything(); context.toast("Semua data dihapus"); wipeDialog = false; onBack() }
                }) { Text("Hapus semua", color = Hub.extras.danger) }
            },
            dismissButton = { TextButton(onClick = { wipeDialog = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun PrivacyDashboardScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val health = rememberTrackerHealth()
    val actions = rememberPermissionActions()
    val security by c.vault.securityFlow.collectAsStateWithLifecycle(null)
    val unlocked by c.session.unlocked.collectAsStateWithLifecycle()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle()
    var counts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var bytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            counts = listOf(
                "Lokasi" to c.db.places().count(),
                "Aktivitas" to c.db.activities().count(),
                "Log deteksi" to c.db.events().count(),
                "Catatan" to c.db.notes().count(),
                "Akun vault" to c.db.vault().accountCount(),
            )
            bytes = c.databaseBytes()
        }
    }
    val ok = Hub.extras.success
    val bad = Hub.extras.danger
    val warn = Hub.extras.warning
    fun col(b: Boolean, soft: Boolean = false) = if (b) ok else if (soft) warn else bad

    HubScaffold("Privacy Dashboard", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Izin & lokasi")
            HubCard {
                StatusRow(Icons.Filled.MyLocation, "Izin lokasi (presisi)", if (health.fineLocation) "Diizinkan" else if (health.coarseLocation) "Hanya perkiraan" else "Ditolak",
                    col(health.fineLocation, health.coarseLocation), actions.requestForeground.takeIf { !health.fineLocation })
                StatusRow(Icons.Filled.GpsFixed, "Lokasi latar belakang", if (health.backgroundLocation) "Sepanjang waktu" else "Hanya saat dibuka",
                    col(health.backgroundLocation, true), actions.requestBackground.takeIf { !health.backgroundLocation })
                StatusRow(Icons.Filled.GpsFixed, "Layanan lokasi / GPS", if (health.locationEnabled) "Aktif" else "Mati", col(health.locationEnabled),
                    actions.openLocationSettings.takeIf { !health.locationEnabled })
                StatusRow(Icons.Filled.Notifications, "Notifikasi", if (c.notifier.canPost()) "Diizinkan" else "Dimatikan", col(c.notifier.canPost(), true),
                    actions.requestNotifications.takeIf { !c.notifier.canPost() })
                StatusRow(Icons.Filled.BatterySaver, "Tracker otomatis", if (prefs.trackingEnabled) "Aktif" else "Nonaktif", col(prefs.trackingEnabled, true), null)
            }
            SectionLabel("Keamanan vault")
            HubCard {
                val s = security
                StatusRow(Icons.Filled.Lock, "Vault", when { s == null -> "Belum dibuat"; unlocked -> "Terbuka (auto-lock aktif)"; else -> "Terkunci" },
                    if (s == null) warn else ok, null)
                StatusRow(Icons.Filled.Fingerprint, "Biometrik", if (s?.biometricEnabled == true) "Aktif" else biometricStatusText(context),
                    col(s?.biometricEnabled == true, true), null)
                StatusRow(Icons.Filled.Screenshot, "Proteksi screenshot", if (s?.screenshotProtection != false) "Aktif" else "Nonaktif",
                    col(s?.screenshotProtection != false, true), null)
                Text("Enkripsi: AES-256-GCM per entri · kunci data dibungkus Android Keystore + PIN (PBKDF2-SHA256) · biometrik via CryptoObject.",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
            }
            SectionLabel("Backup & penyimpanan")
            HubCard {
                StatusRow(Icons.Filled.Restore, "Backup terakhir", if (prefs.lastBackupAt > 0) prefs.lastBackupAt.dateTime() else "Belum pernah",
                    col(prefs.lastBackupAt > 0, true), null)
                StatusRow(Icons.Filled.Storage, "Database lokal", formatBytes(bytes), MaterialTheme.colorScheme.primary, null)
                counts.forEach { (k, v) -> Text("• $k: $v", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted) }
                Spacer(Modifier.height(6.dp))
                Text("Semua data disimpan lokal di perangkat. Tidak ada server, analitik, atau pelacak pihak ketiga. " +
                    "Internet hanya dipakai untuk memuat tile peta OpenStreetMap dan (opsional) nama alamat. " +
                    "Android Auto Backup dinonaktifkan agar data tidak tersalin ke cloud.",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
            }
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, status: String, color: Color, onFix: (() -> Unit)?) {
    SettingRow(icon, title, status, tint = color, onClick = onFix, trailing = if (onFix != null) ({ TextButton(onClick = onFix) { Text("Perbaiki") } }) else null)
}

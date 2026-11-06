package com.teknosolvo.personalhub.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.IconBadge
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.rememberPermissionActions
import com.teknosolvo.personalhub.ui.components.rememberTrackerHealth
import com.teknosolvo.personalhub.ui.theme.Electric
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.ui.theme.Violet
import com.teknosolvo.personalhub.ui.theme.Warning

@Composable
fun OnboardingScreen() {
    val c = LocalContainer.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(c.settings.current.userName) }
    val health = rememberTrackerHealth()
    val actions = rememberPermissionActions()
    val notifOk = c.notifier.canPost()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.large).background(Hub.extras.accentGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Shield, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("NAUY Personal Hub", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tracker lokasi otomatis dan vault password terenkripsi, dalam satu aplikasi pribadi. " +
                "Semua data tersimpan hanya di perangkat ini.",
            style = MaterialTheme.typography.bodyLarge, color = Hub.extras.muted,
        )
        Spacer(Modifier.height(28.dp))

        if (step == 0) {
            Feature(Icons.Filled.MyLocation, Violet, "Smart Location Tracker",
                "Geofencing mencatat jam masuk, keluar, dan durasi di kantor, rumah, atau tempat istirahat.")
            Feature(Icons.Filled.Shield, Electric, "Private Vault",
                "Akun & password dienkripsi AES-256-GCM, kunci dilindungi Android Keystore, PIN, dan sidik jari.")
            Feature(Icons.Filled.NotificationsActive, Warning, "Insight & Notifikasi",
                "Riwayat harian & bulanan, statistik kedatangan, dan peringatan saat GPS bermasalah.")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it.take(30) }, label = { Text("Nama panggilan (opsional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Lanjut") }
        } else {
            Text("Izin yang dibutuhkan", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            PermissionStep("Lokasi saat aplikasi digunakan", "Untuk membaca posisi dan memilih titik lokasi.",
                health.fineLocation, actions.requestForeground)
            PermissionStep("Lokasi sepanjang waktu", "Agar deteksi berjalan di latar belakang dan saat layar mati.",
                health.backgroundLocation, actions.requestBackground)
            PermissionStep("Notifikasi", "Kabar saat tiba/keluar lokasi dan saat GPS mati.", notifOk, actions.requestNotifications)
            Spacer(Modifier.height(8.dp))
            Text("Kamu tetap bisa mengubah izin nanti di Setelan → Privacy Dashboard.",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                c.settings.update { it.copy(userName = name.trim(), onboardingDone = true) }
                c.requestGeofenceSync()
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Mulai") }
            OutlinedButton(onClick = { step = 0 }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Kembali") }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, body: String) {
    Row(Modifier.padding(vertical = 10.dp)) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Hub.extras.muted)
        }
    }
}

@Composable
private fun PermissionStep(title: String, body: String, granted: Boolean, onGrant: () -> Unit) {
    HubCard(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
            }
            if (granted) Icon(Icons.Filled.CheckCircle, "Diizinkan", tint = Hub.extras.success)
            else OutlinedButton(onClick = onGrant) { Text("Izinkan") }
        }
    }
}

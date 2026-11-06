package com.teknosolvo.personalhub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.teknosolvo.personalhub.DevTools
import com.teknosolvo.personalhub.data.EventSource
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.tracking.EngineConfig
import com.teknosolvo.personalhub.tracking.EngineState
import com.teknosolvo.personalhub.tracking.Fix
import com.teknosolvo.personalhub.tracking.PresenceEngine
import com.teknosolvo.personalhub.tracking.engineConfig
import com.teknosolvo.personalhub.tracking.toZone
import com.teknosolvo.personalhub.ui.components.ConfirmDialog
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.SettingRow
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Hub
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.random.Random

/** Fix [meters] north of the place center (positive = outside when > radius). */
fun offsetFix(p: Place, meters: Double, time: Long, accuracy: Float = 10f) =
    Fix(p.latitude + meters / 111_320.0, p.longitude, accuracy, time)

/**
 * Feed a synthetic jittery GPS trace into an *isolated* engine (no database) and count visits.
 * Returns (naive boundary crossings, visits recorded by the engine).
 */
fun jitterTest(p: Place, cfg: EngineConfig, seed: Int = 7): Pair<Int, Int> {
    val rnd = Random(seed)
    val zone = p.toZone()
    var state = EngineState()
    var visits = 0
    var naive = 0
    var lastInside: Boolean? = null
    val start = System.currentTimeMillis() - 4 * 3_600_000L
    // 3 hours sitting right at the edge of the radius (±40 m noise), one fix per minute.
    for (i in 0 until 180) {
        val d = p.radiusM + (rnd.nextDouble() - 0.5) * 80
        val dLng = (rnd.nextDouble() - 0.5) * 20 / (111_320.0 * cos(Math.toRadians(p.latitude)))
        val fix = Fix(p.latitude + d / 111_320.0, p.longitude + dLng, 15f + rnd.nextFloat() * 20, start + i * 60_000L)
        val inside = PresenceEngine.distanceM(p.latitude, p.longitude, fix.lat, fix.lng) <= p.radiusM
        if (lastInside != null && inside != lastInside && inside) naive++
        lastInside = inside
        val r = PresenceEngine.process(state, listOf(zone), fix, cfg)
        if (r.opened != null) visits++
        state = r.state.copy(currentActivityId = if (r.state.currentPlaceId != null) 1 else null)
    }
    return naive to visits
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulationScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var jitter by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val prefs by c.settings.prefs.collectAsStateWithLifecycle()
    val selected = places.firstOrNull { it.id == selectedId } ?: places.firstOrNull { it.enabled }
    val instant = prefs.engineConfig().copy(dwellMs = 0, exitDelayMs = 0)

    HubScaffold("Simulasi & Pengujian", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HubCard {
                Text("Simulasi memakai mesin deteksi yang sama dengan GPS sungguhan. Aktivitas hasil simulasi diberi label \"Simulasi\" " +
                    "dan dapat dihapus tanpa menyentuh data asli.", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
            }
            SectionLabel("Lokasi")
            if (places.isEmpty()) Text("Tambahkan lokasi terlebih dahulu.", color = Hub.extras.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                places.filter { it.enabled }.forEach { p -> FilterChip(selected?.id == p.id, { selectedId = p.id }, label = { Text(p.name) }) }
            }
            selected?.let { p ->
                SectionLabel("Geofence")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val out = c.tracker.processFix(offsetFix(p, 0.0, System.currentTimeMillis()), EventSource.SIMULATION,
                                "ENTER", listOf(p.id), instant)
                            context.toast(out.opened?.let { "Masuk ${it.title} tercatat" } ?: "Tidak ada perubahan (sudah di dalam / duplikat)")
                        }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Filled.Login, null); Spacer(Modifier.width(6.dp)); Text("Masuk") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val out = c.tracker.processFix(offsetFix(p, p.radiusM + prefs.gpsToleranceM + 200.0, System.currentTimeMillis()),
                                EventSource.SIMULATION, "EXIT", listOf(p.id), instant)
                            context.toast(out.closed?.let { "Keluar ${it.title} tercatat" } ?: "Tidak ada perubahan (belum di dalam)")
                        }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(6.dp)); Text("Keluar") }
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        // Two quick ENTER events: the second must be ignored as a duplicate.
                        val t = System.currentTimeMillis()
                        val a = c.tracker.processFix(offsetFix(p, 0.0, t), EventSource.SIMULATION, "ENTER", listOf(p.id), instant)
                        val b = c.tracker.processFix(offsetFix(p, 5.0, t + 1000), EventSource.SIMULATION, "ENTER", listOf(p.id), instant)
                        context.toast("Event 1: ${if (a.opened != null) "kunjungan baru" else "tidak ada"} · Event 2: ${if (b.opened == null) "diabaikan ✓" else "duplikat ✗"}")
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Uji event masuk ganda") }

                SectionLabel("Anti-duplikasi GPS goyah")
                HubCard {
                    Text("Menjalankan 180 fix (3 jam) yang berfluktuasi ±40 m tepat di batas radius ${p.radiusM} m pada mesin terisolasi.",
                        style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val (naive, visits) = jitterTest(p, prefs.engineConfig())
                        jitter = "Tanpa filter: $naive kali \"masuk\" · Dengan dwell + hysteresis: $visits kunjungan tercatat"
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Science, null); Spacer(Modifier.width(6.dp)); Text("Jalankan uji jitter") }
                    jitter?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                }
            }
            SectionLabel("Data")
            HubCard {
                if (DevTools.available) SettingRow(Icons.Filled.Storage, "Isi data dummy 30 hari (debug)", "Hanya di build debug · berlabel Simulasi",
                    onClick = { scope.launch { context.toast(DevTools.seed(c)) } })
                SettingRow(Icons.Filled.DeleteSweep, "Hapus semua data simulasi", "Data asli tidak terpengaruh", tint = Hub.extras.danger,
                    onClick = { confirmClear = true })
            }
        }
    }
    if (confirmClear) ConfirmDialog("Hapus data simulasi?", "Semua aktivitas berlabel Simulasi akan dihapus.",
        onConfirm = { scope.launch { c.tracker.clearSimulated(); context.toast("Data simulasi dihapus") } }, onDismiss = { confirmClear = false })
}

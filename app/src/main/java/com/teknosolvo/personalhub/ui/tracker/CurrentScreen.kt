package com.teknosolvo.personalhub.ui.tracker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.AppContainer
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.DetectionStatus
import com.teknosolvo.personalhub.data.EventSource
import com.teknosolvo.personalhub.data.LocationEvent
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.data.TrackerStateEntity
import com.teknosolvo.personalhub.tracking.GeofenceManager
import com.teknosolvo.personalhub.tracking.LocationProvider.Companion.toFix
import com.teknosolvo.personalhub.tracking.PresenceEngine
import com.teknosolvo.personalhub.tracking.TrackerHealth
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.IconBadge
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.StatusDot
import com.teknosolvo.personalhub.ui.components.fixFor
import com.teknosolvo.personalhub.ui.components.placeIcon
import com.teknosolvo.personalhub.ui.components.rememberPermissionActions
import com.teknosolvo.personalhub.ui.components.rememberTrackerHealth
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.util.DATE_LONG
import com.teknosolvo.personalhub.util.durationLong
import com.teknosolvo.personalhub.util.formatDistance
import com.teknosolvo.personalhub.util.hm
import com.teknosolvo.personalhub.util.relativeTime
import com.teknosolvo.personalhub.util.toLocalDate
import com.teknosolvo.personalhub.util.zoneLabel
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Reads a fresh fix in the foreground and runs it through the engine. */
suspend fun refreshLocation(c: AppContainer): String {
    val loc = c.locations.currentFix(highAccuracy = true)
        ?: return "Lokasi tidak tersedia. Pastikan GPS aktif dan izin diberikan."
    val out = c.tracker.processFix(loc.toFix(), EventSource.FOREGROUND)
    return when {
        out.rejected == "DISABLED" -> "Tracker sedang dinonaktifkan di Pengaturan GPS."
        out.rejected != null -> "Fix diabaikan: ${DetectionStatus.label(out.rejected)} (±${loc.accuracy.toInt()} m)"
        out.opened != null -> "Tercatat masuk ${out.opened.title}"
        out.closed != null -> "Tercatat keluar ${out.closed.title}"
        out.pending -> "Menunggu konfirmasi dwell time… (±${loc.accuracy.toInt()} m)"
        else -> "Lokasi diperbarui (±${loc.accuracy.toInt()} m)"
    }
}

@Composable
fun CurrentActivityCard(
    state: TrackerStateEntity,
    current: DailyActivity?,
    places: List<Place>,
    now: Long,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val place = places.firstOrNull { it.id == (current?.placeId ?: state.currentPlaceId) }
    val pendingPlace = places.firstOrNull { it.id == state.pendingPlaceId }
    val extras = Hub.extras
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(extras.heroGradient)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(if (current != null) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.6f), animate = current != null)
                Spacer(Modifier.width(8.dp))
                Text("CURRENT ACTIVITY", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f))
                if (onRefresh != null) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Refresh, "Perbarui lokasi", tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(current?.title ?: "", transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "place") { title ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current != null) {
                        Icon(placeIcon(place?.iconKey ?: activityIconKey(current, place)), null, tint = Color.White,
                            modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (current != null) title.uppercase() else "DI LUAR AREA TERDAFTAR",
                        style = if (current != null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                        color = Color.White, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(LocalDate.now().format(DATE_LONG), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            if (current != null) {
                Spacer(Modifier.height(14.dp))
                Row {
                    HeroStat("Masuk", "${current.startAt.hm()} ${zoneLabel()}" +
                        if (current.startAt.toLocalDate() != LocalDate.now()) " (kemarin)" else "", Modifier.weight(1f))
                    HeroStat("Durasi", durationLong(now - current.startAt), Modifier.weight(1.2f))
                }
                Spacer(Modifier.height(10.dp))
                Text("Status: ${statusSentence(current)}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                if (state.pendingExitSince != null) {
                    Text("Terdeteksi keluar area sejak ${state.pendingExitSince.hm()} — menunggu konfirmasi",
                        color = Color(0xFFFDE68A), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Spacer(Modifier.height(10.dp))
                val msg = if (pendingPlace != null && state.pendingSince != null)
                    "Mendeteksi ${pendingPlace.name} sejak ${state.pendingSince.hm()} — menunggu dwell time"
                else "Tidak berada di lokasi yang terdaftar."
                Text("Status: $msg", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Pembaruan terakhir: " + (state.lastCheckAt?.let { relativeTime(it, now) } ?: "belum pernah"),
                color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun statusSentence(a: DailyActivity) = when (a.kind) {
    PlaceKind.OFFICE -> "Sedang Berada di Kantor"
    PlaceKind.HOME -> "Sedang Berada di Rumah"
    PlaceKind.BREAK, PlaceKind.FOOD -> "Sedang Istirahat di ${a.title}"
    else -> "Sedang Berada di ${a.title}"
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
fun PulseDot(color: Color, animate: Boolean) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(10.dp).alpha(if (animate) a else 1f).clip(CircleShape).background(color))
}

/** GPS / permission status with a one-tap fix. */
@Composable
fun HealthCard(health: TrackerHealth, modifier: Modifier = Modifier, compact: Boolean = false) {
    val actions = rememberPermissionActions()
    val issue = health.issue
    val ok = issue == null
    val color = if (ok) Hub.extras.success else if (issue == TrackerHealth.ISSUE_NO_BACKGROUND) Hub.extras.warning else Hub.extras.danger
    if (compact && ok) {
        Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color)
            Spacer(Modifier.width(8.dp))
            Text("GPS aktif · izin lokasi sepanjang waktu", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
        return
    }
    HubCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                when (issue) {
                    null -> Icons.Filled.GpsFixed
                    TrackerHealth.ISSUE_GPS_OFF -> Icons.Filled.GpsOff
                    TrackerHealth.ISSUE_NO_PERMISSION -> Icons.Filled.LocationOff
                    else -> Icons.Filled.WarningAmber
                }, color,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (ok) "Deteksi otomatis aktif" else "Perlu tindakan", style = MaterialTheme.typography.titleSmall)
                Text(TrackerHealth.describe(issue), style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
            }
        }
        actions.fixFor(issue)?.let { (label, action) ->
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}

/** Tracker tab 0: current location, health, geofence sync, recent detections. */
@Composable
fun CurrentLocationTab(onAddPlace: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by c.tracker.stateFlow.collectAsStateWithLifecycle(TrackerStateEntity())
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())
    val now = rememberNow()
    val (from, to) = remember(now / 3_600_000) { com.teknosolvo.personalhub.tracking.Stats.dayBounds(LocalDate.now(), java.time.ZoneId.systemDefault()) }
    val today by c.tracker.overlapping(from - 86_400_000L * 2, to).collectAsStateWithLifecycle(emptyList())
    val events by c.tracker.recentEvents(30).collectAsStateWithLifecycle(emptyList())
    val lastSync by c.lastSync.collectAsStateWithLifecycle()
    val health = rememberTrackerHealth()
    var refreshing by remember { mutableStateOf(false) }
    val current = today.firstOrNull { it.id == state.currentActivityId && it.isOpen }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CurrentActivityCard(state, current, places, now, refreshing = refreshing, onRefresh = {
                refreshing = true
                scope.launch { context.toast(refreshLocation(c)); refreshing = false }
            })
        }
        item { HealthCard(health) }
        item {
            HubCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Posisi terakhir", style = MaterialTheme.typography.titleSmall)
                        val lat = state.lastLat; val lng = state.lastLng
                        if (lat != null && lng != null) {
                            Text("%.5f, %.5f · ±%d m".format(lat, lng, (state.lastAccuracy ?: 0f).toInt()),
                                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                            val nearest = places.filter { it.enabled }.minByOrNull { PresenceEngine.distanceM(it.latitude, it.longitude, lat, lng) }
                            nearest?.let {
                                val d = PresenceEngine.distanceM(it.latitude, it.longitude, lat, lng)
                                Text("Terdekat: ${it.name} · ${formatDistance(d)} (radius ${it.radiusM} m)",
                                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                            }
                        } else Text("Belum ada data posisi", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                        state.lastFixAt?.let {
                            Text("Fix: ${relativeTime(it, now)}", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val r = c.syncGeofencesNow()
                            context.toast(syncLabel(r))
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Sinkron geofence")
                    }
                    if (places.isEmpty()) FilledTonalButton(onClick = onAddPlace, modifier = Modifier.weight(1f)) { Text("Tambah lokasi") }
                }
                lastSync?.let { (t, r) ->
                    Spacer(Modifier.height(6.dp))
                    Text("Sinkron terakhir ${relativeTime(t, now)}: ${syncLabel(r)}", style = MaterialTheme.typography.bodySmall,
                        color = Hub.extras.muted)
                }
            }
        }
        item { SectionLabel("Log deteksi terbaru") }
        if (events.isEmpty()) item {
            Text("Belum ada deteksi. Tambahkan lokasi lalu biarkan aplikasi bekerja di latar belakang.",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
        items(events, key = { it.id }) { EventRow(it) }
    }
}

fun syncLabel(r: GeofenceManager.SyncResult) = when (r) {
    is GeofenceManager.SyncResult.Ok -> "${r.count} geofence aktif"
    is GeofenceManager.SyncResult.Skipped -> TrackerHealth.describe(r.reason)
    is GeofenceManager.SyncResult.Failed -> "Gagal: ${r.message}"
}

@Composable
fun EventRow(e: LocationEvent) {
    val color = when (e.status) {
        DetectionStatus.CONFIRMED, DetectionStatus.MERGED -> Hub.extras.success
        DetectionStatus.PENDING -> MaterialTheme.colorScheme.primary
        else -> Hub.extras.muted
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${if (e.type == "ENTER") "Masuk" else "Keluar"} ${e.placeName}", style = MaterialTheme.typography.bodyMedium)
            Text("${DetectionStatus.label(e.status)} · ${e.source.lowercase()} · ±${e.accuracyM?.toInt() ?: 0} m",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
        Text(e.timestamp.hm(), style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
    }
}

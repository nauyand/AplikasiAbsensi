package com.teknosolvo.personalhub.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.TrackerStateEntity
import com.teknosolvo.personalhub.tracking.Bucket
import com.teknosolvo.personalhub.tracking.Stats
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.IconBadge
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.StatTile
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.components.rememberTrackerHealth
import com.teknosolvo.personalhub.ui.theme.Electric
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.ui.theme.Violet
import com.teknosolvo.personalhub.ui.theme.Warning
import com.teknosolvo.personalhub.ui.tracker.BarChart
import com.teknosolvo.personalhub.ui.tracker.CurrentActivityCard
import com.teknosolvo.personalhub.ui.tracker.HealthCard
import com.teknosolvo.personalhub.ui.tracker.TimelineRow
import com.teknosolvo.personalhub.ui.tracker.refreshLocation
import com.teknosolvo.personalhub.ui.tracker.rememberNow
import com.teknosolvo.personalhub.ui.tracker.timelineOf
import com.teknosolvo.personalhub.util.durationShort
import com.teknosolvo.personalhub.util.zone
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun DashboardScreen(
    onOpenTracker: (Int) -> Unit,
    onOpenVault: () -> Unit,
    onOpenNotes: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onAddPlace: () -> Unit,
) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by c.settings.prefs.collectAsStateWithLifecycle()
    val state by c.tracker.stateFlow.collectAsStateWithLifecycle(TrackerStateEntity())
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())
    val unread by c.db.notifications().observeUnread().collectAsStateWithLifecycle(0)
    val now = rememberNow()
    val today = LocalDate.now()
    val weekStart = today.minusDays(6)
    val (from, _) = Stats.dayBounds(weekStart, zone())
    val (dayFrom, dayTo) = Stats.dayBounds(today, zone())
    val week by remember(weekStart) { c.tracker.overlapping(from - 86_400_000L, dayTo) }.collectAsStateWithLifecycle(emptyList())
    val health = rememberTrackerHealth()
    var refreshing by remember { mutableStateOf(false) }

    val todaySummary = Stats.daySummary(today, week, zone(), now)
    val current = week.firstOrNull { it.id == state.currentActivityId && it.isOpen }
    val todayTimeline = timelineOf(week, dayFrom, dayTo)
    val weekly = (0..6).map { i ->
        val d = weekStart.plusDays(i.toLong())
        Stats.dayName(d.dayOfWeek).take(3) to Stats.daySummary(d, week, zone(), now).ms(Bucket.OFFICE) / 3_600_000f
    }

    LazyColumn(
        Modifier.windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting() + (prefs.userName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = Hub.extras.muted)
                    Text("NAUY Personal Hub", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Cari") }
                IconButton(onClick = onNotifications) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) {
                        Icon(Icons.Filled.Notifications, "Notifikasi")
                    }
                }
            }
        }
        item {
            CurrentActivityCard(state, current, places, now, refreshing = refreshing, onRefresh = {
                refreshing = true
                scope.launch { context.toast(refreshLocation(c)); refreshing = false }
            })
        }
        item { HealthCard(health, compact = true) }
        if (places.isEmpty()) item {
            HubCard(onClick = onAddPlace) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.AddLocationAlt, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Daftarkan lokasi pertamamu", style = MaterialTheme.typography.titleSmall)
                        Text("Tambahkan Kantor dan Rumah agar kedatangan & kepulangan tercatat otomatis.",
                            style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                    }
                }
            }
        }
        item { SectionLabel("Ringkasan hari ini") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Di kantor", durationShort(todaySummary.ms(Bucket.OFFICE)), Icons.Filled.BusinessCenter, Violet, Modifier.weight(1f),
                    hint = todaySummary.firstOfficeArrival?.let { "datang ${Stats.hm(it)}" })
                StatTile("Di rumah", durationShort(todaySummary.ms(Bucket.HOME)), Icons.Filled.Home, Electric, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Istirahat", durationShort(todaySummary.ms(Bucket.BREAK)), Icons.Filled.Coffee, Warning, Modifier.weight(1f))
                StatTile("Luar area", durationShort(todaySummary.outsideMs), Icons.Filled.Explore, Color(0xFF22D3EE), Modifier.weight(1f))
            }
        }
        item {
            SectionLabel("Aktivitas terakhir") { TextButton(onClick = { onOpenTracker(2) }) { Text("Riwayat") } }
        }
        if (todayTimeline.isEmpty()) item {
            Text("Belum ada aktivitas tercatat hari ini.", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
        val recent = todayTimeline.takeLast(5)
        recent.forEachIndexed { i, e ->
            item(key = "t${e.activity.id}${e.isEnter}") {
                TimelineRow(e, places.firstOrNull { it.id == e.activity.placeId }, isLast = i == recent.lastIndex, now = now,
                    onClick = { onOpenTracker(2) })
            }
        }
        item { SectionLabel("Jam kantor 7 hari") { TextButton(onClick = { onOpenTracker(4) }) { Text("Statistik") } } }
        item { HubCard { BarChart(weekly, MaterialTheme.colorScheme.primary, highlightIndex = 6, height = 110.dp) } }
        item { SectionLabel("Akses cepat") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Shortcut("Lokasi", Icons.Filled.AddLocationAlt, Violet, Modifier.weight(1f)) { onOpenTracker(1) }
                Shortcut("Vault", Icons.Filled.Lock, Electric, Modifier.weight(1f), onOpenVault)
                Shortcut("Catatan", Icons.Filled.EditNote, Warning, Modifier.weight(1f), onOpenNotes)
                Shortcut("Bulanan", Icons.Filled.BarChart, Color(0xFF22D3EE), Modifier.weight(1f)) { onOpenTracker(3) }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Shortcut(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    HubCard(modifier = modifier, onClick = onClick, padding = 12.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, tint, size = 36.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 11 -> "Selamat pagi"
        h < 15 -> "Selamat siang"
        h < 18 -> "Selamat sore"
        else -> "Selamat malam"
    }
}

package com.teknosolvo.personalhub.ui.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.ActivityKind
import com.teknosolvo.personalhub.data.ActivitySource
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.LocationEvent
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.tracking.Stats
import com.teknosolvo.personalhub.ui.components.ConfirmDialog
import com.teknosolvo.personalhub.ui.components.DatePickDialog
import com.teknosolvo.personalhub.ui.components.EmptyState
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.TimePickDialog
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.util.DATE_KEY
import com.teknosolvo.personalhub.util.DATE_LONG
import com.teknosolvo.personalhub.util.TIME_HM
import com.teknosolvo.personalhub.util.durationLong
import com.teknosolvo.personalhub.util.hm
import com.teknosolvo.personalhub.util.toEpochMs
import com.teknosolvo.personalhub.util.toLocalDateTime
import com.teknosolvo.personalhub.util.zone
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun DailyTab(dateKey: String, onDateChange: (String) -> Unit, onEdit: (Long) -> Unit) {
    val c = LocalContainer.current
    val date = runCatching { LocalDate.parse(dateKey, DATE_KEY) }.getOrDefault(LocalDate.now())
    val (from, to) = Stats.dayBounds(date, zone())
    val activities by remember(from) { c.tracker.overlapping(from, to) }.collectAsStateWithLifecycle(emptyList())
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())
    val now = rememberNow()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf(false) }
    val summary = Stats.daySummary(date, activities, zone(), now)

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDateChange(date.minusDays(1).format(DATE_KEY)) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Sebelumnya") }
                TextButton(onClick = { pick = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                    Text(if (date == LocalDate.now()) "Hari ini · ${date.format(com.teknosolvo.personalhub.util.DATE_MEDIUM)}" else date.format(DATE_LONG))
                }
                IconButton(onClick = { onDateChange(date.plusDays(1).format(DATE_KEY)) }, enabled = date < LocalDate.now()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Berikutnya")
                }
            }
        }
        item {
            HubCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Datang kantor", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                        Text(summary.firstOfficeArrival?.let(Stats::hm) ?: "-", style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Pulang kantor", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                        Text(summary.lastOfficeLeave?.let(Stats::hm) ?: "-", style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Luar area", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                        Text(com.teknosolvo.personalhub.util.durationShort(summary.outsideMs), style = MaterialTheme.typography.titleLarge)
                    }
                }
                if (summary.totals.isNotEmpty()) { Spacer(Modifier.height(12.dp)); BucketBar(summary.totals) }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Timeline", "Kunjungan").forEachIndexed { i, l ->
                    SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                }
            }
        }
        if (activities.isEmpty()) item {
            EmptyState(Icons.Filled.EventBusy, "Tidak ada aktivitas",
                "Belum ada catatan untuk tanggal ini. Tambahkan aktivitas yang terlewat dengan tombol +.")
        } else if (mode == 0) {
            val entries = timelineOf(activities, from, to)
            entries.forEachIndexed { i, e ->
                item(key = "e${e.activity.id}-${e.isEnter}") {
                    TimelineRow(e, places.firstOrNull { it.id == e.activity.placeId }, i == entries.lastIndex, now) { onEdit(e.activity.id) }
                }
            }
            val carried = activities.filter { it.startAt < from }
            if (carried.isNotEmpty()) item {
                Text("${carried.size} kunjungan dimulai sebelum tanggal ini (mis. menginap di rumah).",
                    style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
            }
        } else {
            activities.forEach { a ->
                item(key = "v${a.id}") { VisitRow(a, places.firstOrNull { it.id == a.placeId }, now) { onEdit(a.id) } }
            }
        }
    }
    if (pick) DatePickDialog(date, onPick = { onDateChange(it.format(DATE_KEY)) }, onDismiss = { pick = false })
}

/**
 * Add / correct an activity.
 * id > 0: edit · id == 0: add a missed visit (place) · id == -1: add a personal activity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityEditScreen(id: Long, date: String, onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())

    var original by remember { mutableStateOf<DailyActivity?>(null) }
    var events by remember { mutableStateOf<List<LocationEvent>>(emptyList()) }
    var personal by rememberSaveable { mutableStateOf(id == -1L) }
    var placeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(runCatching { LocalDate.parse(date, DATE_KEY) }.getOrDefault(LocalDate.now()).format(DATE_KEY)) }
    var start by rememberSaveable { mutableStateOf(LocalTime.now().minusHours(1).withSecond(0).withNano(0).format(TIME_HM)) }
    var end by rememberSaveable { mutableStateOf<String?>(LocalTime.now().withSecond(0).withNano(0).format(TIME_HM)) }
    var endDayOffset by rememberSaveable { mutableIntStateOf(0) }
    var note by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id > 0) c.db.activities().byId(id)?.let { a ->
            original = a
            personal = a.kind == ActivityKind.PERSONAL
            placeId = a.placeId; title = a.title
            val s = a.startAt.toLocalDateTime()
            day = s.toLocalDate().format(DATE_KEY); start = s.toLocalTime().format(TIME_HM)
            end = a.endAt?.toLocalDateTime()?.let { e ->
                endDayOffset = (e.toLocalDate().toEpochDay() - s.toLocalDate().toEpochDay()).toInt()
                e.toLocalTime().format(TIME_HM)
            }
            note = a.note.orEmpty()
            events = c.db.events().between(a.startAt - 15 * 60_000, (a.endAt ?: System.currentTimeMillis()) + 15 * 60_000)
                .filter { it.activityId == a.id || it.placeId == a.placeId }
        }
    }
    LaunchedEffect(places, personal) {
        if (!personal && placeId == null && id <= 0) places.firstOrNull { it.enabled }?.let { placeId = it.id }
    }

    val dayD = LocalDate.parse(day, DATE_KEY)
    val startMs = dayD.atTime(LocalTime.parse(start, TIME_HM)).toEpochMs()
    val endMs = end?.let { dayD.plusDays(endDayOffset.toLong()).atTime(LocalTime.parse(it, TIME_HM)).toEpochMs() }
    val selectedPlace = places.firstOrNull { it.id == placeId }
    val error = when {
        personal && title.isBlank() -> "Isi nama aktivitas"
        !personal && selectedPlace == null && original?.placeId == null && original == null -> "Pilih lokasi"
        endMs != null && endMs < startMs -> "Waktu keluar sebelum waktu masuk (aktifkan \"+1 hari\" bila melewati tengah malam)"
        endMs == null && (original == null || original?.isOpen == false) -> "Isi waktu keluar"
        startMs > System.currentTimeMillis() -> "Waktu masuk di masa depan"
        else -> null
    }

    HubScaffold(
        title = when { id > 0 -> "Koreksi aktivitas"; personal -> "Aktivitas pribadi"; else -> "Tambah aktivitas" },
        subtitle = original?.let { "Sumber: ${ActivitySource.label(it.source)}" + if (it.edited) " · dikoreksi" else "" },
        onBack = onBack,
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Hapus", tint = Hub.extras.danger) } },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (id <= 0) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!personal, { personal = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Lokasi terdaftar") }
                    SegmentedButton(personal, { personal = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Pribadi / luar area") }
                }
            }
            if (personal) {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Aktivitas (mis. Meeting klien, Olahraga)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                SectionLabel("Lokasi")
                if (places.isEmpty()) Text("Belum ada lokasi terdaftar.", color = Hub.extras.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    places.forEach { p ->
                        FilterChip(selected = placeId == p.id, onClick = { placeId = p.id; title = p.name }, label = { Text(p.name) })
                    }
                }
                if (original != null && original?.placeId == null && !personal) {
                    Text("Lokasi asli \"${original?.title}\" sudah dihapus.", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                }
            }
            SectionLabel("Waktu")
            OutlinedButton(onClick = { picker = "date" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(dayD.format(DATE_LONG))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker = "start" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(6.dp)); Text("Masuk $start")
                }
                OutlinedButton(onClick = { picker = "end" }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Schedule, null); Spacer(Modifier.width(6.dp)); Text("Keluar ${end ?: "—"}")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keluar pada hari berikutnya (+1 hari)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(endDayOffset > 0, { endDayOffset = if (it) 1 else 0 })
            }
            if (original?.isOpen == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Masih berada di lokasi", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(end == null, { open -> end = if (open) null else LocalTime.now().withSecond(0).withNano(0).format(TIME_HM) })
                }
            }
            if (endMs != null && endMs >= startMs) {
                Text("Durasi: ${durationLong(endMs - startMs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(note, { note = it.take(500) }, label = { Text("Catatan pribadi") }, minLines = 2,
                modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Hub.extras.danger, style = MaterialTheme.typography.bodySmall) }
            Button(
                enabled = error == null,
                onClick = {
                    scope.launch {
                        try {
                            val o = original
                            val kind = if (personal) ActivityKind.PERSONAL else selectedPlace?.kind ?: o?.kind ?: PlaceKind.OTHER
                            val t = if (personal) title.trim() else selectedPlace?.name ?: o?.title ?: title
                            if (o == null) {
                                c.tracker.addManual(if (personal) null else placeId, t, kind, startMs, endMs, note)
                                context.toast("Aktivitas ditambahkan (manual)")
                            } else {
                                c.tracker.updateActivity(o.copy(
                                    placeId = if (personal) null else (placeId ?: o.placeId), title = t, kind = kind,
                                    startAt = startMs, endAt = endMs, note = note.takeIf { it.isNotBlank() },
                                ))
                                context.toast("Perubahan disimpan")
                            }
                            onBack()
                        } catch (e: IllegalArgumentException) {
                            context.toast(e.message ?: "Data tidak valid")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Simpan") }

            if (events.isNotEmpty()) {
                SectionLabel("Jejak deteksi")
                events.forEach { EventRow(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picker) {
        "date" -> DatePickDialog(dayD, { day = it.format(DATE_KEY) }, { picker = null })
        "start" -> TimePickDialog(LocalTime.parse(start, TIME_HM), { start = it.format(TIME_HM) }, { picker = null })
        "end" -> TimePickDialog(end?.let { LocalTime.parse(it, TIME_HM) } ?: LocalTime.now(), { end = it.format(TIME_HM) }, { picker = null })
    }
    if (confirmDelete) ConfirmDialog("Hapus catatan aktivitas?", "Catatan ini akan dihapus permanen dari riwayat.",
        onConfirm = { scope.launch { original?.let { c.tracker.deleteActivity(it) }; onBack() } }, onDismiss = { confirmDelete = false })
}

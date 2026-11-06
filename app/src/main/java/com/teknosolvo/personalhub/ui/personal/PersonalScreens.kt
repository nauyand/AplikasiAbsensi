package com.teknosolvo.personalhub.ui.personal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.ActivityKind
import com.teknosolvo.personalhub.data.NoteTag
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.data.QuickNote
import com.teknosolvo.personalhub.report.ReportExporter
import com.teknosolvo.personalhub.report.ReportFilter
import com.teknosolvo.personalhub.tracking.Stats
import com.teknosolvo.personalhub.ui.components.ConfirmDialog
import com.teknosolvo.personalhub.ui.components.DatePickDialog
import com.teknosolvo.personalhub.ui.components.EmptyState
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.Pill
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Electric
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.ui.theme.Violet
import com.teknosolvo.personalhub.ui.theme.Warning
import com.teknosolvo.personalhub.ui.tracker.VisitRow
import com.teknosolvo.personalhub.ui.tracker.rememberNow
import com.teknosolvo.personalhub.util.DATE_MEDIUM
import com.teknosolvo.personalhub.util.relativeTime
import com.teknosolvo.personalhub.util.zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

fun tagColor(tag: String): Color = when (tag) {
    NoteTag.WORK -> Violet
    NoteTag.IDEA -> Electric
    NoteTag.REMINDER -> Warning
    else -> Color(0xFFF87171)
}

private val personalTabs = listOf("Quick Notes", "Activity Notes", "Export Report")

@Composable
fun PersonalHubScreen(initialTab: Int, onEditNote: (Long) -> Unit, onEditActivity: (Long) -> Unit, onAddPersonal: () -> Unit) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    HubScaffold(
        title = "Personal",
        floatingActionButton = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(onClick = { onEditNote(0) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Catatan") })
                1 -> ExtendedFloatingActionButton(onClick = onAddPersonal, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Aktivitas") })
                else -> {}
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background, divider = {},
                indicator = { pos -> TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), width = 28.dp) }) {
                personalTabs.forEachIndexed { i, l -> Tab(tab == i, { tab = i }, text = { Text(l) }) }
            }
            when (tab) {
                0 -> NotesTab(onEditNote)
                1 -> ActivityNotesTab(onEditActivity)
                else -> ExportTab()
            }
        }
    }
}

@Composable
private fun NotesTab(onEdit: (Long) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val notes by c.db.notes().observeAll().collectAsStateWithLifecycle(emptyList())
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = notes.filter { (filter == null || it.tag == filter) && (query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true)) }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Cari catatan…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == null, { filter = null }, label = { Text("Semua") }) }
                items(NoteTag.all) { t -> FilterChip(filter == t, { filter = if (filter == t) null else t }, label = { Text(NoteTag.label(t)) }) }
            }
        }
        if (shown.isEmpty()) item {
            EmptyState(Icons.AutoMirrored.Filled.StickyNote2, "Belum ada catatan", "Simpan ide, pengingat, atau info penting secara cepat.")
        }
        items(shown, key = { it.id }) { n ->
            HubCard(onClick = { onEdit(n.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(NoteTag.label(n.tag), tagColor(n.tag))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { c.db.notes().upsert(n.copy(pinned = !n.pinned)) } }, modifier = Modifier.size(32.dp)) {
                        Icon(if (n.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Sematkan",
                            tint = if (n.pinned) MaterialTheme.colorScheme.primary else Hub.extras.muted, modifier = Modifier.size(18.dp))
                    }
                }
                if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleMedium)
                Text(n.body, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, color = Hub.extras.muted)
                Spacer(Modifier.height(4.dp))
                Text(relativeTime(n.updatedAt), style = MaterialTheme.typography.labelSmall, color = Hub.extras.muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(id: Long, onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<QuickNote?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf(NoteTag.WORK) }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id > 0) c.db.notes().byId(id)?.let { n -> original = n; title = n.title; body = n.body; tag = n.tag; pinned = n.pinned }
    }
    HubScaffold(if (id > 0) "Ubah catatan" else "Catatan baru", onBack = onBack, actions = {
        IconButton(onClick = { pinned = !pinned }) {
            Icon(if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Sematkan", tint = if (pinned) MaterialTheme.colorScheme.primary else Hub.extras.muted)
        }
        if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Hapus", tint = Hub.extras.danger) }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteTag.all.forEach { t -> FilterChip(tag == t, { tag = t }, label = { Text(NoteTag.label(t)) }) }
            }
            OutlinedTextField(title, { title = it.take(100) }, label = { Text("Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text("Isi catatan") }, minLines = 8, modifier = Modifier.fillMaxWidth())
            Button(enabled = title.isNotBlank() || body.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp), onClick = {
                scope.launch {
                    val now = System.currentTimeMillis()
                    val o = original
                    c.db.notes().upsert(QuickNote(id = o?.id ?: 0, title = title.trim(), body = body.trim(), tag = tag, pinned = pinned,
                        createdAt = o?.createdAt ?: now, updatedAt = now))
                    context.toast("Catatan disimpan"); onBack()
                }
            }) { Text("Simpan") }
        }
    }
    if (confirmDelete) ConfirmDialog("Hapus catatan?", "Catatan akan dihapus permanen.",
        onConfirm = { scope.launch { original?.let { c.db.notes().delete(it) }; onBack() } }, onDismiss = { confirmDelete = false })
}

/** Manual activities outside registered places, plus any activity carrying a personal note. */
@Composable
private fun ActivityNotesTab(onEdit: (Long) -> Unit) {
    val c = LocalContainer.current
    val now = rememberNow()
    val from = remember { Stats.dayBounds(LocalDate.now().minusDays(90), zone()).first }
    val to = remember { Stats.dayBounds(LocalDate.now(), zone()).second }
    val all by remember { c.tracker.overlapping(from, to) }.collectAsStateWithLifecycle(emptyList())
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())
    val list = all.filter { it.kind == ActivityKind.PERSONAL || !it.note.isNullOrBlank() }.sortedByDescending { it.startAt }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Catat aktivitas di luar lokasi terdaftar (meeting, olahraga, perjalanan) dan lihat semua catatan pribadi pada riwayat. 90 hari terakhir.",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
        if (list.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.DirectionsRun, "Belum ada aktivitas pribadi", "Tambahkan dengan tombol di bawah.") }
        var lastDate: String? = null
        list.forEach { a ->
            if (a.date != lastDate) {
                lastDate = a.date
                item(key = "h${a.date}") { SectionLabel(LocalDate.parse(a.date).format(DATE_MEDIUM)) }
            }
            item(key = a.id) { VisitRow(a, places.firstOrNull { it.id == a.placeId }, now) { onEdit(a.id) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportTab() {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())
    var from by rememberSaveable { mutableStateOf(YearMonth.now().atDay(1).toString()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var placeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    val filter = ReportFilter(LocalDate.parse(from), LocalDate.parse(to), placeId, kind)

    suspend fun rows() = withContext(Dispatchers.IO) {
        val (f, _) = Stats.dayBounds(filter.from, zone()); val (_, t) = Stats.dayBounds(filter.to, zone())
        ReportExporter.filter(c.tracker.overlappingNow(f, t), filter)
    }
    LaunchedEffect(from, to, placeId, kind) { count = rows().size }

    fun write(uri: android.net.Uri?, pdf: Boolean) {
        c.session.release()
        if (uri == null) return
        scope.launch {
            runCatching {
                val r = rows()
                val bytes = withContext(Dispatchers.Default) {
                    if (pdf) ReportExporter.pdf(r, filter, "Laporan Aktivitas Lokasi") else ReportExporter.csv(r)
                }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
            }.onSuccess { context.toast("Laporan tersimpan") }.onFailure { context.toast("Gagal: ${it.message}") }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { write(it, false) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { write(it, true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Periode")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = "from" }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(LocalDate.parse(from).format(DATE_MEDIUM))
            }
            OutlinedButton(onClick = { picking = "to" }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(LocalDate.parse(to).format(DATE_MEDIUM))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ym = YearMonth.now()
            FilterChip(false, { from = ym.atDay(1).toString(); to = LocalDate.now().toString() }, label = { Text("Bulan ini") })
            FilterChip(false, { val p = ym.minusMonths(1); from = p.atDay(1).toString(); to = p.atEndOfMonth().toString() }, label = { Text("Bulan lalu") })
            FilterChip(false, { from = LocalDate.now().withDayOfYear(1).toString(); to = LocalDate.now().toString() }, label = { Text("Tahun ini") })
        }
        SectionLabel("Lokasi")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(placeId == null, { placeId = null }, label = { Text("Semua") })
            places.forEach { p -> FilterChip(placeId == p.id, { placeId = p.id }, label = { Text(p.name) }) }
        }
        SectionLabel("Jenis aktivitas")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == null, { kind = null }, label = { Text("Semua") })
            (PlaceKind.all + ActivityKind.PERSONAL).forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text(PlaceKind.label(k)) }) }
        }
        HubCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("${count ?: "…"} catatan aktivitas akan diekspor", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val name = "laporan-aktivitas_${from}_$to"
        Button(onClick = { c.session.hold(); csvLauncher.launch("$name.csv") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.TableChart, null); Spacer(Modifier.width(8.dp)); Text("Ekspor CSV")
        }
        OutlinedButton(onClick = { c.session.hold(); pdfLauncher.launch("$name.pdf") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("Ekspor PDF")
        }
        Text("Laporan hanya berisi riwayat lokasi (tanpa data vault). File disimpan ke lokasi yang kamu pilih.",
            style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
    }
    when (picking) {
        "from" -> DatePickDialog(LocalDate.parse(from), { from = it.toString() }, { picking = null })
        "to" -> DatePickDialog(LocalDate.parse(to), { to = it.toString() }, { picking = null })
    }
}

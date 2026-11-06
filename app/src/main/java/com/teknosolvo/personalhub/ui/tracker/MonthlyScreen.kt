package com.teknosolvo.personalhub.ui.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.ActivityKind
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.tracking.Bucket
import com.teknosolvo.personalhub.tracking.Stats
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.IconBadge
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.StatTile
import com.teknosolvo.personalhub.ui.theme.Electric
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.ui.theme.Violet
import com.teknosolvo.personalhub.ui.theme.Warning
import com.teknosolvo.personalhub.util.DATE_KEY
import com.teknosolvo.personalhub.util.DATE_LONG
import com.teknosolvo.personalhub.util.MONTH_YEAR
import com.teknosolvo.personalhub.util.durationShort
import com.teknosolvo.personalhub.util.zone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private val kindFilters = listOf<String?>(null, PlaceKind.OFFICE, PlaceKind.HOME, PlaceKind.BREAK, PlaceKind.FOOD, PlaceKind.OTHER, ActivityKind.PERSONAL)

fun List<DailyActivity>.filtered(placeId: Long?, kind: String?) =
    filter { (placeId == null || it.placeId == placeId) && (kind == null || it.kind == kind) }

@Composable
fun MonthlyTab(onOpenDay: (String) -> Unit, onEdit: (Long) -> Unit) {
    val c = LocalContainer.current
    var ymText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(ymText)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var placeFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val (from, _) = Stats.dayBounds(ym.atDay(1), zone())
    val (_, to) = Stats.dayBounds(ym.atEndOfMonth(), zone())
    val all by remember(from) { c.tracker.overlapping(from, to) }.collectAsStateWithLifecycle(emptyList())
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())
    val now = rememberNow(60_000)
    val activities = all.filtered(placeFilter, kindFilter)
    val summary = Stats.month(ym, activities, zone(), now)
    val byDate = summary.days.associateBy { it.date }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ymText = ym.minusMonths(1).toString(); selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Bulan sebelumnya") }
                Text(ym.format(MONTH_YEAR).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { ymText = ym.plusMonths(1).toString(); selected = null }, enabled = ym < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Bulan berikutnya")
                }
            }
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { ymText = ym.minusYears(1).toString() }) { Text("« ${ym.year - 1}") }
                TextButton(onClick = { ymText = YearMonth.now().toString() }) { Text("Bulan ini") }
                TextButton(onClick = { ymText = ym.plusYears(1).toString() }, enabled = ym.plusYears(1) <= YearMonth.now()) { Text("${ym.year + 1} »") }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(placeFilter == null, { placeFilter = null }, label = { Text("Semua lokasi") }) }
                items(places, key = { it.id }) { p ->
                    FilterChip(placeFilter == p.id, { placeFilter = if (placeFilter == p.id) null else p.id }, label = { Text(p.name) })
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(kindFilters) { k ->
                    FilterChip(kindFilter == k, { kindFilter = k }, label = { Text(k?.let(PlaceKind::label) ?: "Semua jenis") })
                }
            }
        }
        item { HubCard(padding = 12.dp) { MonthCalendar(ym, byDate.mapValues { it.value.totals }, selected) { d ->
            selected = if (selected == d.format(DATE_KEY)) null else d.format(DATE_KEY)
        } } }
        selected?.let { key ->
            val d = LocalDate.parse(key, DATE_KEY)
            val (df, dt) = Stats.dayBounds(d, zone())
            val dayActs = activities.filter { it.startAt < dt && (it.endAt ?: now) > df }
            item {
                SectionLabel(d.format(DATE_LONG)) { TextButton(onClick = { onOpenDay(key) }) { Text("Buka harian") } }
            }
            if (dayActs.isEmpty()) item { Text("Tidak ada aktivitas tercatat.", color = Hub.extras.muted, style = MaterialTheme.typography.bodySmall) }
            dayActs.forEach { a -> item(key = "m${a.id}") { VisitRow(a, places.firstOrNull { it.id == a.placeId }, now) { onEdit(a.id) } } }
        }
        item { SectionLabel("Ringkasan bulan") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Total di kantor", durationShort(summary.officeMs), Icons.Filled.BusinessCenter, Violet, Modifier.weight(1f))
                StatTile("Total di rumah", durationShort(summary.homeMs), Icons.Filled.Home, Electric, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Total istirahat", durationShort(summary.breakMs), Icons.Filled.Coffee, Warning, Modifier.weight(1f))
                StatTile("Di luar lokasi terdaftar", durationShort(summary.outsideMs), Icons.Filled.Explore, Color(0xFF22D3EE), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Rata-rata datang", summary.avgOfficeArrival?.let(Stats::hm) ?: "-", Icons.AutoMirrored.Filled.Login, Hub.extras.success, Modifier.weight(1f))
                StatTile("Rata-rata pulang", summary.avgOfficeLeave?.let(Stats::hm) ?: "-", Icons.AutoMirrored.Filled.Logout, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Hari di kantor", "${summary.officeDays} hari", Icons.Filled.EventAvailable, Violet, Modifier.weight(1f))
                StatTile("Hari tanpa aktivitas", "${summary.daysWithoutActivity} hari", Icons.Filled.EventBusy, Hub.extras.muted, Modifier.weight(1f))
            }
        }
        item { SectionLabel("Jam kantor per hari") }
        item {
            HubCard {
                val bars = summary.days.map { d -> (if (d.date.dayOfMonth % 5 == 1) "${d.date.dayOfMonth}" else "") to d.ms(Bucket.OFFICE) / 3_600_000f }
                BarChart(bars, Violet, height = 120.dp, valueLabel = { "" })
            }
        }
        item { SectionLabel("Per minggu") }
        item {
            HubCard {
                val weeks = summary.days.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSortedMap()
                val bars = weeks.entries.mapIndexed { i, (_, ds) -> "M${i + 1}" to ds.sumOf { it.ms(Bucket.OFFICE) } / 3_600_000f }
                BarChart(bars, Electric, height = 110.dp, valueLabel = { if (it <= 0) "" else "%.0fj".format(it) })
                Spacer(Modifier.height(8.dp))
                val totals = mapOf(Bucket.OFFICE to summary.officeMs, Bucket.HOME to summary.homeMs, Bucket.BREAK to summary.breakMs,
                    Bucket.PERSONAL to summary.personalMs).filterValues { it > 0 }
                if (totals.isNotEmpty()) BucketBar(totals)
            }
        }
    }
}

@Composable
fun MonthCalendar(ym: YearMonth, totals: Map<LocalDate, Map<Bucket, Long>>, selected: String?, onClick: (LocalDate) -> Unit) {
    val first = ym.atDay(1)
    val offset = first.dayOfWeek.value - 1 // Monday first
    val cells = offset + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now()
    Column {
        Row {
            listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Hub.extras.muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        for (r in 0 until rows) {
            Row {
                for (col in 0 until 7) {
                    val idx = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (idx in 1..ym.lengthOfMonth()) {
                            val d = ym.atDay(idx)
                            val t = totals[d].orEmpty().filterValues { it > 0 }
                            val isSel = selected == d.format(DATE_KEY)
                            val future = d.isAfter(today)
                            Column(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.small)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
                                    .then(if (d == today) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                                    .clickable(enabled = !future) { onClick(d) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$idx", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (t.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (future) Hub.extras.muted.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                    t.keys.sortedBy { it.ordinal }.take(3).forEach { b ->
                                        Box(Modifier.size(5.dp).clip(CircleShape).background(bucketColor(b)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsTab() {
    val c = LocalContainer.current
    var range by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val start = when (range) {
        0 -> today.withDayOfMonth(1)
        1 -> today.minusMonths(2).withDayOfMonth(1)
        else -> today.withDayOfYear(1)
    }
    val (from, _) = Stats.dayBounds(start, zone())
    val (_, to) = Stats.dayBounds(today, zone())
    val activities by remember(from) { c.tracker.overlapping(from, to) }.collectAsStateWithLifecycle(emptyList())
    val now = rememberNow(60_000)
    val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
    val summary = Stats.period(dates, activities, zone(), now)
    val insights = Stats.insights(summary, activities)
    val monthSummary = Stats.month(YearMonth.now(), activities, zone(), now)

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Bulan ini", "3 bulan", "Tahun ini").forEachIndexed { i, l ->
                    SegmentedButton(range == i, { range = i }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                }
            }
        }
        item {
            HubCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Insight otomatis", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                insights.sentences.forEach {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("•", color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Kedatangan paling awal", insights.earliestArrival?.let(Stats::hm) ?: "-", Icons.Filled.WbSunny, Warning, Modifier.weight(1f))
                StatTile("Kedatangan paling sering", insights.commonArrivalSlot?.let { "${Stats.hm(it)}–${Stats.hm(it.plusMinutes(15))}" } ?: "-",
                    Icons.Filled.AccessTime, Violet, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Rata-rata di kantor / hari", durationShort(insights.avgOfficeMsPerDay), Icons.Filled.Timer, Electric, Modifier.weight(1f))
                StatTile("Rata-rata istirahat / hari", durationShort(insights.avgBreakMsPerDay), Icons.Filled.Coffee, Warning, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Total kantor bulan ini", durationShort(monthSummary.officeMs), Icons.Filled.BusinessCenter, Violet, Modifier.weight(1f))
                StatTile("Paling sering dikunjungi", insights.mostVisitedPlace ?: "-", Icons.Filled.Place, Hub.extras.success, Modifier.weight(1f),
                    hint = if (insights.mostVisitedCount > 0) "${insights.mostVisitedCount} kunjungan" else null)
            }
        }
        item { SectionLabel("Pola mingguan (rata-rata jam kantor)") }
        item {
            HubCard {
                val bars = DayOfWeek.entries.map { Stats.dayName(it).take(3) to (insights.weekdayPatternMs[it] ?: 0) / 3_600_000f }
                BarChart(bars, MaterialTheme.colorScheme.primary, height = 130.dp, highlightIndex = today.dayOfWeek.value - 1)
            }
        }
        item {
            SectionLabel("Distribusi waktu")
            HubCard {
                val totals = mapOf(Bucket.OFFICE to summary.officeMs, Bucket.HOME to summary.homeMs, Bucket.BREAK to summary.breakMs,
                    Bucket.PERSONAL to summary.personalMs).filterValues { it > 0 }
                if (totals.isEmpty()) Text("Belum ada data.", color = Hub.extras.muted) else BucketBar(totals)
            }
        }
    }
}

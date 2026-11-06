package com.teknosolvo.personalhub.ui.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.teknosolvo.personalhub.data.ActivityKind
import com.teknosolvo.personalhub.data.ActivitySource
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.tracking.Bucket
import com.teknosolvo.personalhub.ui.components.Pill
import com.teknosolvo.personalhub.ui.components.asColor
import com.teknosolvo.personalhub.ui.components.placeIcon
import com.teknosolvo.personalhub.ui.theme.Electric
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.ui.theme.Violet
import com.teknosolvo.personalhub.ui.theme.Warning
import com.teknosolvo.personalhub.util.durationShort
import com.teknosolvo.personalhub.util.hm
import kotlinx.coroutines.delay

/** Ticks every [periodMs] so durations of open visits stay live. */
@Composable
fun rememberNow(periodMs: Long = 30_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

fun bucketColor(b: Bucket): Color = when (b) {
    Bucket.OFFICE -> Violet
    Bucket.HOME -> Electric
    Bucket.BREAK -> Warning
    Bucket.OTHER_PLACE -> Color(0xFF22D3EE)
    Bucket.PERSONAL -> Color(0xFFEC4899)
}

fun activityColor(a: DailyActivity, place: Place?): Color =
    place?.colorArgb?.asColor() ?: when (a.kind) {
        PlaceKind.OFFICE -> Violet
        PlaceKind.HOME -> Electric
        PlaceKind.BREAK, PlaceKind.FOOD -> Warning
        ActivityKind.PERSONAL -> Color(0xFFEC4899)
        else -> Color(0xFF22D3EE)
    }

fun activityIconKey(a: DailyActivity, place: Place?): String = place?.iconKey ?: when (a.kind) {
    PlaceKind.OFFICE -> "office"
    PlaceKind.HOME -> "home"
    PlaceKind.BREAK -> "coffee"
    PlaceKind.FOOD -> "food"
    else -> "place"
}

/** Verb phrases used in the timeline: "Masuk Kantor", "Istirahat di Warung", … */
fun enterLabel(a: DailyActivity): String = when (a.kind) {
    PlaceKind.BREAK, PlaceKind.FOOD -> "Istirahat · ${a.title}"
    ActivityKind.PERSONAL -> a.title
    else -> "Masuk ${a.title}"
}

fun exitLabel(a: DailyActivity): String = when (a.kind) {
    PlaceKind.BREAK, PlaceKind.FOOD -> "Selesai istirahat · ${a.title}"
    ActivityKind.PERSONAL -> "Selesai · ${a.title}"
    else -> "Keluar ${a.title}"
}

@Composable
fun SourceBadges(a: DailyActivity) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (a.isOpen) Pill("Berlangsung", Hub.extras.success)
        if (a.source == ActivitySource.MANUAL) Pill("Manual", Warning)
        if (a.edited) Pill("Dikoreksi", Warning)
        if (a.source == ActivitySource.SIMULATED) Pill("Simulasi", Color(0xFFEC4899))
    }
}

/** Visit card: icon, name, in → out, duration, badges, note. */
@Composable
fun VisitRow(a: DailyActivity, place: Place?, now: Long, onClick: () -> Unit) {
    val color = activityColor(a, place)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.teknosolvo.personalhub.ui.components.IconBadge(placeIcon(activityIconKey(a, place)), color, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${a.startAt.hm()} → ${a.endAt?.hm() ?: "sekarang"}",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted,
            )
            Spacer(Modifier.height(4.dp))
            SourceBadges(a)
            a.note?.let {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EditNote, null, tint = Hub.extras.muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted, maxLines = 2)
                }
            }
        }
        Text(durationShort((a.endAt ?: now) - a.startAt), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

data class TimelineEntry(val time: Long, val label: String, val activity: DailyActivity, val isEnter: Boolean)

fun timelineOf(activities: List<DailyActivity>, from: Long, to: Long): List<TimelineEntry> = buildList {
    activities.forEach { a ->
        if (a.startAt in from until to) add(TimelineEntry(a.startAt, enterLabel(a), a, true))
        a.endAt?.let { if (it in from until to) add(TimelineEntry(it, exitLabel(a), a, false)) }
    }
}.sortedWith(compareBy({ it.time }, { if (it.isEnter) 1 else 0 }))

@Composable
fun TimelineRow(e: TimelineEntry, place: Place?, isLast: Boolean, now: Long, onClick: () -> Unit) {
    val color = activityColor(e.activity, place)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick)) {
        Text(
            e.time.hm(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(54.dp).padding(top = 12.dp), textAlign = TextAlign.Start,
        )
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 16.dp).size(12.dp).clip(CircleShape).background(if (e.isEnter) color else color.copy(alpha = 0.35f)))
            if (!isLast) Box(Modifier.padding(top = 30.dp).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (e.isEnter) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout, null,
                    tint = color, modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val dur = if (e.isEnter) (if (e.activity.isOpen) "berlangsung ${durationShort(now - e.activity.startAt)}" else null)
            else "durasi ${durationShort((e.activity.endAt ?: now) - e.activity.startAt)}"
            if (dur != null) Text(dur, style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
            if (e.isEnter) {
                if (e.activity.isManual || e.activity.source == ActivitySource.SIMULATED) {
                    Spacer(Modifier.height(2.dp)); SourceBadges(e.activity.copy(endAt = e.activity.endAt ?: 0))
                }
                e.activity.note?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted) }
            }
        }
    }
}

/** Simple rounded bar chart drawn with Canvas. */
@Composable
fun BarChart(
    values: List<Pair<String, Float>>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    highlightIndex: Int = -1,
    valueLabel: (Float) -> String = { if (it <= 0f) "" else "%.1f".format(it) },
) {
    val max = (values.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            values.forEach { (_, v) ->
                Text(valueLabel(v), style = MaterialTheme.typography.labelSmall, color = Hub.extras.muted,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 4.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, (_, v) ->
                val x = slot * i + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                val h = size.height * (v / max)
                if (h > 0f) {
                    val c = if (highlightIndex == -1 || highlightIndex == i) color else color.copy(alpha = 0.55f)
                    drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            values.forEach { (label, _) ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = Hub.extras.muted,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** Horizontal proportion bar for bucket totals with legend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BucketBar(totals: Map<Bucket, Long>, modifier: Modifier = Modifier) {
    val sum = totals.values.sum().coerceAtLeast(1)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Bucket.entries.forEach { b ->
                val v = totals[b] ?: 0
                if (v > 0) Box(Modifier.weight(v.toFloat() / sum).fillMaxHeight().background(bucketColor(b)))
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Bucket.entries.filter { (totals[it] ?: 0) > 0 }.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(bucketColor(b)))
                    Spacer(Modifier.width(4.dp))
                    Text("${bucketLabel(b)} ${durationShort(totals[b] ?: 0)}", style = MaterialTheme.typography.bodySmall,
                        color = Hub.extras.muted)
                }
            }
        }
    }
}

fun bucketLabel(b: Bucket) = when (b) {
    Bucket.OFFICE -> "Kantor"
    Bucket.HOME -> "Rumah"
    Bucket.BREAK -> "Istirahat"
    Bucket.OTHER_PLACE -> "Lainnya"
    Bucket.PERSONAL -> "Pribadi"
}

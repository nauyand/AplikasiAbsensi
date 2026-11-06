package com.teknosolvo.personalhub.tracking

import com.teknosolvo.personalhub.data.ActivityKind
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.PlaceKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/** Time bucket used for statistics. */
enum class Bucket { OFFICE, HOME, BREAK, OTHER_PLACE, PERSONAL }

fun bucketOf(kind: String): Bucket = when (kind) {
    PlaceKind.OFFICE -> Bucket.OFFICE
    PlaceKind.HOME -> Bucket.HOME
    PlaceKind.BREAK, PlaceKind.FOOD -> Bucket.BREAK
    ActivityKind.PERSONAL -> Bucket.PERSONAL
    else -> Bucket.OTHER_PLACE
}

data class DaySummary(
    val date: LocalDate,
    val totals: Map<Bucket, Long>,
    /** Time not covered by any registered-place visit (within the observed part of the day). */
    val outsideMs: Long,
    val firstOfficeArrival: LocalTime?,
    val lastOfficeLeave: LocalTime?,
    val activityCount: Int,
) {
    val hasActivity get() = activityCount > 0
    fun ms(b: Bucket) = totals[b] ?: 0L
}

data class PeriodSummary(
    val days: List<DaySummary>,
    val officeMs: Long,
    val homeMs: Long,
    val breakMs: Long,
    val outsideMs: Long,
    val personalMs: Long,
    val avgOfficeArrival: LocalTime?,
    val avgOfficeLeave: LocalTime?,
    val officeDays: Int,
    val daysWithoutActivity: Int,
)

data class Insights(
    val earliestArrival: LocalTime?,
    /** Most frequent 15-minute arrival slot start. */
    val commonArrivalSlot: LocalTime?,
    val avgOfficeMsPerDay: Long,
    val avgBreakMsPerDay: Long,
    val totalOfficeMs: Long,
    val mostVisitedPlace: String?,
    val mostVisitedCount: Int,
    /** Average office hours per weekday (Mon..Sun). */
    val weekdayPatternMs: Map<DayOfWeek, Long>,
    val sentences: List<String>,
)

object Stats {

    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Duration of [a] that falls inside [from, to); open activities count until [now]. */
    fun clippedMs(a: DailyActivity, from: Long, to: Long, now: Long): Long {
        val end = a.endAt ?: now
        val s = maxOf(a.startAt, from)
        val e = minOf(end, to)
        return (e - s).coerceAtLeast(0)
    }

    fun daySummary(date: LocalDate, activities: List<DailyActivity>, zone: ZoneId, now: Long): DaySummary {
        val (from, to) = dayBounds(date, zone)
        val inDay = activities.filter { it.startAt < to && (it.endAt ?: now) > from }
        val totals = mutableMapOf<Bucket, Long>()
        for (a in inDay) {
            val b = bucketOf(a.kind)
            totals[b] = (totals[b] ?: 0) + clippedMs(a, from, to, now)
        }
        // Outside registered places: observed span minus union of place visits.
        val observedEnd = minOf(to, now)
        val placeIntervals = inDay.filter { it.kind != ActivityKind.PERSONAL }
            .map { maxOf(it.startAt, from) to minOf(it.endAt ?: now, to) }
            .filter { it.second > it.first }
        val outside = if (inDay.isEmpty() || observedEnd <= from) 0L
        else (observedEnd - from) - unionLength(placeIntervals)

        val office = inDay.filter { it.kind == PlaceKind.OFFICE }
        val arrival = office.filter { it.startAt >= from }.minOfOrNull { it.startAt }
        val leave = office.mapNotNull { it.endAt }.filter { it in from until to }.maxOrNull()
        return DaySummary(
            date = date,
            totals = totals,
            outsideMs = outside.coerceAtLeast(0),
            firstOfficeArrival = arrival?.let { localTime(it, zone) },
            lastOfficeLeave = leave?.let { localTime(it, zone) },
            activityCount = inDay.size,
        )
    }

    fun period(dates: List<LocalDate>, activities: List<DailyActivity>, zone: ZoneId, now: Long): PeriodSummary {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val days = dates.filter { !it.isAfter(today) }.map { daySummary(it, activities, zone, now) }
        val arrivals = days.mapNotNull { it.firstOfficeArrival }
        val leaves = days.mapNotNull { it.lastOfficeLeave }
        return PeriodSummary(
            days = days,
            officeMs = days.sumOf { it.ms(Bucket.OFFICE) },
            homeMs = days.sumOf { it.ms(Bucket.HOME) },
            breakMs = days.sumOf { it.ms(Bucket.BREAK) },
            outsideMs = days.sumOf { it.outsideMs },
            personalMs = days.sumOf { it.ms(Bucket.PERSONAL) },
            avgOfficeArrival = averageTime(arrivals),
            avgOfficeLeave = averageTime(leaves),
            officeDays = days.count { it.ms(Bucket.OFFICE) > 0 },
            daysWithoutActivity = days.count { !it.hasActivity },
        )
    }

    fun month(ym: YearMonth, activities: List<DailyActivity>, zone: ZoneId, now: Long): PeriodSummary =
        period((1..ym.lengthOfMonth()).map { ym.atDay(it) }, activities, zone, now)

    fun insights(summary: PeriodSummary, activities: List<DailyActivity>): Insights {
        val arrivals = summary.days.mapNotNull { it.firstOfficeArrival }
        val slot = arrivals.groupingBy { LocalTime.of(it.hour, it.minute / 15 * 15) }.eachCount()
            .maxWithOrNull(compareBy<Map.Entry<LocalTime, Int>> { it.value }.thenByDescending { it.key })?.key
        val officeDays = summary.days.filter { it.ms(Bucket.OFFICE) > 0 }
        val avgOffice = if (officeDays.isEmpty()) 0 else officeDays.sumOf { it.ms(Bucket.OFFICE) } / officeDays.size
        val breakDays = summary.days.filter { it.ms(Bucket.BREAK) > 0 }
        val avgBreak = if (breakDays.isEmpty()) 0 else breakDays.sumOf { it.ms(Bucket.BREAK) } / breakDays.size
        val visits = activities.filter { it.kind != ActivityKind.PERSONAL }.groupingBy { it.title }.eachCount()
        val top = visits.maxByOrNull { it.value }
        val pattern = DayOfWeek.entries.associateWith { dow ->
            val ds = summary.days.filter { it.date.dayOfWeek == dow && it.ms(Bucket.OFFICE) > 0 }
            if (ds.isEmpty()) 0L else ds.sumOf { it.ms(Bucket.OFFICE) } / ds.size
        }

        val sentences = buildList {
            summary.avgOfficeArrival?.let { add("Rata-rata kamu tiba di kantor pukul ${hm(it)} pada periode ini.") }
            summary.avgOfficeLeave?.let { add("Rata-rata kamu meninggalkan kantor pukul ${hm(it)}.") }
            arrivals.minOrNull()?.let { add("Kedatangan paling awal tercatat pukul ${hm(it)}.") }
            if (summary.officeMs > 0) {
                add("Total ${formatHours(summary.officeMs)} di kantor dalam ${summary.officeDays} hari.")
            }
            if (avgBreak > 0) add("Rata-rata waktu istirahat ${formatHours(avgBreak)} per hari.")
            val busiest = pattern.maxByOrNull { it.value }
            if (busiest != null && busiest.value > 0) {
                add("Hari terpanjang di kantor biasanya ${dayName(busiest.key)} (${formatHours(busiest.value)}).")
            }
            top?.let { add("Lokasi paling sering dikunjungi: ${it.key} (${it.value} kunjungan).") }
            if (isEmpty()) add("Belum ada cukup data. Daftarkan lokasi dan biarkan tracker bekerja beberapa hari.")
        }
        return Insights(
            earliestArrival = arrivals.minOrNull(),
            commonArrivalSlot = slot,
            avgOfficeMsPerDay = avgOffice,
            avgBreakMsPerDay = avgBreak,
            totalOfficeMs = summary.officeMs,
            mostVisitedPlace = top?.key,
            mostVisitedCount = top?.value ?: 0,
            weekdayPatternMs = pattern,
            sentences = sentences,
        )
    }

    fun averageTime(times: List<LocalTime>): LocalTime? {
        if (times.isEmpty()) return null
        val avg = times.map { it.toSecondOfDay().toDouble() }.average().roundToInt()
        return LocalTime.ofSecondOfDay((avg / 60 * 60).toLong())
    }

    fun unionLength(intervals: List<Pair<Long, Long>>): Long {
        var total = 0L
        var curS = Long.MIN_VALUE
        var curE = Long.MIN_VALUE
        for ((s, e) in intervals.sortedBy { it.first }) {
            if (s > curE) {
                if (curE > curS) total += curE - curS
                curS = s; curE = e
            } else if (e > curE) curE = e
        }
        if (curE > curS) total += curE - curS
        return total
    }

    fun localTime(ms: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().withSecond(0).withNano(0)

    fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

    fun formatHours(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m menit"
            m == 0L -> "$h jam"
            else -> "$h jam $m menit"
        }
    }

    fun dayName(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "Senin"
        DayOfWeek.TUESDAY -> "Selasa"
        DayOfWeek.WEDNESDAY -> "Rabu"
        DayOfWeek.THURSDAY -> "Kamis"
        DayOfWeek.FRIDAY -> "Jumat"
        DayOfWeek.SATURDAY -> "Sabtu"
        DayOfWeek.SUNDAY -> "Minggu"
    }
}

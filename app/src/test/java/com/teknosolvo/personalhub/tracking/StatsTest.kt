package com.teknosolvo.personalhub.tracking

import com.teknosolvo.personalhub.data.ActivityKind
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.PlaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class StatsTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val day = LocalDate.of(2026, 11, 6)

    private fun ms(d: LocalDate, h: Int, m: Int) = LocalDateTime.of(d, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    private fun visit(kind: String, d: LocalDate, sh: Int, sm: Int, eh: Int?, em: Int?, title: String = kind, endDay: LocalDate = d) =
        DailyActivity(placeId = 1, title = title, kind = kind, date = d.toString(), startAt = ms(d, sh, sm),
            endAt = eh?.let { ms(endDay, it, em!!) }, createdAt = 0, updatedAt = 0)

    private val sample = listOf(
        visit(PlaceKind.HOME, day.minusDays(1), 18, 0, 7, 15, "Rumah", endDay = day),
        visit(PlaceKind.OFFICE, day, 8, 12, 12, 5, "Kantor"),
        visit(PlaceKind.FOOD, day, 12, 10, 12, 45, "Warung"),
        visit(PlaceKind.OFFICE, day, 12, 48, 17, 10, "Kantor"),
        visit(PlaceKind.HOME, day, 18, 0, null, null, "Rumah"),
    )

    @Test fun `day summary clips overnight visits and computes totals`() {
        val now = ms(day, 20, 0)
        val s = Stats.daySummary(day, sample, zone, now)
        assertEquals((7 * 60 + 15) * 60_000L + 2 * 3_600_000L, s.ms(Bucket.HOME)) // 00:00–07:15 + 18:00–20:00
        assertEquals((3 * 60 + 53 + 4 * 60 + 22) * 60_000L, s.ms(Bucket.OFFICE))
        assertEquals(35 * 60_000L, s.ms(Bucket.BREAK))
        assertEquals(LocalTime.of(8, 12), s.firstOfficeArrival)
        assertEquals(LocalTime.of(17, 10), s.lastOfficeLeave)
        // Observed 00:00–20:00 minus covered time.
        val covered = s.ms(Bucket.HOME) + s.ms(Bucket.OFFICE) + s.ms(Bucket.BREAK)
        assertEquals(20 * 3_600_000L - covered, s.outsideMs)
    }

    @Test fun `period averages arrival and leave times`() {
        val d2 = day.plusDays(1)
        val acts = sample + visit(PlaceKind.OFFICE, d2, 8, 30, 17, 30, "Kantor")
        val p = Stats.period(listOf(day, d2), acts, zone, ms(d2, 23, 0))
        assertEquals(LocalTime.of(8, 21), p.avgOfficeArrival)
        assertEquals(LocalTime.of(17, 20), p.avgOfficeLeave)
        assertEquals(2, p.officeDays)
        assertEquals(0, p.daysWithoutActivity)
    }

    @Test fun `month counts days without activity up to today only`() {
        val now = ms(day, 20, 0)
        val p = Stats.month(YearMonth.of(2026, 11), sample, zone, now)
        assertEquals(6, p.days.size) // 1..6 November
        assertEquals(4, p.daysWithoutActivity) // 1–4 Nov empty; 5 Nov has the evening at home
    }

    @Test fun `insights describe arrival time`() {
        val now = ms(day, 20, 0)
        val p = Stats.month(YearMonth.of(2026, 11), sample, zone, now)
        val i = Stats.insights(p, sample)
        assertTrue(i.sentences.first().contains("08:12"))
        assertEquals(LocalTime.of(8, 0), i.commonArrivalSlot)
        assertEquals(2, i.mostVisitedCount)
        assertTrue(i.mostVisitedPlace in setOf("Kantor", "Rumah"))
    }

    @Test fun `personal activities do not reduce outside time`() {
        val acts = listOf(DailyActivity(placeId = null, title = "Meeting", kind = ActivityKind.PERSONAL, date = day.toString(),
            startAt = ms(day, 9, 0), endAt = ms(day, 10, 0), createdAt = 0, updatedAt = 0))
        val s = Stats.daySummary(day, acts, zone, ms(day, 12, 0))
        assertEquals(12 * 3_600_000L, s.outsideMs)
        assertEquals(3_600_000L, s.ms(Bucket.PERSONAL))
    }

    @Test fun `union length merges overlaps`() {
        assertEquals(30L, Stats.unionLength(listOf(0L to 10L, 5L to 20L, 25L to 35L)))
        assertEquals(0L, Stats.unionLength(emptyList()))
    }
}

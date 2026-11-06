package com.teknosolvo.personalhub

import com.teknosolvo.personalhub.data.ActivitySource
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.util.dateKey
import com.teknosolvo.personalhub.util.toEpochMs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * DEBUG BUILD ONLY. Seeds 30 days of sample history so the history / monthly / statistics screens
 * can be checked. Every seeded activity is marked [ActivitySource.SIMULATED] and can be removed with
 * "Hapus data simulasi"; places are created only if none exist and are named "(Dev)".
 */
object DevTools {
    const val available = true

    suspend fun seed(container: AppContainer): String {
        val db = container.db
        val now = System.currentTimeMillis()
        var places = db.places().all()
        if (places.isEmpty()) {
            listOf(
                Place(name = "Kantor (Dev)", kind = PlaceKind.OFFICE, latitude = -6.2250, longitude = 106.8090, radiusM = 100,
                    colorArgb = 0xFF8B7CFF, iconKey = "office", createdAt = now, updatedAt = now),
                Place(name = "Rumah (Dev)", kind = PlaceKind.HOME, latitude = -6.2600, longitude = 106.7800, radiusM = 150,
                    colorArgb = 0xFF3DA9FC, iconKey = "home", createdAt = now, updatedAt = now),
                Place(name = "Warung (Dev)", kind = PlaceKind.FOOD, latitude = -6.2255, longitude = 106.8100, radiusM = 60,
                    colorArgb = 0xFFFBBF24, iconKey = "food", createdAt = now, updatedAt = now),
            ).forEach { db.places().insert(it) }
            places = db.places().all()
        }
        val office = places.firstOrNull { it.kind == PlaceKind.OFFICE } ?: places.first()
        val home = places.firstOrNull { it.kind == PlaceKind.HOME } ?: places.first()
        val food = places.firstOrNull { it.kind == PlaceKind.FOOD || it.kind == PlaceKind.BREAK } ?: office
        val rnd = Random(42)
        var n = 0
        fun add(p: Place, start: Long, end: Long) {
            n++
            pending += DailyActivity(placeId = p.id, title = p.name, kind = p.kind, date = start.dateKey(), startAt = start, endAt = end,
                source = ActivitySource.SIMULATED, createdAt = now, updatedAt = now)
        }
        pending.clear()
        for (i in 30 downTo 1) {
            val d = LocalDate.now().minusDays(i.toLong())
            val homeMorningEnd = d.atTime(LocalTime.of(6, 50).plusMinutes(rnd.nextLong(40))).toEpochMs()
            add(home, d.atStartOfDay().toEpochMs(), homeMorningEnd)
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) {
                val arrive = d.atTime(LocalTime.of(7, 45).plusMinutes(rnd.nextLong(50))).toEpochMs()
                val lunch = d.atTime(LocalTime.of(12, 0).plusMinutes(rnd.nextLong(15))).toEpochMs()
                val back = lunch + (35 + rnd.nextLong(25)) * 60_000
                val leave = d.atTime(LocalTime.of(16, 50).plusMinutes(rnd.nextLong(70))).toEpochMs()
                add(office, arrive, lunch - 3 * 60_000)
                add(food, lunch, back - 3 * 60_000)
                add(office, back, leave)
                add(home, leave + (35 + rnd.nextLong(30)) * 60_000, d.plusDays(1).atStartOfDay().toEpochMs())
            } else {
                add(home, homeMorningEnd + 3 * 3_600_000, d.plusDays(1).atStartOfDay().toEpochMs())
            }
        }
        pending.forEach { db.activities().insert(it) }
        return "$n aktivitas simulasi dibuat (30 hari)"
    }

    private val pending = mutableListOf<DailyActivity>()
}

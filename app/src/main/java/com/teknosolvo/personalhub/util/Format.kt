package com.teknosolvo.personalhub.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone

val LOCALE_ID: Locale = Locale.forLanguageTag("id-ID")
val DATE_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
val TIME_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val TIME_HMS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
val DATE_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", LOCALE_ID)
val DATE_MEDIUM: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", LOCALE_ID)
val DATE_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", LOCALE_ID)
val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_ID)
val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", LOCALE_ID)

fun zone(): ZoneId = ZoneId.systemDefault()

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone()).toLocalDateTime()
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone()).toLocalDate()
fun Long.dateKey(): String = toLocalDate().format(DATE_KEY)
fun Long.hm(): String = toLocalDateTime().format(TIME_HM)
fun Long.dateTime(): String = toLocalDateTime().format(DATE_TIME)
fun LocalDateTime.toEpochMs(): Long = atZone(zone()).toInstant().toEpochMilli()

/** "WIB", "WITA", "WIT" for Indonesian zones, otherwise the short zone name. */
fun zoneLabel(): String = when (zone().id) {
    "Asia/Jakarta", "Asia/Pontianak" -> "WIB"
    "Asia/Makassar" -> "WITA"
    "Asia/Jayapura" -> "WIT"
    else -> TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT, LOCALE_ID)
}

/** "01 Jam 28 Menit". */
fun durationLong(ms: Long): String {
    val totalMin = (ms / 60_000).coerceAtLeast(0)
    return "%02d Jam %02d Menit".format(totalMin / 60, totalMin % 60)
}

/** "1j 28m". */
fun durationShort(ms: Long): String {
    val totalMin = (ms / 60_000).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}j"
        else -> "${h}j ${m}m"
    }
}

fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - ms).coerceAtLeast(0) / 1000
    return when {
        diff < 60 -> "baru saja"
        diff < 3600 -> "${diff / 60} menit lalu"
        diff < 86_400 -> "${diff / 3600} jam lalu"
        else -> ms.dateTime()
    }
}

fun formatDistance(meters: Double?): String = when {
    meters == null -> "-"
    meters >= 1000 -> String.format(LOCALE_ID, "%.2f km", meters / 1000)
    else -> "${meters.toInt()} m"
}

fun monthName(month: Int): String =
    java.time.Month.of(month).getDisplayName(TextStyle.FULL, LOCALE_ID)

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(LOCALE_ID, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(LOCALE_ID, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

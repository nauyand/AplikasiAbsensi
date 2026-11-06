package com.teknosolvo.personalhub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ThemeMode {
    const val DARK = "DARK"
    const val LIGHT = "LIGHT"
    const val SYSTEM = "SYSTEM"
}

object Accent {
    const val VIOLET = "VIOLET"
    const val BLUE = "BLUE"
}

/** Non-sensitive app preferences, persisted in the `app_settings` key/value table. */
data class AppPrefs(
    val userName: String = "",
    val onboardingDone: Boolean = false,
    val themeMode: String = ThemeMode.DARK,
    val accent: String = Accent.VIOLET,
    // Tracker
    val trackingEnabled: Boolean = true,
    val dwellMinutes: Int = 3,
    val exitDelayMinutes: Int = 3,
    val gpsToleranceM: Int = 30,
    val maxAccuracyM: Int = 200,
    val mergeGapMinutes: Int = 10,
    /** 0 = off. Minimum 15 (WorkManager limit). */
    val periodicCheckMinutes: Int = 30,
    // Notifications
    val notifyOffice: Boolean = true,
    val notifyHome: Boolean = true,
    val notifyOtherPlaces: Boolean = false,
    val notifyIssues: Boolean = true,
    // Backup
    val lastBackupAt: Long = 0,
)

class SettingsRepository(private val dao: SettingsDao, private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val _prefs = MutableStateFlow(runBlocking(Dispatchers.IO) { decode(dao.all()) })
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    val current: AppPrefs get() = _prefs.value

    fun update(transform: (AppPrefs) -> AppPrefs) {
        scope.launch(Dispatchers.IO) { updateNow(transform) }
    }

    suspend fun updateNow(transform: (AppPrefs) -> AppPrefs) = mutex.withLock {
        val next = transform(_prefs.value)
        _prefs.value = next
        encode(next).forEach { (k, v) -> dao.put(AppSettingEntry(k, v)) }
    }

    /** Re-read from the database (used by background workers in a fresh process). */
    suspend fun reload() = mutex.withLock { _prefs.value = decode(dao.all()) }

    companion object {
        fun encode(p: AppPrefs): Map<String, String> = mapOf(
            "userName" to p.userName,
            "onboardingDone" to p.onboardingDone.toString(),
            "themeMode" to p.themeMode,
            "accent" to p.accent,
            "trackingEnabled" to p.trackingEnabled.toString(),
            "dwellMinutes" to p.dwellMinutes.toString(),
            "exitDelayMinutes" to p.exitDelayMinutes.toString(),
            "gpsToleranceM" to p.gpsToleranceM.toString(),
            "maxAccuracyM" to p.maxAccuracyM.toString(),
            "mergeGapMinutes" to p.mergeGapMinutes.toString(),
            "periodicCheckMinutes" to p.periodicCheckMinutes.toString(),
            "notifyOffice" to p.notifyOffice.toString(),
            "notifyHome" to p.notifyHome.toString(),
            "notifyOtherPlaces" to p.notifyOtherPlaces.toString(),
            "notifyIssues" to p.notifyIssues.toString(),
            "lastBackupAt" to p.lastBackupAt.toString(),
        )

        fun decode(entries: List<AppSettingEntry>): AppPrefs {
            val m = entries.associate { it.key to it.value }
            val d = AppPrefs()
            fun b(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
            fun i(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
            return AppPrefs(
                userName = m["userName"] ?: d.userName,
                onboardingDone = b("onboardingDone", d.onboardingDone),
                themeMode = m["themeMode"] ?: d.themeMode,
                accent = m["accent"] ?: d.accent,
                trackingEnabled = b("trackingEnabled", d.trackingEnabled),
                dwellMinutes = i("dwellMinutes", d.dwellMinutes),
                exitDelayMinutes = i("exitDelayMinutes", d.exitDelayMinutes),
                gpsToleranceM = i("gpsToleranceM", d.gpsToleranceM),
                maxAccuracyM = i("maxAccuracyM", d.maxAccuracyM),
                mergeGapMinutes = i("mergeGapMinutes", d.mergeGapMinutes),
                periodicCheckMinutes = i("periodicCheckMinutes", d.periodicCheckMinutes),
                notifyOffice = b("notifyOffice", d.notifyOffice),
                notifyHome = b("notifyHome", d.notifyHome),
                notifyOtherPlaces = b("notifyOtherPlaces", d.notifyOtherPlaces),
                notifyIssues = b("notifyIssues", d.notifyIssues),
                lastBackupAt = m["lastBackupAt"]?.toLongOrNull() ?: d.lastBackupAt,
            )
        }
    }
}

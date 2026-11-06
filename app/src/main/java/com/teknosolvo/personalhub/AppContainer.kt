package com.teknosolvo.personalhub

import android.content.Context
import com.teknosolvo.personalhub.backup.BackupManager
import com.teknosolvo.personalhub.data.HubDatabase
import com.teknosolvo.personalhub.data.SettingsRepository
import com.teknosolvo.personalhub.notify.Notifier
import com.teknosolvo.personalhub.tracking.GeofenceManager
import com.teknosolvo.personalhub.tracking.LocationProvider
import com.teknosolvo.personalhub.tracking.TrackerRepository
import com.teknosolvo.personalhub.tracking.TrackingScheduler
import com.teknosolvo.personalhub.vault.AndroidKeystoreKeys
import com.teknosolvo.personalhub.vault.BiometricKeys
import com.teknosolvo.personalhub.vault.KeyWrapper
import com.teknosolvo.personalhub.vault.SecureClipboard
import com.teknosolvo.personalhub.vault.VaultRepository
import com.teknosolvo.personalhub.vault.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Manual dependency container (no DI framework needed for a single-user app). */
class AppContainer(
    val context: Context,
    /** Test hook: replaces the Android Keystore (unavailable on the JVM). */
    keyOverride: Pair<KeyWrapper, BiometricKeys>? = null,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: HubDatabase = HubDatabase.build(context)
    val settings = SettingsRepository(db.settings(), scope)
    val notifier = Notifier(context, db.notifications())
    val locations = LocationProvider(context)
    val geofences = GeofenceManager(context, locations)

    val tracker = TrackerRepository(
        db = db,
        settings = settings,
        notifier = notifier,
        scheduler = { delay -> TrackingScheduler.confirmSoon(context, delay) },
        geofenceSync = { requestGeofenceSync() },
    )

    val keystore by lazy { AndroidKeystoreKeys() }
    val session = VaultSession(scope)
    val vault by lazy {
        VaultRepository(db, session, keyOverride?.first ?: keystore, keyOverride?.second ?: keystore)
    }
    val clipboard = SecureClipboard(context, scope)
    val backup by lazy { BackupManager(db, vault) }

    private val _lastSync = MutableStateFlow<Pair<Long, GeofenceManager.SyncResult>?>(null)
    val lastSync: StateFlow<Pair<Long, GeofenceManager.SyncResult>?> = _lastSync.asStateFlow()

    fun requestGeofenceSync() {
        scope.launch { syncGeofencesNow() }
    }

    suspend fun syncGeofencesNow(): GeofenceManager.SyncResult {
        val p = settings.current
        val r = geofences.sync(tracker.allPlaces(), p.trackingEnabled, p.gpsToleranceM)
        _lastSync.value = System.currentTimeMillis() to r
        return r
    }

    fun applyTrackingSchedule() {
        val p = settings.current
        TrackingScheduler.schedulePeriodic(context, if (p.trackingEnabled) p.periodicCheckMinutes else 0)
    }

    fun databaseBytes(): Long = listOf("", "-wal", "-shm").sumOf {
        context.getDatabasePath(HubDatabase.NAME + it).takeIf { f -> f.exists() }?.length() ?: 0L
    }

    /** Factory reset: every table, Keystore keys, geofences and scheduled work. */
    suspend fun wipeEverything() {
        session.lock()
        db.clearAllTables()
        runCatching { keystore.deleteAll() }
        settings.reload()
        syncGeofencesNow()
        applyTrackingSchedule()
    }
}

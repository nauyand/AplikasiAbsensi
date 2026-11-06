package com.teknosolvo.personalhub.tracking

import androidx.room.withTransaction
import com.teknosolvo.personalhub.data.ActivityKind
import com.teknosolvo.personalhub.data.ActivitySource
import com.teknosolvo.personalhub.data.AppPrefs
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.DetectionStatus
import com.teknosolvo.personalhub.data.EventSource
import com.teknosolvo.personalhub.data.EventType
import com.teknosolvo.personalhub.data.HubDatabase
import com.teknosolvo.personalhub.data.LocationEvent
import com.teknosolvo.personalhub.data.NotificationType
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.data.SettingsRepository
import com.teknosolvo.personalhub.data.TrackerStateEntity
import com.teknosolvo.personalhub.notify.Notifier
import com.teknosolvo.personalhub.util.dateKey
import com.teknosolvo.personalhub.util.durationLong
import com.teknosolvo.personalhub.util.hm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Schedules the delayed dwell / exit confirmation check. */
fun interface ConfirmScheduler {
    fun schedule(delayMs: Long)
}

/** Called whenever the set of places changes so geofences can be re-registered. */
fun interface GeofenceSyncTrigger {
    fun requestSync()
}

data class ProcessOutcome(
    val opened: DailyActivity? = null,
    val closed: DailyActivity? = null,
    val rejected: String? = null,
    val pending: Boolean = false,
)

fun AppPrefs.engineConfig() = EngineConfig(
    dwellMs = dwellMinutes * 60_000L,
    exitDelayMs = exitDelayMinutes * 60_000L,
    toleranceM = gpsToleranceM,
    maxAccuracyM = maxAccuracyM,
    mergeGapMs = mergeGapMinutes * 60_000L,
)

fun Place.toZone() = Zone(id, name, latitude, longitude, radiusM)

fun TrackerStateEntity.toEngine() = EngineState(
    currentPlaceId = currentPlaceId,
    currentActivityId = currentActivityId,
    pendingPlaceId = pendingPlaceId,
    pendingSince = pendingSince,
    pendingExitSince = pendingExitSince,
    lastFix = if (lastLat != null && lastLng != null && lastFixAt != null)
        Fix(lastLat, lastLng, lastAccuracy ?: 0f, lastFixAt) else null,
)

class TrackerRepository(
    private val db: HubDatabase,
    private val settings: SettingsRepository,
    private val notifier: Notifier?,
    private val scheduler: ConfirmScheduler,
    private val geofenceSync: GeofenceSyncTrigger,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val places = db.places()
    private val activities = db.activities()
    private val events = db.events()
    private val stateDao = db.trackerState()

    val placesFlow: Flow<List<Place>> = places.observeAll()
    val stateFlow: Flow<TrackerStateEntity> = stateDao.observe().map { it ?: TrackerStateEntity() }
    fun recentEvents(limit: Int = 50) = events.observeRecent(limit)
    fun latestActivities(limit: Int = 10) = activities.observeLatest(limit)
    fun overlapping(from: Long, to: Long) = activities.observeOverlapping(from, to)
    suspend fun overlappingNow(from: Long, to: Long) = activities.overlapping(from, to)
    fun activeDates(from: String, to: String) = activities.observeActiveDates(from, to)
    fun observeActivity(id: Long) = activities.observe(id)
    fun observePlace(id: Long) = places.observe(id)

    // ---------------------------------------------------------------- detection

    /**
     * Feed one location fix into the presence engine and apply the result.
     * [hintPlaceIds]/[hintType] describe the geofence transition that woke us, if any (used only
     * for the audit log: an ENTER for a place we are already in is logged as a duplicate).
     */
    suspend fun processFix(
        fix: Fix,
        source: String,
        hintType: String? = null,
        hintPlaceIds: List<Long> = emptyList(),
        configOverride: EngineConfig? = null,
    ): ProcessOutcome = mutex.withLock {
        val prefs = settings.current
        if (!prefs.trackingEnabled && source != EventSource.SIMULATION) return@withLock ProcessOutcome(rejected = "DISABLED")
        val cfg = configOverride ?: prefs.engineConfig()
        val enabled = places.enabled()
        val zones = enabled.map { it.toZone() }
        val byId = enabled.associateBy { it.id }

        var outcome = ProcessOutcome()
        val newState = db.withTransaction {
            val stored = reconcile(stateDao.get() ?: TrackerStateEntity())
            val result = PresenceEngine.process(stored.toEngine(), zones, fix, cfg)
            var engineState = result.state

            if (result.rejected != null) {
                if (source == EventSource.GEOFENCE || source == EventSource.SIMULATION) {
                    val pid = hintPlaceIds.firstOrNull()
                    events.insert(event(pid, byId[pid]?.name, hintType ?: EventType.ENTER, fix, source, result.rejected))
                }
                outcome = outcome.copy(rejected = result.rejected)
            } else if (result.transitions.isEmpty() && hintType != null) {
                // The OS reported a transition the engine considers a no-op (GPS bounce).
                hintPlaceIds.forEach { pid ->
                    events.insert(event(pid, byId[pid]?.name, hintType, fix, source, DetectionStatus.DUPLICATE))
                }
            }

            for (t in result.transitions) {
                val place = byId[t.placeId] ?: places.byId(t.placeId)
                val name = place?.name ?: "Lokasi terhapus"
                when {
                    t.type == EventType.ENTER && t.status == DetectionStatus.CONFIRMED && place != null -> {
                        val (activity, merged) = openVisit(place, t.at, source, cfg)
                        engineState = engineState.copy(currentActivityId = activity.id)
                        events.insert(
                            event(place.id, name, EventType.ENTER, fix, source,
                                if (merged) DetectionStatus.MERGED else DetectionStatus.CONFIRMED, activity.id, t.at)
                        )
                        outcome = outcome.copy(opened = activity)
                    }
                    t.type == EventType.EXIT && t.status == DetectionStatus.CONFIRMED -> {
                        val closed = stored.currentActivityId?.let { closeVisit(it, t.at) }
                        events.insert(event(t.placeId, name, EventType.EXIT, fix, source, DetectionStatus.CONFIRMED, closed?.id, t.at))
                        outcome = outcome.copy(closed = closed)
                    }
                    else -> events.insert(event(t.placeId, name, t.type, fix, source, t.status, null, t.at))
                }
            }

            val now = clock()
            val entity = stored.copy(
                currentActivityId = engineState.currentActivityId,
                currentPlaceId = engineState.currentPlaceId,
                pendingPlaceId = engineState.pendingPlaceId,
                pendingSince = engineState.pendingSince,
                pendingExitSince = engineState.pendingExitSince,
                lastLat = engineState.lastFix?.lat,
                lastLng = engineState.lastFix?.lng,
                lastAccuracy = engineState.lastFix?.accuracyM,
                lastFixAt = engineState.lastFix?.time,
                lastCheckAt = now,
            )
            stateDao.put(entity)
            entity
        }

        val delay = PresenceEngine.nextCheckDelay(newState.toEngine(), cfg, clock())
        if (delay != null && source != EventSource.SIMULATION) scheduler.schedule(delay)
        outcome = outcome.copy(pending = delay != null)

        outcome.opened?.let { notifyVisit(it, entered = true, prefs) }
        outcome.closed?.let { notifyVisit(it, entered = false, prefs) }
        outcome
    }

    private suspend fun openVisit(place: Place, at: Long, source: String, cfg: EngineConfig): Pair<DailyActivity, Boolean> {
        val now = clock()
        val last = activities.lastClosedAt(place.id)
        if (last != null && PresenceEngine.shouldMerge(last.endAt, at, cfg)) {
            // Continue the previous visit only if nothing else happened in between.
            val between = activities.overlapping(last.endAt!!, at).filter { it.id != last.id }
            if (between.isEmpty()) {
                val reopened = last.copy(endAt = null, updatedAt = now)
                activities.update(reopened)
                return reopened to true
            }
        }
        val activity = DailyActivity(
            placeId = place.id,
            title = place.name,
            kind = place.kind,
            date = at.dateKey(),
            startAt = at,
            endAt = null,
            source = if (source == EventSource.SIMULATION) ActivitySource.SIMULATED else ActivitySource.AUTO,
            createdAt = now,
            updatedAt = now,
        )
        return activity.copy(id = activities.insert(activity)) to false
    }

    private suspend fun closeVisit(activityId: Long, at: Long): DailyActivity? {
        val a = activities.byId(activityId) ?: return null
        if (a.endAt != null) return a
        val closed = a.copy(endAt = at.coerceAtLeast(a.startAt), updatedAt = clock())
        activities.update(closed)
        return closed
    }

    /** Keep engine state consistent with user edits (deleted / manually closed visits). */
    private suspend fun reconcile(s: TrackerStateEntity): TrackerStateEntity {
        val id = s.currentActivityId ?: return if (s.currentPlaceId != null) s.copy(currentPlaceId = null, pendingExitSince = null) else s
        val a = activities.byId(id)
        return if (a == null || a.endAt != null) s.copy(currentActivityId = null, currentPlaceId = null, pendingExitSince = null) else s
    }

    private fun event(
        placeId: Long?, name: String?, type: String, fix: Fix, source: String, status: String,
        activityId: Long? = null, at: Long = fix.time,
    ) = LocationEvent(
        placeId = placeId, placeName = name ?: "-", type = type, timestamp = at,
        latitude = fix.lat, longitude = fix.lng, accuracyM = fix.accuracyM,
        source = source, status = status, activityId = activityId,
    )

    private suspend fun notifyVisit(a: DailyActivity, entered: Boolean, prefs: AppPrefs) {
        val n = notifier ?: return
        val wanted = when (a.kind) {
            PlaceKind.OFFICE -> prefs.notifyOffice
            PlaceKind.HOME -> prefs.notifyHome
            else -> prefs.notifyOtherPlaces
        }
        val sim = if (a.source == ActivitySource.SIMULATED) " (simulasi)" else ""
        if (entered) {
            n.post(NotificationType.ENTER, "Tiba di ${a.title}$sim", "Masuk pukul ${a.startAt.hm()}", system = wanted)
        } else {
            val dur = (a.endAt ?: clock()) - a.startAt
            n.post(NotificationType.EXIT, "Meninggalkan ${a.title}$sim",
                "Keluar pukul ${a.endAt?.hm()} · durasi ${durationLong(dur)}", system = wanted)
        }
    }

    /** Record a health issue once (deduplicated) and notify. */
    suspend fun reportHealth(issue: String?) {
        val prev = mutex.withLock {
            val s = stateDao.get() ?: TrackerStateEntity()
            if (s.lastIssue != issue) stateDao.put(s.copy(lastIssue = issue))
            s.lastIssue
        }
        if (issue == null || issue == prev) return
        val prefs = settings.current
        val type = if (issue == TrackerHealth.ISSUE_GPS_OFF) NotificationType.GPS else NotificationType.PERMISSION
        val title = when (issue) {
            TrackerHealth.ISSUE_GPS_OFF -> "GPS tidak aktif"
            TrackerHealth.ISSUE_NO_PERMISSION -> "Izin lokasi dicabut"
            TrackerHealth.ISSUE_NO_BACKGROUND -> "Lokasi latar belakang tidak diizinkan"
            else -> "Masalah deteksi lokasi"
        }
        notifier?.post(type, title, TrackerHealth.describe(issue), system = prefs.notifyIssues)
    }

    suspend fun reportProblem(message: String) {
        notifier?.post(NotificationType.SYSTEM, "Masalah deteksi lokasi", message, system = settings.current.notifyIssues)
    }

    // ---------------------------------------------------------------- places

    suspend fun savePlace(place: Place): Long {
        val now = clock()
        val id = if (place.id == 0L) places.insert(place.copy(createdAt = now, updatedAt = now))
        else { places.update(place.copy(updatedAt = now)); place.id }
        geofenceSync.requestSync()
        return id
    }

    suspend fun setPlaceEnabled(place: Place, enabled: Boolean) {
        places.update(place.copy(enabled = enabled, updatedAt = clock()))
        if (!enabled) closeIfCurrent(place.id)
        geofenceSync.requestSync()
    }

    suspend fun deletePlace(place: Place) {
        closeIfCurrent(place.id)
        places.delete(place)
        geofenceSync.requestSync()
    }

    suspend fun allPlaces() = places.all()

    private suspend fun closeIfCurrent(placeId: Long) = mutex.withLock {
        val s = stateDao.get() ?: return@withLock
        if (s.currentPlaceId == placeId) {
            s.currentActivityId?.let { closeVisit(it, clock()) }
            stateDao.put(s.copy(currentActivityId = null, currentPlaceId = null, pendingExitSince = null))
        } else if (s.pendingPlaceId == placeId) {
            stateDao.put(s.copy(pendingPlaceId = null, pendingSince = null))
        }
    }

    // ---------------------------------------------------------------- manual corrections

    /** Add a missed visit or a personal activity. Marked as manual. */
    suspend fun addManual(
        placeId: Long?, title: String, kind: String, startAt: Long, endAt: Long?, note: String?,
    ): Long {
        require(endAt == null || endAt >= startAt) { "Waktu keluar harus setelah waktu masuk" }
        val now = clock()
        return activities.insert(
            DailyActivity(
                placeId = placeId, title = title.trim(), kind = kind, date = startAt.dateKey(),
                startAt = startAt, endAt = endAt, source = ActivitySource.MANUAL,
                note = note?.takeIf { it.isNotBlank() }, createdAt = now, updatedAt = now,
            )
        )
    }

    /** Correct an activity. Automatic records become flagged as edited. */
    suspend fun updateActivity(a: DailyActivity) = mutex.withLock {
        require(a.endAt == null || a.endAt >= a.startAt) { "Waktu keluar harus setelah waktu masuk" }
        val old = activities.byId(a.id) ?: return@withLock
        val timesChanged = old.startAt != a.startAt || old.endAt != a.endAt || old.placeId != a.placeId || old.title != a.title
        activities.update(
            a.copy(
                date = a.startAt.dateKey(),
                edited = a.edited || (timesChanged && a.source != ActivitySource.MANUAL),
                updatedAt = clock(),
            )
        )
        val s = stateDao.get()
        if (s != null && s.currentActivityId == a.id && a.endAt != null) {
            stateDao.put(s.copy(currentActivityId = null, currentPlaceId = null, pendingExitSince = null))
        }
    }

    suspend fun updateNote(id: Long, note: String?) {
        val a = activities.byId(id) ?: return
        activities.update(a.copy(note = note?.takeIf { it.isNotBlank() }, updatedAt = clock()))
    }

    suspend fun deleteActivity(a: DailyActivity) = mutex.withLock {
        activities.delete(a)
        val s = stateDao.get()
        if (s != null && s.currentActivityId == a.id) {
            stateDao.put(s.copy(currentActivityId = null, currentPlaceId = null, pendingExitSince = null))
        }
    }

    /** Remove everything created by the simulator (keeps real data intact). */
    suspend fun clearSimulated() = mutex.withLock {
        db.withTransaction {
            val sims = activities.all().filter { it.source == ActivitySource.SIMULATED }
            sims.forEach { activities.delete(it) }
            val s = stateDao.get()
            if (s != null && sims.any { it.id == s.currentActivityId }) {
                stateDao.put(s.copy(currentActivityId = null, currentPlaceId = null, pendingExitSince = null,
                    pendingPlaceId = null, pendingSince = null))
            }
        }
    }

    /** Reset pending detection state (e.g. after changing settings). */
    suspend fun resetPending() = mutex.withLock {
        val s = stateDao.get() ?: return@withLock
        stateDao.put(s.copy(pendingPlaceId = null, pendingSince = null, pendingExitSince = null))
    }

    companion object {
        val PERSONAL = ActivityKind.PERSONAL
    }
}

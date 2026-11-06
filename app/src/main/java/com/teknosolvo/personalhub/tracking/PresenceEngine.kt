package com.teknosolvo.personalhub.tracking

import com.teknosolvo.personalhub.data.DetectionStatus
import com.teknosolvo.personalhub.data.EventType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A geofenced place as seen by the engine. */
data class Zone(
    val id: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
)

/** One location fix. */
data class Fix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val time: Long,
    val mock: Boolean = false,
)

data class EngineConfig(
    /** How long a fix must stay inside a zone before the visit is confirmed. */
    val dwellMs: Long = 3 * 60_000L,
    /** How long a fix must stay outside before the exit is confirmed. */
    val exitDelayMs: Long = 3 * 60_000L,
    /** Hysteresis band: you leave a zone only beyond radius + tolerance. */
    val toleranceM: Int = 30,
    /** Fixes with a worse accuracy are ignored. */
    val maxAccuracyM: Int = 200,
    /** Re-entering the same place within this gap continues the previous visit. */
    val mergeGapMs: Long = 10 * 60_000L,
    /** Faster apparent movement than this (m/s) over > [jumpMinDistanceM] is treated as a glitch. */
    val maxSpeedMps: Double = 75.0,
    val jumpMinDistanceM: Double = 1_000.0,
)

/** Persistent engine state. */
data class EngineState(
    val currentPlaceId: Long? = null,
    val currentActivityId: Long? = null,
    val pendingPlaceId: Long? = null,
    val pendingSince: Long? = null,
    val pendingExitSince: Long? = null,
    val lastFix: Fix? = null,
)

/** A transition the repository must apply / log. */
data class Transition(
    val type: String,
    val placeId: Long,
    /** Effective time of the transition (the first fix that crossed the boundary). */
    val at: Long,
    val status: String,
)

data class EngineResult(
    val state: EngineState,
    val transitions: List<Transition>,
    /** Non-null when the fix itself was rejected (see [DetectionStatus]). */
    val rejected: String? = null,
) {
    /** Opened visit, if any (status CONFIRMED + ENTER). */
    val opened: Transition? get() = transitions.firstOrNull { it.type == EventType.ENTER && it.status == DetectionStatus.CONFIRMED }
    val closed: Transition? get() = transitions.firstOrNull { it.type == EventType.EXIT && it.status == DetectionStatus.CONFIRMED }
}

/**
 * Deterministic presence state machine.
 *
 * Geofence callbacks, periodic checks and confirmation re-checks all feed fixes in here; the
 * engine alone decides when a visit starts and ends, so an unstable GPS signal bouncing around
 * the border cannot create repeated enter/exit records:
 *
 * - **Accuracy filter**: fixes worse than [EngineConfig.maxAccuracyM] are ignored.
 * - **Mock filter**: fixes from mock-location apps are ignored.
 * - **Jump validation**: an implausible jump is ignored once; a second consistent fix is accepted.
 * - **Dwell time**: an entry is confirmed only if still inside after [EngineConfig.dwellMs];
 *   the visit start is back-dated to the first fix inside.
 * - **Hysteresis**: exit only beyond radius + tolerance, and only if still outside after
 *   [EngineConfig.exitDelayMs]; the exit time is back-dated to the first fix outside.
 */
object PresenceEngine {

    fun process(state: EngineState, zones: List<Zone>, fix: Fix, cfg: EngineConfig): EngineResult {
        val last = state.lastFix
        if (last != null && fix.time < last.time) {
            return EngineResult(state, emptyList(), DetectionStatus.DUPLICATE)
        }
        if (fix.mock) return EngineResult(state, emptyList(), REJECT_MOCK)
        if (fix.accuracyM > cfg.maxAccuracyM) {
            return EngineResult(state, emptyList(), DetectionStatus.LOW_ACCURACY)
        }
        if (last != null && isImplausibleJump(last, fix, cfg)) {
            // Remember it so that a confirming second fix is accepted.
            return EngineResult(state.copy(lastFix = fix), emptyList(), DetectionStatus.IMPLAUSIBLE_JUMP)
        }

        var s = state.copy(lastFix = fix)
        val out = mutableListOf<Transition>()
        val byId = zones.associateBy { it.id }

        // 1. Currently inside a place: check whether we left it.
        val currentId = s.currentPlaceId
        if (currentId != null) {
            val zone = byId[currentId]
            val d = zone?.let { distanceM(it.lat, it.lng, fix.lat, fix.lng) }
            val stillInside = zone != null && d!! <= zone.radiusM + cfg.toleranceM
            if (stillInside) {
                if (s.pendingExitSince != null) {
                    out += Transition(EventType.EXIT, currentId, fix.time, DetectionStatus.CANCELLED)
                    s = s.copy(pendingExitSince = null)
                }
                return EngineResult(s.copy(pendingPlaceId = null, pendingSince = null), out)
            }
            // Place deleted/disabled → close immediately at this fix.
            val exitSince = if (zone == null) fix.time else (s.pendingExitSince ?: fix.time)
            if (s.pendingExitSince == null && zone != null) {
                out += Transition(EventType.EXIT, currentId, fix.time, DetectionStatus.PENDING)
                s = s.copy(pendingExitSince = fix.time)
            }
            if (zone == null || fix.time - exitSince >= cfg.exitDelayMs) {
                out += Transition(EventType.EXIT, currentId, exitSince, DetectionStatus.CONFIRMED)
                s = s.copy(currentPlaceId = null, currentActivityId = null, pendingExitSince = null)
            } else {
                return EngineResult(s, out)
            }
        }

        // 2. Not in a place: the most specific zone containing the fix (smallest radius, then nearest),
        //    e.g. a canteen registered inside a large office campus wins over the campus.
        val candidate = zones
            .map { it to distanceM(it.lat, it.lng, fix.lat, fix.lng) }
            .filter { (z, d) -> d <= z.radiusM }
            .minWithOrNull(compareBy<Pair<Zone, Double>>({ it.first.radiusM }, { it.second }))
            ?.first

        if (candidate == null) {
            if (s.pendingPlaceId != null) {
                out += Transition(EventType.ENTER, s.pendingPlaceId!!, fix.time, DetectionStatus.CANCELLED)
                s = s.copy(pendingPlaceId = null, pendingSince = null)
            }
            return EngineResult(s, out)
        }

        if (s.pendingPlaceId != candidate.id) {
            if (s.pendingPlaceId != null) {
                out += Transition(EventType.ENTER, s.pendingPlaceId!!, fix.time, DetectionStatus.CANCELLED)
            }
            s = s.copy(pendingPlaceId = candidate.id, pendingSince = fix.time)
            if (cfg.dwellMs > 0) {
                out += Transition(EventType.ENTER, candidate.id, fix.time, DetectionStatus.PENDING)
                return EngineResult(s, out)
            }
        }

        val since = s.pendingSince ?: fix.time
        if (fix.time - since >= cfg.dwellMs) {
            out += Transition(EventType.ENTER, candidate.id, since, DetectionStatus.CONFIRMED)
            s = s.copy(currentPlaceId = candidate.id, pendingPlaceId = null, pendingSince = null)
        }
        return EngineResult(s, out)
    }

    /** Whether a new visit starting at [openAt] should continue a visit that ended at [lastEnd]. */
    fun shouldMerge(lastEnd: Long?, openAt: Long, cfg: EngineConfig): Boolean =
        lastEnd != null && openAt >= lastEnd && openAt - lastEnd <= cfg.mergeGapMs

    /** When the next confirmation check should run, or null if nothing is pending. */
    fun nextCheckDelay(state: EngineState, cfg: EngineConfig, now: Long): Long? {
        val pendingEnter = state.pendingSince?.let { it + cfg.dwellMs - now }
        val pendingExit = state.pendingExitSince?.let { it + cfg.exitDelayMs - now }
        val d = listOfNotNull(pendingEnter, pendingExit).minOrNull() ?: return null
        return d.coerceAtLeast(15_000L)
    }

    fun isImplausibleJump(a: Fix, b: Fix, cfg: EngineConfig): Boolean {
        val dist = distanceM(a.lat, a.lng, b.lat, b.lng)
        if (dist < cfg.jumpMinDistanceM) return false
        val seconds = (b.time - a.time).coerceAtLeast(1L) / 1000.0
        return dist / seconds > cfg.maxSpeedMps
    }

    const val REJECT_MOCK = "MOCK_LOCATION"

    /** Haversine distance in meters. */
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_008.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

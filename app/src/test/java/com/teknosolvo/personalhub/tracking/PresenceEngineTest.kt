package com.teknosolvo.personalhub.tracking

import com.teknosolvo.personalhub.data.DetectionStatus
import com.teknosolvo.personalhub.data.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PresenceEngineTest {
    private val office = Zone(1, "Kantor", -6.2250, 106.8090, 100)
    private val home = Zone(2, "Rumah", -6.2600, 106.7800, 150)
    private val zones = listOf(office, home)
    private val cfg = EngineConfig(dwellMs = 3 * MIN, exitDelayMs = 3 * MIN, toleranceM = 30, maxAccuracyM = 200, mergeGapMs = 10 * MIN)
    private val t0 = 1_780_000_000_000L

    /** Fix [meters] north of the zone center. */
    private fun at(z: Zone, meters: Double, t: Long, acc: Float = 10f) = Fix(z.lat + meters / 111_320.0, z.lng, acc, t)

    private fun run(fixes: List<Fix>, start: EngineState = EngineState(), c: EngineConfig = cfg): Pair<EngineState, List<EngineResult>> {
        var s = start
        val results = fixes.map { f ->
            PresenceEngine.process(s, zones, f, c).also { r ->
                s = r.state
                // The repository assigns an activity id when a visit opens.
                if (r.opened != null) s = s.copy(currentActivityId = 99)
            }
        }
        return s to results
    }

    @Test fun `entry is confirmed only after dwell time and back-dated to the first fix`() {
        val (s, r) = run(listOf(at(office, 0.0, t0), at(office, 10.0, t0 + 1 * MIN), at(office, 5.0, t0 + 3 * MIN)))
        assertNull(r[0].opened)
        assertEquals(DetectionStatus.PENDING, r[0].transitions.single().status)
        assertNull(r[1].opened)
        val opened = r[2].opened
        assertNotNull(opened)
        assertEquals(office.id, opened!!.placeId)
        assertEquals("start time is the first fix inside", t0, opened.at)
        assertEquals(office.id, s.currentPlaceId)
    }

    @Test fun `walking past a place does not create a visit`() {
        val (s, r) = run(listOf(at(office, 50.0, t0), at(office, 400.0, t0 + 2 * MIN), at(office, 800.0, t0 + 4 * MIN)))
        assertTrue(r.none { it.opened != null })
        assertEquals(DetectionStatus.CANCELLED, r[1].transitions.single().status)
        assertNull(s.currentPlaceId)
        assertNull(s.pendingPlaceId)
    }

    @Test fun `repeated fixes inside never duplicate the entry`() {
        val fixes = (0..60).map { at(office, (it % 7) * 8.0, t0 + it * MIN) }
        val (_, r) = run(fixes)
        assertEquals(1, r.count { it.opened != null })
    }

    @Test fun `gps jitter around the border is absorbed by hysteresis`() {
        val enter = listOf(at(office, 0.0, t0), at(office, 0.0, t0 + 3 * MIN))
        // Bounce between 90 m and 125 m (radius 100 + tolerance 30 = 130) for an hour.
        val bounce = (1..60).map { at(office, if (it % 2 == 0) 90.0 else 125.0, t0 + (3 + it) * MIN) }
        val (s, r) = run(enter + bounce)
        assertEquals(1, r.count { it.opened != null })
        assertEquals(0, r.count { it.closed != null })
        assertEquals(office.id, s.currentPlaceId)
    }

    @Test fun `random noisy trace at the edge yields at most one visit`() {
        val rnd = Random(1)
        var naiveEntries = 0
        var prevInside = false
        val fixes = (0 until 240).map { i ->
            val d = office.radiusM + (rnd.nextDouble() - 0.5) * 60 // ±30 m around the border
            val inside = d <= office.radiusM
            if (inside && !prevInside) naiveEntries++
            prevInside = inside
            at(office, d, t0 + i * MIN, acc = 20f)
        }
        val (_, r) = run(fixes)
        assertTrue("naive approach would produce many entries", naiveEntries > 10)
        assertTrue(r.count { it.opened != null } <= 1)
        assertTrue(r.count { it.closed != null } == 0)
    }

    @Test fun `exit is confirmed after the exit delay and back-dated`() {
        val enter = listOf(at(office, 0.0, t0), at(office, 0.0, t0 + 3 * MIN))
        val leave = listOf(at(office, 300.0, t0 + 60 * MIN), at(office, 900.0, t0 + 62 * MIN), at(office, 1500.0, t0 + 64 * MIN))
        val (s, r) = run(enter + leave)
        val exitPending = r[2].transitions.single()
        assertEquals(EventType.EXIT, exitPending.type)
        assertEquals(DetectionStatus.PENDING, exitPending.status)
        assertNull(r[3].closed)
        val closed = r[4].closed
        assertNotNull(closed)
        assertEquals(t0 + 60 * MIN, closed!!.at)
        assertNull(s.currentPlaceId)
    }

    @Test fun `short excursion outside is cancelled`() {
        val enter = listOf(at(office, 0.0, t0), at(office, 0.0, t0 + 3 * MIN))
        val excursion = listOf(at(office, 300.0, t0 + 30 * MIN), at(office, 20.0, t0 + 31 * MIN))
        val (s, r) = run(enter + excursion)
        assertEquals(DetectionStatus.CANCELLED, r[3].transitions.single().status)
        assertEquals(office.id, s.currentPlaceId)
        assertNull(s.pendingExitSince)
    }

    @Test fun `moving from office to home closes one and opens the other`() {
        val fixes = listOf(
            at(office, 0.0, t0), at(office, 0.0, t0 + 3 * MIN),
            at(home, 0.0, t0 + 60 * MIN), at(home, 0.0, t0 + 63 * MIN), at(home, 0.0, t0 + 66 * MIN),
        )
        val (s, r) = run(fixes)
        assertEquals(office.id, r[3].closed?.placeId)
        assertEquals(t0 + 60 * MIN, r[3].closed?.at)
        // New zone needs its own dwell.
        val opened = r.mapNotNull { it.opened }.last()
        assertEquals(home.id, opened.placeId)
        assertEquals(home.id, s.currentPlaceId)
    }

    @Test fun `low accuracy and mock fixes are ignored`() {
        val r1 = PresenceEngine.process(EngineState(), zones, at(office, 0.0, t0, acc = 500f), cfg)
        assertEquals(DetectionStatus.LOW_ACCURACY, r1.rejected)
        assertNull(r1.state.lastFix)
        val r2 = PresenceEngine.process(EngineState(), zones, at(office, 0.0, t0).copy(mock = true), cfg)
        assertEquals(PresenceEngine.REJECT_MOCK, r2.rejected)
    }

    @Test fun `implausible jump is ignored once then accepted when confirmed`() {
        val (s1, _) = run(listOf(at(office, 0.0, t0), at(office, 0.0, t0 + 3 * MIN)))
        // 50 km away 10 seconds later.
        val jump = at(office, 50_000.0, t0 + 3 * MIN + 10_000)
        val r = PresenceEngine.process(s1, zones, jump, cfg)
        assertEquals(DetectionStatus.IMPLAUSIBLE_JUMP, r.rejected)
        assertEquals(office.id, r.state.currentPlaceId)
        // A consistent second fix near the jump target is accepted (starts the exit).
        val r2 = PresenceEngine.process(r.state, zones, at(office, 50_010.0, t0 + 4 * MIN), cfg)
        assertNull(r2.rejected)
        assertEquals(DetectionStatus.PENDING, r2.transitions.single().status)
    }

    @Test fun `stale fixes are ignored`() {
        val (s, _) = run(listOf(at(office, 0.0, t0 + 10 * MIN)))
        val r = PresenceEngine.process(s, zones, at(office, 0.0, t0), cfg)
        assertEquals(DetectionStatus.DUPLICATE, r.rejected)
    }

    @Test fun `zero dwell opens immediately`() {
        val r = PresenceEngine.process(EngineState(), zones, at(office, 0.0, t0), cfg.copy(dwellMs = 0))
        assertEquals(t0, r.opened?.at)
    }

    @Test fun `deleted place closes the open visit`() {
        val (s, _) = run(listOf(at(office, 0.0, t0), at(office, 0.0, t0 + 3 * MIN)))
        val r = PresenceEngine.process(s, listOf(home), at(office, 0.0, t0 + 10 * MIN), cfg)
        assertEquals(office.id, r.closed?.placeId)
        assertNull(r.state.currentPlaceId)
    }

    @Test fun `overlapping zones pick the most specific (smallest) one`() {
        val big = Zone(3, "Area kantor", office.lat, office.lng, 1000)
        val r = PresenceEngine.process(EngineState(), listOf(big, office), at(office, 10.0, t0), cfg.copy(dwellMs = 0))
        assertEquals(office.id, r.opened?.placeId)
    }

    @Test fun `merge window and next check delay`() {
        assertTrue(PresenceEngine.shouldMerge(t0, t0 + 5 * MIN, cfg))
        assertFalse(PresenceEngine.shouldMerge(t0, t0 + 30 * MIN, cfg))
        assertFalse(PresenceEngine.shouldMerge(null, t0, cfg))
        val pending = EngineState(pendingPlaceId = 1, pendingSince = t0)
        assertEquals(3 * MIN - MIN, PresenceEngine.nextCheckDelay(pending, cfg, t0 + MIN))
        assertNull(PresenceEngine.nextCheckDelay(EngineState(), cfg, t0))
    }

    @Test fun `haversine distance is accurate`() {
        // Monas → Bundaran HI ≈ 2.6 km
        val d = PresenceEngine.distanceM(-6.1754, 106.8272, -6.1950, 106.8231)
        assertEquals(2_220.0, d, 150.0)
    }

    companion object { const val MIN = 60_000L }
}

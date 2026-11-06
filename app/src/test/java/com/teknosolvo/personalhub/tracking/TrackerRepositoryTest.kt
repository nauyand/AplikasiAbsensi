package com.teknosolvo.personalhub.tracking

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teknosolvo.personalhub.FakeClock
import com.teknosolvo.personalhub.data.ActivitySource
import com.teknosolvo.personalhub.data.DetectionStatus
import com.teknosolvo.personalhub.data.EventSource
import com.teknosolvo.personalhub.data.EventType
import com.teknosolvo.personalhub.data.HubDatabase
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.data.SettingsRepository
import com.teknosolvo.personalhub.util.dateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** End-to-end tracker tests on a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class TrackerRepositoryTest {
    private lateinit var db: HubDatabase
    private lateinit var repo: TrackerRepository
    private lateinit var settings: SettingsRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = FakeClock()
    private val scheduled = mutableListOf<Long>()
    private var syncRequests = 0
    private lateinit var office: Place
    private lateinit var home: Place

    private val MIN = 60_000L

    @Before fun setUp() = runBlocking {
        db = HubDatabase.inMemory(ApplicationProvider.getApplicationContext())
        settings = SettingsRepository(db.settings(), scope)
        repo = TrackerRepository(db, settings, notifier = null, scheduler = { scheduled += it }, geofenceSync = { syncRequests++ }, clock = clock)
        // 1. Add office with a custom radius; 2. add home.
        val officeId = repo.savePlace(Place(name = "Kantor", kind = PlaceKind.OFFICE, latitude = -6.2250, longitude = 106.8090,
            radiusM = 100, colorArgb = 0xFF8B7CFF, iconKey = "office", createdAt = 0, updatedAt = 0))
        val homeId = repo.savePlace(Place(name = "Rumah", kind = PlaceKind.HOME, latitude = -6.2600, longitude = 106.7800,
            radiusM = 150, colorArgb = 0xFF3DA9FC, iconKey = "home", createdAt = 0, updatedAt = 0))
        office = db.places().byId(officeId)!!
        home = db.places().byId(homeId)!!
    }

    @After fun tearDown() { db.close(); scope.cancel() }

    private fun fix(p: Place, meters: Double, t: Long, acc: Float = 10f) = Fix(p.latitude + meters / 111_320.0, p.longitude, acc, t)

    private suspend fun feed(p: Place, meters: Double, t: Long, source: String = EventSource.GEOFENCE, hint: String? = null) =
        repo.processFix(fix(p, meters, t), source, hint, if (hint != null) listOf(p.id) else emptyList())

    @Test fun `place radius is stored and changes request a geofence sync`() = runBlocking {
        assertEquals(100, office.radiusM)
        repo.savePlace(office.copy(radiusM = 250))
        assertEquals(250, db.places().byId(office.id)!!.radiusM)
        assertEquals(3, syncRequests)
    }

    @Test fun `enter and exit are stored with real times and duration`() = runBlocking {
        val t0 = clock.now
        feed(office, 0.0, t0, hint = EventType.ENTER)
        assertTrue("confirmation check scheduled", scheduled.isNotEmpty())
        clock.advance(3 * MIN)
        val opened = feed(office, 10.0, clock.now, EventSource.CONFIRM).opened
        assertNotNull(opened)
        assertEquals(t0, opened!!.startAt)
        assertEquals(t0.dateKey(), opened.date)
        assertEquals(ActivitySource.AUTO, opened.source)

        val state = db.trackerState().get()!!
        assertEquals(office.id, state.currentPlaceId)
        assertEquals(opened.id, state.currentActivityId)

        val leaveAt = t0 + 90 * MIN
        clock.now = leaveAt
        feed(office, 500.0, leaveAt, hint = EventType.EXIT)
        clock.advance(3 * MIN)
        val closed = feed(office, 800.0, clock.now, EventSource.CONFIRM).closed
        assertNotNull(closed)
        assertEquals(leaveAt, closed!!.endAt)
        assertEquals(90 * MIN, closed.endAt!! - closed.startAt)
        assertNull(db.trackerState().get()!!.currentPlaceId)
    }

    @Test fun `duplicate geofence events do not create duplicate activities`() = runBlocking {
        val t0 = clock.now
        feed(office, 0.0, t0, hint = EventType.ENTER)
        feed(office, 0.0, t0 + 3 * MIN, EventSource.CONFIRM)
        // The OS (or a flaky GPS) reports ENTER again several times.
        repeat(5) { i -> feed(office, 20.0, t0 + (10 + i) * MIN, hint = EventType.ENTER) }
        // Bounce inside the hysteresis band.
        repeat(10) { i -> feed(office, if (i % 2 == 0) 95.0 else 125.0, t0 + (20 + i) * MIN, EventSource.PERIODIC) }

        val acts = db.activities().all()
        assertEquals(1, acts.size)
        assertTrue(acts.single().isOpen)
        val dupes = db.events().all().count { it.status == DetectionStatus.DUPLICATE }
        assertEquals(5, dupes)
    }

    @Test fun `returning within merge gap continues the same visit`() = runBlocking {
        val t0 = clock.now
        feed(office, 0.0, t0); feed(office, 0.0, t0 + 3 * MIN)
        feed(office, 600.0, t0 + 60 * MIN); feed(office, 600.0, t0 + 63 * MIN) // exit at +60
        feed(office, 0.0, t0 + 65 * MIN); feed(office, 0.0, t0 + 68 * MIN)     // back at +65 (< 10 min gap)
        val acts = db.activities().all()
        assertEquals(1, acts.size)
        assertTrue(acts.single().isOpen)
        assertTrue(db.events().all().any { it.status == DetectionStatus.MERGED })
    }

    @Test fun `daily sequence office - lunch - office - home is recorded in order`() = runBlocking {
        val warung = db.places().byId(repo.savePlace(Place(name = "Warung", kind = PlaceKind.FOOD, latitude = -6.2350, longitude = 106.8090,
            radiusM = 60, colorArgb = 0, iconKey = "food", createdAt = 0, updatedAt = 0)))!!
        var t = clock.now
        suspend fun stay(p: Place, minutes: Int) {
            feed(p, 0.0, t); t += 3 * MIN; feed(p, 0.0, t); t += (minutes - 3) * MIN
        }
        suspend fun travel(from: Place) { feed(from, 2000.0, t); t += 3 * MIN; feed(from, 2000.0, t); t += 10 * MIN }
        stay(office, 230); travel(office)
        stay(warung, 40); travel(warung)
        stay(office, 260); travel(office)
        stay(home, 60)
        val acts = db.activities().all()
        assertEquals(listOf("Kantor", "Warung", "Kantor", "Rumah"), acts.map { it.title })
        assertEquals(3, acts.count { !it.isOpen })
        // Timeline continuity: each visit ends before the next starts.
        acts.zipWithNext().forEach { (a, b) -> assertTrue(a.endAt!! <= b.startAt) }
    }

    @Test fun `manual correction is flagged and keeps engine state consistent`() = runBlocking {
        val t0 = clock.now
        feed(office, 0.0, t0); feed(office, 0.0, t0 + 3 * MIN)
        val open = db.activities().all().single()
        // User closes it manually and moves the start earlier.
        repo.updateActivity(open.copy(startAt = t0 - 15 * MIN, endAt = t0 + 30 * MIN, note = "datang lebih awal"))
        val edited = db.activities().byId(open.id)!!
        assertTrue(edited.edited)
        assertTrue(edited.isManual)
        assertEquals("datang lebih awal", edited.note)
        assertNull("manual close clears current place", db.trackerState().get()!!.currentActivityId)

        // Added missing activity is marked MANUAL.
        val id = repo.addManual(home.id, "Rumah", PlaceKind.HOME, t0 - 10 * 60 * MIN, t0 - 9 * 60 * MIN, null)
        assertEquals(ActivitySource.MANUAL, db.activities().byId(id)!!.source)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `manual activity with end before start is rejected`() = runBlocking<Unit> {
        repo.addManual(office.id, "Kantor", PlaceKind.OFFICE, clock.now, clock.now - MIN, null)
    }

    @Test fun `deleting the current activity resets detection`() = runBlocking {
        feed(office, 0.0, clock.now); feed(office, 0.0, clock.now + 3 * MIN)
        repo.deleteActivity(db.activities().all().single())
        // Next fix inside starts a new pending entry rather than referencing a deleted row.
        feed(office, 0.0, clock.now + 10 * MIN)
        assertEquals(office.id, db.trackerState().get()!!.pendingPlaceId)
        feed(office, 0.0, clock.now + 13 * MIN)
        assertEquals(1, db.activities().all().size)
    }

    @Test fun `disabling a place closes its open visit`() = runBlocking {
        feed(office, 0.0, clock.now); feed(office, 0.0, clock.now + 3 * MIN)
        repo.setPlaceEnabled(office, false)
        assertFalse(db.activities().all().single().isOpen)
        assertNull(db.trackerState().get()!!.currentPlaceId)
    }

    @Test fun `daily and monthly queries return overlapping activities`() = runBlocking {
        val t0 = clock.now
        feed(office, 0.0, t0); feed(office, 0.0, t0 + 3 * MIN)
        feed(office, 900.0, t0 + 120 * MIN); feed(office, 900.0, t0 + 123 * MIN)
        val day = repo.overlapping(t0 - 3_600_000, t0 + 86_400_000).first()
        assertEquals(1, day.size)
        val none = repo.overlapping(t0 + 200 * MIN, t0 + 300 * MIN).first()
        assertTrue(none.isEmpty())
        val dates = repo.activeDates("2000-01-01", "2100-01-01").first()
        assertEquals(listOf(t0.dateKey()), dates)
    }

    @Test fun `simulated data is labelled and removable without touching real data`() = runBlocking {
        val instant = settings.current.engineConfig().copy(dwellMs = 0, exitDelayMs = 0)
        repo.processFix(fix(home, 0.0, clock.now), EventSource.SIMULATION, configOverride = instant)
        repo.processFix(fix(home, 900.0, clock.now + MIN), EventSource.SIMULATION, configOverride = instant)
        repo.addManual(office.id, "Kantor", PlaceKind.OFFICE, clock.now - 60 * MIN, clock.now - 30 * MIN, null)
        assertEquals(ActivitySource.SIMULATED, db.activities().all().first { it.title == "Rumah" }.source)
        repo.clearSimulated()
        assertEquals(listOf("Kantor"), db.activities().all().map { it.title })
    }

    @Test fun `tracking disabled ignores real fixes`() = runBlocking {
        settings.updateNow { it.copy(trackingEnabled = false) }
        val out = repo.processFix(fix(office, 0.0, clock.now), EventSource.GEOFENCE)
        assertEquals("DISABLED", out.rejected)
        assertTrue(db.activities().all().isEmpty())
    }
}

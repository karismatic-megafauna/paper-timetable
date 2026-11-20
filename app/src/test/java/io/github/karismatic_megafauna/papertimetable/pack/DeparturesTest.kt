package io.github.karismatic_megafauna.papertimetable.pack

import io.github.karismatic_megafauna.papertimetable.TestPacks
import io.github.karismatic_megafauna.papertimetable.TestPacks.at
import io.github.karismatic_megafauna.papertimetable.time.ServiceClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeparturesTest {
    private val db = TestPacks.mini()
    private val la: ServiceClock = TestPacks.anyClock
    private val D = db.route("D")!!.id
    private val R720 = db.route("720")!!.id
    private val R617 = db.route("617")!!.id
    private val R99 = db.route("99")!!.id
    private val ALPHA = db.stop("rail:ALPHA")!!.id
    private val GAMMA = db.stop("rail:GAMMA")!!.id
    private val S101 = db.stop("bus:101")!!.id
    private val S102 = db.stop("bus:102")!!.id
    private val S301 = db.stop("bus:301")!!.id
    private val alphaPlace = db.stop("rail:ALPHA")!!.placeId

    @After fun close() = db.close()

    private fun next(stop: Int, route: Int, dir: Int, from: LocalDateTime, limit: Int) = db.nextDepartures(stop, route, dir, from, limit)
    private fun assertTimes(expected: String, actual: List<Departure>) = assertEquals(expected, actual.joinToString(" ") { la.format(it.time) })

    @Test fun nextThree() = assertTimes("4:10p 4:20p 4:30p", next(ALPHA, D, 0, at("2026-11-04T16:03"), 3))

    @Test fun aDepartureAtExactlyNowIsIncluded() = assertTimes("4:10p", next(ALPHA, D, 0, at("2026-11-04T16:10"), 1))

    @Test fun afterMidnightBelongsToPreviousDay() {
        val d = next(ALPHA, D, 0, at("2026-11-05T00:05"), 3)
        assertTimes("12:10a 12:40a 1:10a", d)
        assertEquals(listOf(20261104, 20261104, 20261104), d.map { it.serviceDay })
    }

    @Test fun endOfServiceRollsToNextDay() {
        val d = next(ALPHA, D, 0, at("2026-11-05T01:15"), 1).single()
        assertEquals(at("2026-11-05T05:00"), d.time)
    }

    @Test fun shortTurnIsMarked() {
        val d = next(ALPHA, D, 0, at("2026-11-04T23:52"), 2)
        assertTimes("11:55p 12:10a", d)
        assertEquals("Beta Station", d[0].otherHeadsign)
        assertEquals(null, d[1].otherHeadsign)
    }

    @Test fun terminusHasNoDepartures() = assertTrue(next(GAMMA, D, 0, at("2026-11-04T16:03"), 3).isEmpty())

    @Test fun holidayUsesSundayBusService() = assertTimes("7:00a 7:30a", next(S101, R720, 0, at("2026-11-26T06:00"), 2))

    @Test fun railOnProjectedDay() = assertTimes("4:10p", next(ALPHA, D, 0, at("2026-11-26T16:03"), 1))

    @Test fun dayBeyondPackUsesSameWeekday() = assertEquals(54, db.dayDepartures(ALPHA, D, 0, 20261220).size) // a Sunday

    @Test fun dayDeparturesAreInOrderAndIncludeAfterMidnight() {
        val d = db.dayDepartures(ALPHA, D, 0, 20261104)
        assertEquals(118, d.size)
        assertEquals(at("2026-11-04T05:00"), d.first().time)
        assertEquals(at("2026-11-05T01:10"), d.last().time)
        assertEquals(d.map { it.time }.sorted(), d.map { it.time })
    }

    // At 12:30 on Thursday morning the trains about to leave belong to Wednesday's service.
    @Test fun serviceDayForLateNightIsThePreviousDay() {
        assertEquals(20261104, db.serviceDayFor(ALPHA, D, 0, at("2026-11-05T00:30")))
        assertEquals(20261105, db.serviceDayFor(ALPHA, D, 0, at("2026-11-05T01:15"))) // last train has gone
        assertEquals(20261104, db.serviceDayFor(ALPHA, D, 0, at("2026-11-04T16:00")))
        assertEquals(20261107, db.serviceDayFor(S101, R720, 0, at("2026-11-07T00:30"))) // 720 does not run that late
    }

    // Review Focus 5
    @Test fun noSaturdayServiceSkipsToSunday() {
        val d = next(S101, R720, 0, at("2026-11-07T10:00"), 1).single()
        assertEquals(at("2026-11-08T07:00"), d.time)
    }

    @Test fun loopsFinalVisitIsNotADeparture() = assertTimes("6:30a 9:30a", next(S102, R99, 0, at("2026-11-04T06:00"), 2))

    @Test fun lineWithNoServiceForAWeekReturnsNothing() {
        // 617 runs on weekdays only; ask about a stop it never departs from.
        assertTrue(next(db.stop("bus:201")!!.id, R617, 0, at("2026-11-04T06:00"), 2).isEmpty())
    }

    @Test fun placeBoardListsEveryLineAtPlace() {
        val rows = db.placeBoard(alphaPlace, at("2026-11-04T16:03"), 2)
        assertEquals(listOf("D→Gamma Station", "99→Alpha / Main", "720→Far / Out"), rows.map { "${it.route.shortName}→${it.headsign}" })
        assertTimes("4:10p 4:20p", rows[0].departures)
        assertEquals("Alpha / Main", rows[2].stopName)
    }

    @Test fun placeBoardKeepsARowWithNoServiceToday() {
        val far = db.stop("bus:301")!!.placeId
        val rows = db.placeBoard(far, at("2026-11-07T09:00"), 2) // Saturday: 617 does not run
        val r617 = rows.single { it.route.shortName == "617" }
        assertEquals(at("2026-11-09T07:00"), r617.departures.first().time) // Monday
    }
}

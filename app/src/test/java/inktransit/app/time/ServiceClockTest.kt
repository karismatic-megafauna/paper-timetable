package inktransit.app.time

import inktransit.app.TestPacks.LA
import inktransit.app.TestPacks.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class ServiceClockTest {
    private val la = ServiceClock(LA) { Instant.parse("2026-11-05T00:03:00Z") }

    @Test fun formats() {
        assertEquals("4:12p", la.format(at("2026-11-04T16:12")))
        assertEquals("12:10a", la.format(at("2026-11-05T00:10")))
        assertEquals("12:00p", la.format(at("2026-11-05T12:00")))
        assertEquals("9:05a", la.format(at("2026-11-05T09:05")))
    }

    // Review Focus 1: a phone set to another time zone must not shift the schedule.
    @Test fun nowUsesAgencyZoneNotDeviceZone() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(at("2026-11-04T16:03"), la.now())
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test fun candidatesLookAtYesterdaysLateTrips() {
        assertEquals(
            listOf(ServiceTime(20261104, 86400 + 300), ServiceTime(20261105, 300)),
            la.candidates(at("2026-11-05T00:05")),
        )
    }

    // Clocks go back on 2026-11-01. Schedule times count from "noon minus 12 hours".
    @Test fun fallBackDayIsCorrect() {
        assertEquals(at("2026-11-01T02:00"), toLocal(LA, 20261101, 7200))
        assertEquals(at("2026-11-01T12:00"), toLocal(LA, 20261101, 43200))
    }

    @Test fun afterMidnightTimeLandsOnTheNextCalendarDay() {
        assertEquals(at("2026-11-05T01:10"), toLocal(LA, 20261104, 25 * 3600 + 600))
    }

    @Test fun dayLabels() {
        val now = at("2026-11-19T16:00")
        assertNull(la.dayLabel(at("2026-11-19T23:00"), now))
        assertEquals("Tomorrow", la.dayLabel(at("2026-11-20T05:00"), now))
        assertEquals("Sat Nov 21", la.dayLabel(at("2026-11-21T05:00"), now))
    }

    @Test fun dayNumbers() {
        assertEquals(20261104, dayOf(at("2026-11-04T16:03").toLocalDate()))
        assertEquals(at("2026-11-04T00:00").toLocalDate(), dateOf(20261104))
    }
}

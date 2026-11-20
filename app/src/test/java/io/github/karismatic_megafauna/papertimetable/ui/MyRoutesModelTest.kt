package io.github.karismatic_megafauna.papertimetable.ui

import io.github.karismatic_megafauna.papertimetable.TestPacks
import io.github.karismatic_megafauna.papertimetable.TestPacks.at
import io.github.karismatic_megafauna.papertimetable.data.SavedRoute
import io.github.karismatic_megafauna.papertimetable.pack.stop
import io.github.karismatic_megafauna.papertimetable.ui.screens.buildCards
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyRoutesModelTest {
    private val db = TestPacks.mini()
    private val la = TestPacks.anyClock
    private val dAlpha = SavedRoute("mini", "D", 0, "rail:ALPHA", "D", "Gamma Station", "Alpha Station")
    private val bus720 = SavedRoute("mini", "720", 0, "bus:101", "720", "Far / Out", "Alpha / Main")

    @After fun close() = db.close()

    @Test fun cardsShowNextThree() {
        val c = buildCards(db, la, listOf(dAlpha), at("2026-11-04T16:03")).single()
        assertTrue(c.resolved)
        assertEquals(listOf("4:10p", "4:20p", "4:30p"), c.times)
        assertNull(c.dayLabel)
        assertEquals(db.stop("rail:ALPHA")!!.placeId, c.placeId)
    }

    @Test fun cardAfterServiceShowsTomorrow() {
        val late = buildCards(db, la, listOf(bus720), at("2026-11-04T23:59")).single()
        assertEquals("6:00a", late.times.first())
        assertEquals("Tomorrow", late.dayLabel)
        val early = buildCards(db, la, listOf(dAlpha), at("2026-11-05T01:15")).single()
        assertEquals("5:00a", early.times.first())
        assertNull(early.dayLabel) // same calendar day
    }

    // After a reroute the line and the stop can both still exist while the line no longer
    // calls there. "No service" would read as "not today"; the rider must be told to move.
    @Test fun lineThatNoLongerServesTheStopIsUnresolved() {
        val rerouted = dAlpha.copy(stopKey = "bus:301", stopName = "Far / Out")
        assertFalse(buildCards(db, la, listOf(rerouted), at("2026-11-04T16:03")).single().resolved)
    }

    @Test fun aStopThatIsOnlyTheEndOfTheLineIsUnresolved() {
        val terminus = dAlpha.copy(stopKey = "rail:GAMMA", stopName = "Gamma Station")
        assertFalse(buildCards(db, la, listOf(terminus), at("2026-11-04T16:03")).single().resolved)
    }

    // Review Focus 4: a pack update can remove a line or a stop a rider had saved.
    @Test fun missingStopIsUnresolvedNotACrash() {
        val c = buildCards(db, la, listOf(dAlpha.copy(stopKey = "bus:999")), at("2026-11-04T16:03")).single()
        assertFalse(c.resolved)
        assertTrue(c.times.isEmpty())
        assertNull(c.placeId)
    }

    @Test fun missingLineIsUnresolved() =
        assertFalse(buildCards(db, la, listOf(dAlpha.copy(routeKey = "Z")), at("2026-11-04T16:03")).single().resolved)

    @Test fun otherAgencyIsUnresolved() =
        assertFalse(buildCards(db, la, listOf(dAlpha.copy(agencyId = "elsewhere")), at("2026-11-04T16:03")).single().resolved)
}

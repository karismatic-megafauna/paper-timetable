package io.github.karismatic_megafauna.papertimetable.ui

import io.github.karismatic_megafauna.papertimetable.TestPacks
import io.github.karismatic_megafauna.papertimetable.pack.RouteRow
import io.github.karismatic_megafauna.papertimetable.pack.placeLines
import io.github.karismatic_megafauna.papertimetable.pack.route
import io.github.karismatic_megafauna.papertimetable.pack.routes
import io.github.karismatic_megafauna.papertimetable.pack.stop
import io.github.karismatic_megafauna.papertimetable.ui.screens.linesShown
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextTest {
    private val db = TestPacks.mini()
    @After fun close() = db.close()

    @Test fun trailNamesEveryScreenBehindTheOneOnShow() {
        val d = db.route("D")!!
        val alpha = db.stop("rail:ALPHA")!!
        val nav = Nav()
        assertEquals(emptyList<String>(), crumbLabels(db, nav.trail))
        nav.push(Screen.Lines)
        nav.push(Screen.LineOverview(d.id))
        nav.push(Screen.DirectionStops(d.id, 0))
        nav.push(Screen.StopView(alpha.placeId, alpha.id))
        nav.push(Screen.DayTimes(alpha.id, d.id, 0))
        assertEquals(listOf("Home", "Lines", "D Line", "D → Gamma Station", "Alpha Station"), crumbLabels(db, nav.trail))
        nav.pop()
        assertEquals(listOf("Home", "Lines", "D Line", "D → Gamma Station"), crumbLabels(db, nav.trail))
    }

    @Test fun busLinesAreNamedByNumberInTheTrail() {
        val nav = Nav().apply { push(Screen.NearMe); push(Screen.LineOverview(db.route("720")!!.id)); push(Screen.About) }
        assertEquals(listOf("Home", "Near me", "Line 720"), crumbLabels(db, nav.trail))
    }

    @Test fun backButtonNamesTheScreenItGoesTo() {
        val d = db.route("D")!!
        val nav = Nav()
        assertNull(backLabel(db, nav.trail)) // home: nowhere to go back to
        nav.push(Screen.Lines)
        assertEquals("‹ Home", backLabel(db, nav.trail))
        nav.push(Screen.LineOverview(d.id))
        nav.push(Screen.DirectionStops(d.id, 0))
        assertEquals("‹ D Line", backLabel(db, nav.trail))
    }

    @Test fun placesListTheLinesThatCanBeBoardedThere() {
        val alpha = db.stop("rail:ALPHA")!!.placeId
        val gamma = db.stop("rail:GAMMA")!!.placeId
        val lines = db.placeLines(listOf(alpha, gamma))
        assertEquals(listOf("D", "99", "720"), lines[alpha]) // rail first, then buses in line order
        assertEquals(listOf("D"), lines[gamma])
        assertEquals(emptyMap<Int, List<String>>(), db.placeLines(emptyList()))
    }

    @Test fun linesAreSplitIntoRailAndBusUntilSomethingIsTyped() {
        val routes = db.routes()
        assertEquals(listOf("D"), linesShown(routes, "", rail = true).map(RouteRow::shortName))
        assertEquals(listOf("99", "617", "720"), linesShown(routes, " ", rail = false).map(RouteRow::shortName))
        // Typing looks in both, whichever is selected.
        assertEquals(listOf("720"), linesShown(routes, "72", rail = true).map(RouteRow::shortName))
        assertEquals(listOf("D"), linesShown(routes, "d", rail = false).map(RouteRow::shortName))
    }
}

package inktransit.app.ui

import inktransit.app.TestPacks
import inktransit.app.pack.RouteRow
import inktransit.app.pack.placeLines
import inktransit.app.pack.route
import inktransit.app.pack.routes
import inktransit.app.pack.stop
import inktransit.app.ui.screens.linesShown
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
        assertEquals(listOf("Home", "Lines", "D Line", "→ Gamma Station", "Alpha Station"), crumbLabels(db, nav.trail))
        nav.pop()
        assertEquals(listOf("Home", "Lines", "D Line", "→ Gamma Station"), crumbLabels(db, nav.trail))
    }

    @Test fun busLinesAreNamedByNumberInTheTrail() {
        val nav = Nav().apply { push(Screen.NearMe); push(Screen.LineOverview(db.route("720")!!.id)); push(Screen.About) }
        assertEquals(listOf("Home", "Near me", "Line 720"), crumbLabels(db, nav.trail))
    }

    @Test fun trailTextKeepsTheNearestStepsWhenItIsTooLong() {
        assertNull(crumbText(emptyList()))
        assertEquals("Home", crumbText(listOf("Home")))
        assertEquals("Home › Lines › D Line", crumbText(listOf("Home", "Lines", "D Line"), 30))
        assertEquals("… › D Line › → Gamma Station", crumbText(listOf("Home", "Lines", "D Line", "→ Gamma Station"), 30))
        // One step that is too long on its own is left for the screen to cut off.
        assertEquals("… › A very long station name indeed", crumbText(listOf("Home", "A very long station name indeed"), 10))
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

package io.github.karismatic_megafauna.papertimetable.ui

import io.github.karismatic_megafauna.papertimetable.TestPacks
import io.github.karismatic_megafauna.papertimetable.pack.FreqRow
import io.github.karismatic_megafauna.papertimetable.pack.route
import io.github.karismatic_megafauna.papertimetable.pack.routes
import io.github.karismatic_megafauna.papertimetable.ui.screens.bandLines
import io.github.karismatic_megafauna.papertimetable.ui.screens.filterLines
import io.github.karismatic_megafauna.papertimetable.ui.screens.overview
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinesModelTest {
    private val db = TestPacks.mini()
    private val la = TestPacks.anyClock
    private val routes = db.routes()
    private val D = db.route("D")!!.id
    private val R720 = db.route("720")!!.id

    @After fun close() = db.close()

    @Test fun filter() {
        assertEquals(listOf("617"), filterLines(routes, "6").map { it.shortName })
        assertEquals(4, filterLines(routes, "").size)
        assertEquals(listOf("D"), filterLines(routes, "d").map { it.shortName })
        assertEquals(listOf("720", "617"), filterLines(routes, "7").map { it.shortName }) // starts-with first, then contains
    }

    @Test fun overviewWeekday() {
        val wk = overview(db, la, D).first { it.dayType == 0 }
        assertEquals("5:00a – 1:10a", wk.firstLast)
        assertEquals("Midday" to "every 10 min", wk.bands[2].name to wk.bands[2].text)
        assertNull(wk.bands[2].note)
        assertEquals(6, wk.bands.size)
    }

    @Test fun overviewNoService() {
        val sa = overview(db, la, R720).first { it.dayType == 1 }
        assertNull(sa.firstLast)
        assertEquals("no service", sa.bands[2].text)
    }

    @Test fun overviewHasAllThreeDayTypes() = assertEquals(listOf(0, 1, 2), overview(db, la, D).map { it.dayType })

    @Test fun aBandWhereDirectionsDifferALotSaysSo() {
        val rows = listOf(FreqRow(0, 2, 36, 10), FreqRow(1, 2, 12, 30))
        val line = bandLines(rows, mapOf(0 to "Gamma Station", 1 to "Alpha Station"))[2]
        assertEquals("every 10 min", line.text)
        assertEquals("→ Alpha Station: every 30 min", line.note)
    }

    @Test fun aSmallDifferenceBetweenDirectionsIsNotMentioned() {
        val rows = listOf(FreqRow(0, 2, 36, 10), FreqRow(1, 2, 30, 12))
        assertNull(bandLines(rows, mapOf(0 to "A", 1 to "B"))[2].note)
    }
}

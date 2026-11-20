package io.github.karismatic_megafauna.papertimetable.ui

import io.github.karismatic_megafauna.papertimetable.TestPacks
import io.github.karismatic_megafauna.papertimetable.TestPacks.at
import io.github.karismatic_megafauna.papertimetable.pack.place
import io.github.karismatic_megafauna.papertimetable.pack.stop
import io.github.karismatic_megafauna.papertimetable.ui.screens.boardRows
import io.github.karismatic_megafauna.papertimetable.ui.screens.dayOptions
import io.github.karismatic_megafauna.papertimetable.ui.screens.gridStartRow
import io.github.karismatic_megafauna.papertimetable.ui.screens.isBeyondPublished
import io.github.karismatic_megafauna.papertimetable.ui.screens.staleText
import io.github.karismatic_megafauna.papertimetable.ui.screens.whenLabel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhenTest {
    private val db = TestPacks.mini()
    private val la = TestPacks.anyClock
    private val meta = db.meta
    private val alphaPlace = db.stop("rail:ALPHA")!!.placeId

    @After fun close() = db.close()

    @Test fun dayLabels() {
        val o = dayOptions(LocalDate.of(2026, 11, 19))
        assertEquals(listOf("Today", "Tomorrow", "Sat Nov 21"), o.take(3).map { it.second })
        assertEquals(28, o.size)
        assertEquals(LocalDate.of(2026, 12, 16), o.last().first)
    }

    @Test fun beyondPublished() {
        assertFalse(isBeyondPublished(meta, 20261110, 20261104))
        assertTrue(isBeyondPublished(meta, 20261126, 20261104))
        assertTrue(isBeyondPublished(meta, 20261116, 20261116))
    }

    @Test fun staleWording() {
        assertNull(staleText(meta, 20261110, 20261104))
        assertEquals("Beyond published schedule — times may change", staleText(meta, 20261126, 20261104))
        assertEquals("Schedule may be out of date", staleText(meta, 20261116, 20261116))
        assertEquals("Schedule may be out of date", staleText(meta, 20261220, 20261216))
    }

    @Test fun whenLabels() {
        val now = at("2026-11-19T16:00")
        assertEquals("When: Now", whenLabel(WhenChoice.Now, la, now))
        assertEquals("When: Sat Nov 21, 9:00a", whenLabel(WhenChoice.At(at("2026-11-21T09:00")), la, now))
        assertEquals("When: Today, 6:15p", whenLabel(WhenChoice.At(at("2026-11-19T18:15")), la, now))
    }

    @Test fun boardAtChosenTime() {
        val rows = boardRows(db, alphaPlace, at("2026-11-07T09:05"), null) // Saturday
        assertEquals(listOf("9:20a", "9:40a"), rows.first { it.route.shortName == "D" }.departures.map { la.format(it.time) })
    }

    @Test fun highlightedStopComesFirst() {
        val s101 = db.stop("bus:101")!!.id
        assertEquals("720", boardRows(db, alphaPlace, at("2026-11-04T16:03"), s101).first().route.shortName)
        assertEquals("D", boardRows(db, alphaPlace, at("2026-11-04T16:03"), null).first().route.shortName)
    }

    @Test fun noServiceRowIsKept() {
        val far = db.stop("bus:301")!!.placeId
        assertEquals("Far / Out", db.place(far).name)
        val row = boardRows(db, far, at("2026-11-07T09:00"), null).single { it.route.shortName == "617" } // Saturday
        assertEquals(at("2026-11-09T07:00"), row.departures.first().time) // Monday
    }

    @Test fun dayGridOpensOnTheRowHoldingTheChosenTime() {
        val times = (0 until 60).map { at("2026-11-04T05:00").plusMinutes(it * 10L) } // 5:00a … 2:50p
        assertEquals(0, gridStartRow(times, at("2026-11-04T04:00"), 4))
        assertEquals(6, gridStartRow(times, at("2026-11-04T09:05"), 4)) // 9:10a is index 25, in the seventh row of four
        assertEquals(14, gridStartRow(times, at("2026-11-04T23:00"), 4)) // after the last: the last row
        assertEquals(0, gridStartRow(emptyList(), at("2026-11-04T09:05"), 4))
    }
}

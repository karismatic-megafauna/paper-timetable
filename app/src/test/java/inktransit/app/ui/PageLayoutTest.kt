package inktransit.app.ui

import inktransit.app.TestPacks
import inktransit.app.pack.stop
import inktransit.app.ui.screens.boardRowDp
import inktransit.app.ui.screens.boardRows
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PageLayoutTest {
    @Test fun aFullPageLeavesNoGapAboveThePageButtons() {
        // 460 dp of room, 56 dp rows, 40 items: 7 rows fit above the 66 dp bar and share all 394 dp.
        val layout = pageLayout(460f, 56f, 40)
        assertEquals(7, layout.size)
        assertEquals(394f, layout.size * layout.slotDp, 0.01f)
    }

    @Test fun aTallerScreenShowsMoreRowsNotMoreGap() {
        assertEquals(5, pageLayout(400f, 56f, 40).size)
        assertEquals(8, pageLayout(520f, 56f, 40).size)
    }

    @Test fun aShortListKeepsItsRowsNearTheirNaturalHeight() {
        // Two routes must not stretch into two half-screen slabs.
        val layout = pageLayout(460f, 56f, 2)
        assertEquals(8, layout.size)
        assertEquals(57.5f, layout.slotDp, 0.01f)
    }

    @Test fun thereIsAlwaysOneRow() = assertEquals(1, pageLayout(30f, 56f, 9).size)

    @Test fun stopRowsAreSizedForTheTallestRowAtThePlace() {
        val db = TestPacks.mini()
        try {
            val alpha = db.stop("rail:ALPHA")!!.placeId
            val plain = boardRows(db, alpha, TestPacks.at("2026-11-04T09:00"), null)
            // Late at night one D train from Alpha turns back early, so its row carries an "* only to" line.
            val noted = boardRows(db, alpha, TestPacks.at("2026-11-04T23:52"), null)
            assertEquals(68f, boardRowDp(plain), 0.01f)
            assertEquals(89f, boardRowDp(noted), 0.01f)
            assertEquals(68f, boardRowDp(emptyList()), 0.01f)
        } finally { db.close() }
    }
}

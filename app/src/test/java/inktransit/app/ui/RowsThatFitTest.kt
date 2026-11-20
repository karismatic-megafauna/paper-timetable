package inktransit.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RowsThatFitTest {
    // Search shares the screen with the keyboard, so how many results fit is not fixed.
    @Test fun rowsThatFitAboveTheKeyboard() {
        assertEquals(6, rowsThatFit(availableDp = 420f, rowDp = 56f, total = 40))   // keyboard closed
        assertEquals(1, rowsThatFit(availableDp = 130f, rowDp = 56f, total = 40))   // keyboard open: one row plus the page bar
        assertEquals(2, rowsThatFit(availableDp = 130f, rowDp = 56f, total = 2))    // two results need no page bar
        assertEquals(1, rowsThatFit(availableDp = 20f, rowDp = 56f, total = 40))    // never zero
    }
}

package io.github.karismatic_megafauna.papertimetable.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PagerTest {
    @Test fun pages() {
        assertEquals(listOf(1, 2, 3), pageOf((1..7).toList(), 0, 3))
        assertEquals(listOf(7), pageOf((1..7).toList(), 2, 3))
        assertEquals(3, pageCount(7, 3))
        assertEquals(1, pageCount(0, 3))
        assertEquals(emptyList<Int>(), pageOf(emptyList<Int>(), 0, 3))
    }

    @Test fun pageBeyondTheEndIsEmptyNotACrash() {
        assertEquals(emptyList<Int>(), pageOf((1..7).toList(), 9, 3))
    }

    @Test fun exactMultipleHasNoEmptyLastPage() = assertEquals(2, pageCount(6, 3))
}

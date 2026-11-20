package io.github.karismatic_megafauna.papertimetable.data

import io.github.karismatic_megafauna.papertimetable.TestPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedRoutesTest {
    private val file = File(TestPacks.tempDir(), "saved.json")
    private val a = SavedRoute("mini", "D", 0, "rail:ALPHA", "D", "Gamma Station", "Alpha Station")
    private val b = SavedRoute("mini", "720", 0, "bus:101", "720", "Far / Out", "Alpha / Main")

    @Test fun addRemoveMovePersist() {
        SavedRoutes(file).apply { add(a); add(b); move(b, up = true) }
        assertEquals(listOf(b, a), SavedRoutes(file).all())
        SavedRoutes(file).remove(a)
        assertEquals(listOf(b), SavedRoutes(file).all())
    }

    @Test fun movingPastEitherEndDoesNothing() {
        val s = SavedRoutes(file).apply { add(a); add(b); move(a, up = true); move(b, up = false) }
        assertEquals(listOf(a, b), s.all())
    }

    @Test fun noDuplicates() {
        val s = SavedRoutes(file).apply { add(a); add(a.copy(stopName = "Renamed")) }
        assertEquals(1, s.all().size)
    }

    @Test fun contains() {
        val s = SavedRoutes(file).apply { add(a) }
        assertTrue(s.contains("mini", "D", 0, "rail:ALPHA"))
        assertFalse(s.contains("mini", "D", 1, "rail:ALPHA"))
        assertFalse(s.contains("other", "D", 0, "rail:ALPHA"))
    }

    @Test fun missingFileIsEmpty() = assertTrue(SavedRoutes(file).all().isEmpty())

    @Test fun corruptFileBecomesEmptyAndIsKept() {
        file.writeText("{{{")
        assertTrue(SavedRoutes(file).all().isEmpty())
        assertEquals("{{{", File(file.parentFile, "saved.bad").readText())
    }

    @Test fun noTempFileLeftBehind() {
        SavedRoutes(file).add(a)
        assertEquals(listOf("saved.json"), file.parentFile!!.list()!!.toList())
    }
}

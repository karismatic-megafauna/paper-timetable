package io.github.karismatic_megafauna.papertimetable.pack

import io.github.karismatic_megafauna.papertimetable.TestPacks
import io.github.karismatic_megafauna.papertimetable.text.FrequencyWords.tag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinesTest {
    private val db = TestPacks.mini()
    private val D = db.route("D")!!.id
    private val R720 = db.route("720")!!.id
    private val R617 = db.route("617")!!.id
    private val R99 = db.route("99")!!.id

    @After fun close() = db.close()

    @Test fun tags() {
        assertEquals("every 10 min", tag(db.frequency(D, 0)))
        assertEquals("about hourly", tag(db.frequency(R617, 0)))
        assertEquals("4 trips a day", tag(db.frequency(R99, 0)))
        assertEquals("no service", tag(db.frequency(R720, 1)))
        assertEquals("every 15 min", tag(db.frequency(R720, 0)))
        assertEquals("every 20 min", tag(db.frequency(D, 1)))
    }

    @Test fun allTagsInOneGo() {
        val tags = db.allFrequencyTags(0)
        assertEquals(mapOf(D to "every 10 min", R99 to "4 trips a day", R617 to "about hourly", R720 to "every 15 min"), tags)
    }

    @Test fun dayTypes() {
        assertEquals(0, dayTypeOf(20261104))
        assertEquals(1, dayTypeOf(20261107))
        assertEquals(2, dayTypeOf(20261108))
    }

    @Test fun routesInOrder() = assertEquals(listOf("D", "99", "617", "720"), db.routes().map { it.shortName })

    @Test fun directions() = assertEquals(listOf("Gamma Station", "Alpha Station"), db.directions(D).map { it.headsign })

    @Test fun directionStopsInTravelOrder() =
        assertEquals(listOf("Alpha Station", "Beta Station", "Gamma Station"), db.directionStops(D, 0).map { it.name })

    @Test fun spans() {
        val s = db.spans(D, 0).single { it.dir == 0 }
        assertEquals(18000 to 90600, s.first to s.last)
        assertTrue(db.spans(R720, 1).isEmpty())
    }

    @Test fun placeById() = assertEquals("Alpha Station", db.place(db.stop("rail:ALPHA")!!.placeId).name)

    @Test fun searchIgnoresSlashAndCase() {
        assertEquals("Far / Out", db.searchPlaces("far out", 10).single().name)
        assertEquals(listOf("Alpha Station"), db.searchPlaces("alph", 10).map { it.name })
        assertEquals(listOf("Far / Out"), db.searchPlaces("OUT/FAR", 10).map { it.name })
    }

    @Test fun searchRanksNamesStartingWithTheWordFirst() =
        assertEquals(listOf("Alpha Station", "Beta Station", "Far / Out", "Gamma Station"), db.searchPlaces("a", 10).map { it.name })

    @Test fun searchRespectsTheLimit() = assertEquals(2, db.searchPlaces("station", 2).size)

    @Test fun searchEmptyQueryReturnsNothing() = assertTrue(db.searchPlaces("  ", 10).isEmpty())
}

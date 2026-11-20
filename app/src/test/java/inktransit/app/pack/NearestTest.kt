package inktransit.app.pack

import inktransit.app.TestPacks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NearestTest {
    private val db = TestPacks.mini()

    @After fun close() = db.close()

    @Test fun nearestOrder() {
        val r = db.nearestPlaces(34.0001, -118.2999, 2)
        assertEquals(listOf("Alpha Station", "Beta Station"), r.map { it.first.name })
        assertTrue(r[0].second < 100)
        assertTrue(r[1].second in 800..1000)
    }

    @Test fun widensUntilEnoughPlacesAreFound() = assertEquals(4, db.nearestPlaces(34.0001, -118.2999, 10).size)

    @Test fun nowhereNearTheNetworkReturnsNothing() = assertTrue(db.nearestPlaces(40.0, -100.0, 3).isEmpty())

    @Test fun distanceWords() {
        assertEquals("350 ft", formatDistance(107, Locale.US))
        assertEquals("0.4 mi", formatDistance(640, Locale.US))
        assertEquals("120 m", formatDistance(120, Locale.GERMANY))
        assertEquals("1.2 km", formatDistance(1234, Locale.GERMANY))
        assertEquals("50 ft", formatDistance(3, Locale.US))
    }
}

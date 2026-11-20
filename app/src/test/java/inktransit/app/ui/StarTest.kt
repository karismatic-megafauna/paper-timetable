package inktransit.app.ui

import inktransit.app.TestPacks
import inktransit.app.data.SavedRoutes
import inktransit.app.pack.PackStore
import inktransit.app.pack.route
import inktransit.app.pack.stop
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
class StarTest {
    // The timetable screen has a line, a direction and a stop: enough to save the route from there too.
    @Test fun aRouteCanBeStarredAndUnstarredFromItsLineDirectionAndStop() {
        val store = PackStore(TestPacks.tempDir(), "v1") { TestPacks.miniFile().inputStream() }
        val app = AppState(store, SavedRoutes(File(TestPacks.tempDir(), "saved.json")))
        val d = app.db.route("D")!!
        val alpha = app.db.stop("rail:ALPHA")!!
        assertFalse(app.isSaved(d.key, 0, alpha.key))
        app.toggleSaved(d, 0, alpha.key, alpha.name, "Gamma Station")
        assertTrue(app.isSaved(d.key, 0, alpha.key))
        val saved = app.savedRoutes.single()
        assertEquals(listOf("D", "Gamma Station", "Alpha Station"), listOf(saved.routeName, saved.headsign, saved.stopName))
        app.toggleSaved(d, 0, alpha.key, alpha.name, "Gamma Station")
        assertTrue(app.savedRoutes.isEmpty())
    }
}

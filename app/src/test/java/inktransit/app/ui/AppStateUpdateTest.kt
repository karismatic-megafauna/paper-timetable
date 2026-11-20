package inktransit.app.ui

import inktransit.app.TestPacks
import inktransit.app.data.SavedRoutes
import inktransit.app.pack.PackStore
import inktransit.app.pack.routes
import inktransit.app.update.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppStateUpdateTest {
    // The update belongs to the app, not to the About screen: leaving that screen
    // mid-download must not lose the result or leave the app on a closed pack.
    @Test fun updateFinishesAndSwitchesPacksWithoutAScreenToReceiveIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = PackStore(TestPacks.tempDir(), "v1") { TestPacks.miniFile().inputStream() }
            val app = AppState(store, SavedRoutes(File(TestPacks.tempDir(), "saved.json")))
            val before = app.db
            val candidate = TestPacks.miniFile(name = "new.pack")
            SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("update meta set value = '2026-12-01T00:00:00Z' where key = 'built_at'")
            }
            app.startUpdate(dispatcher) { progress ->
                progress(50)
                UpdateResult.Updated(store.replaceWith(candidate).meta)
            }
            assertTrue(app.updating)
            assertEquals(4, before.routes().size) // still usable while the swap happens
            testScheduler.advanceUntilIdle()
            assertFalse(app.updating)
            assertEquals("Schedule updated.", app.updateStatus)
            assertEquals(Instant.parse("2026-12-01T00:00:00Z"), app.db.meta.builtAt)
            assertEquals(4, app.db.routes().size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun aFailedUpdateShowsItsReasonAndKeepsThePack() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val app = AppState(PackStore(TestPacks.tempDir(), "v1") { TestPacks.miniFile().inputStream() }, SavedRoutes(File(TestPacks.tempDir(), "saved.json")))
            app.startUpdate(dispatcher) { UpdateResult.Failed("No connection.") }
            testScheduler.advanceUntilIdle()
            assertEquals("No connection.", app.updateStatus)
            assertEquals(4, app.db.routes().size)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

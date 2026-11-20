package inktransit.app.pack

import android.database.sqlite.SQLiteDatabase
import inktransit.app.TestPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackStoreTest {
    private val dir = TestPacks.tempDir()
    private val bundled = TestPacks.miniFile()
    private var opens = 0
    private fun store(stamp: String = "v1", source: File = bundled) = PackStore(dir, stamp) { opens++; source.inputStream() }

    private fun rebuilt(builtAt: String, version: String = "1"): File {
        val f = TestPacks.miniFile(name = "candidate.pack")
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("update meta set value = '$builtAt' where key = 'built_at'")
            it.execSQL("update meta set value = '$version' where key = 'format_version'")
        }
        return f
    }

    @Test fun firstUseInstallsTheBundledPack() {
        val db = store().current()
        assertEquals("mini", db.meta.agencyId)
        assertTrue(File(dir, "mini.pack").exists())
    }

    @Test fun bundledPackIsNotCopiedAgainForTheSameBuild() {
        store().current().close()
        opens = 0
        store().current()
        assertEquals(0, opens)
    }

    @Test fun aNewerAppBuildWithANewerBundledPackReplacesTheInstalledOne() {
        store("v1").current().close()
        val newer = rebuilt("2026-12-01T00:00:00Z")
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), store("v2", newer).current().meta.builtAt)
    }

    @Test fun aNewerAppBuildNeverDowngradesAnUpdatedPack() {
        val s = store("v1")
        s.current()
        s.replaceWith(rebuilt("2026-12-01T00:00:00Z"))
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), store("v2").current().meta.builtAt)
    }

    @Test fun replaceWithSwapsInTheNewPack() {
        val s = store()
        s.current()
        val db = s.replaceWith(rebuilt("2026-12-01T00:00:00Z"))
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), db.meta.builtAt)
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), store().current().meta.builtAt)
    }

    // The UI is still drawing from the old pack while an update swaps the new one in.
    @Test fun theOldPackStaysReadableAfterASwap() {
        val s = store()
        val old = s.current()
        val fresh = s.replaceWith(rebuilt("2026-12-01T00:00:00Z"))
        assertEquals(4, old.routes().size)
        assertEquals(Instant.parse("2026-11-02T00:00:00Z"), old.meta.builtAt)
        old.close()
        assertEquals(4, fresh.routes().size)
    }

    @Test fun replaceWithAnUnreadablePackChangesNothing() {
        val s = store()
        val original = s.current().meta.builtAt
        assertThrows(UnsupportedPackException::class.java) { s.replaceWith(rebuilt("2026-12-01T00:00:00Z", version = "2")) }
        val junk = File(TestPacks.tempDir(), "junk.pack").apply { writeText("nope") }
        assertThrows(PackException::class.java) { s.replaceWith(junk) }
        assertEquals(original, s.current().meta.builtAt)
        assertEquals(1, s.current().routes().size.coerceAtMost(1)) // still open and readable
    }
}

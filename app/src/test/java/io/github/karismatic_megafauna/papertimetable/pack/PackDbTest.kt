package io.github.karismatic_megafauna.papertimetable.pack

import android.database.sqlite.SQLiteDatabase
import io.github.karismatic_megafauna.papertimetable.TestPacks
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
class PackDbTest {
    private fun edited(sql: String): File {
        val f = TestPacks.miniFile()
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL(sql) }
        return f
    }

    @Test fun readsMeta() = TestPacks.mini().use { db ->
        assertEquals("mini", db.meta.agencyId)
        assertEquals("Mini Transit", db.meta.agencyName)
        assertEquals(TestPacks.LA, db.meta.zone)
        assertEquals(listOf(20261102, 20261115, 20261102, 20261213), listOf(db.meta.validFrom, db.meta.validTo, db.meta.firstDay, db.meta.lastDay))
        assertEquals(Instant.parse("2026-11-02T00:00:00Z"), db.meta.builtAt)
    }

    @Test fun rejectsOtherVersion() {
        val f = edited("update meta set value = '2' where key = 'format_version'")
        val e = assertThrows(UnsupportedPackException::class.java) { PackDb.open(f) }
        assertEquals("2", e.found)
    }

    @Test fun rejectsAFileThatIsNotAPackAndLeavesItInPlace() {
        val f = File(TestPacks.tempDir(), "junk.pack").apply { writeText("this is not a database") }
        assertThrows(PackException::class.java) { PackDb.open(f) }
        assertTrue("a bad file must never be deleted by opening it", f.exists())
    }

    @Test fun effectiveDay() = TestPacks.mini().use { db ->
        assertEquals(20261104, db.effectiveDay(20261104))
        assertEquals(20261213, db.effectiveDay(20261220)) // Sunday after the pack ends
        assertEquals(20261102, db.effectiveDay(20261026)) // Monday before it starts
        assertEquals(20261209, db.effectiveDay(20270106)) // Wednesday, weeks later
    }

    @Test fun lookupsByKey() = TestPacks.mini().use { db ->
        assertEquals("D", db.route("D")!!.shortName)
        assertEquals(0, db.route("D")!!.kind)
        assertEquals("Alpha Station", db.stop("rail:ALPHA")!!.name)
        assertEquals(null, db.route("nope"))
        assertEquals(null, db.stop("bus:999"))
    }
}

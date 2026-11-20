package inktransit.app.update

import android.database.sqlite.SQLiteDatabase
import inktransit.app.TestPacks
import inktransit.app.pack.PackStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackUpdaterTest {
    private val original = Instant.parse("2026-11-02T00:00:00Z")
    private val newer = Instant.parse("2026-12-01T00:00:00Z")
    private val store = PackStore(TestPacks.tempDir(), "v1") { TestPacks.miniFile().inputStream() }
    private val workDir = TestPacks.tempDir()
    private val served = HashMap<String, ByteArray>()
    private val requested = ArrayList<String>()
    private var failAfterBytes = -1

    private fun updater(work: File = workDir) = PackUpdater("https://example.invalid/packs/", store, work) { url: URL ->
        val name = url.path.substringAfterLast('/')
        requested.add(name)
        val bytes = served[name] ?: throw IOException("404 $name")
        val limit = failAfterBytes
        if (limit < 0 || name == "index.json") ByteArrayInputStream(bytes) else interrupted(ByteArrayInputStream(bytes), limit)
    }

    private fun interrupted(source: InputStream, limit: Int) = object : FilterInputStream(source) {
        var left = limit
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) throw IOException("connection reset")
            return super.read(b, off, minOf(len, left)).also { if (it > 0) left -= it }
        }
        override fun read(): Int = throw IOException("connection reset")
    }

    private fun pack(builtAt: Instant, version: String = "1"): ByteArray {
        val f = TestPacks.miniFile()
        SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("update meta set value = '$builtAt' where key = 'built_at'")
            it.execSQL("update meta set value = '$version' where key = 'format_version'")
        }
        return f.readBytes()
    }

    private fun gzip(bytes: ByteArray) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Publishes [raw] as the agency's pack, with an index entry that can be bent for a test. */
    private fun publish(raw: ByteArray, builtAt: Instant, indexVersion: Int = 1, sha: String = sha(raw), gz: ByteArray = gzip(raw)) {
        served["mini.pack.gz"] = gz
        val entry = JSONObject().put("agency_id", "mini").put("name", "Mini Transit").put("format_version", indexVersion)
            .put("built_at", builtAt.toString()).put("valid_from", 20261102).put("valid_to", 20261231)
            .put("url", "mini.pack.gz").put("size", gz.size).put("sha256", sha)
        served["index.json"] = JSONObject().put("format", 1).put("packs", JSONArray().put(entry)).toString().toByteArray()
    }

    private fun assertUntouched() {
        assertEquals(original, store.current().meta.builtAt)
        assertEquals("work folder must be left clean", emptyList<String>(), workDir.list()?.toList().orEmpty())
    }

    @Before fun install() { store.current() }

    @Test fun upToDateWhenIndexIsNotNewer() {
        publish(pack(original), original)
        assertNull(updater().check())
        assertEquals(UpdateResult.UpToDate, updater().update {})
        assertEquals(listOf("index.json", "index.json"), requested)
    }

    @Test fun updatesAndNewPackIsCurrent() {
        publish(pack(newer), newer)
        val info = updater().check()!!
        assertEquals(newer, info.builtAt)
        assertEquals(20261231, info.validTo)
        val progress = ArrayList<Int>()
        val result = updater().update { progress.add(it) }
        assertEquals(newer, (result as UpdateResult.Updated).meta.builtAt)
        assertEquals(newer, store.current().meta.builtAt)
        assertEquals(100, progress.last())
        assertTrue("progress is reported at most once per 10%", progress.size <= 11 && progress == progress.sorted().distinct())
        assertEquals(emptyList<String>(), workDir.list()!!.toList())
    }

    // Review Focus 2: whatever goes wrong, the schedule the rider already has keeps working.
    @Test fun interruptedDownloadKeepsOldPack() {
        publish(pack(newer), newer)
        failAfterBytes = 2_000
        assertEquals(UpdateResult.Failed("Download didn't complete. Try again."), updater().update {})
        assertUntouched()
    }

    @Test fun wrongChecksumKeepsOldPack() {
        publish(pack(newer), newer, sha = "00".repeat(32))
        assertEquals(UpdateResult.Failed("The downloaded schedule was damaged. Try again."), updater().update {})
        assertUntouched()
    }

    @Test fun truncatedGzipKeepsOldPack() {
        val raw = pack(newer)
        publish(raw, newer, gz = gzip(raw).let { it.copyOf(it.size / 2) })
        assertEquals(UpdateResult.Failed("The downloaded schedule was damaged. Try again."), updater().update {})
        assertUntouched()
    }

    @Test fun newerFormatInTheIndexIsRefusedBeforeDownload() {
        publish(pack(newer, version = "2"), newer, indexVersion = 2)
        assertNull(updater().check())
        requested.clear()
        assertEquals(UpdateResult.Failed("The new schedule needs a newer version of this app."), updater().update {})
        assertEquals(listOf("index.json"), requested)
        assertUntouched()
    }

    @Test fun newerFormatInsideThePackIsRefusedAfterDownload() {
        publish(pack(newer, version = "2"), newer, indexVersion = 1)
        assertEquals(UpdateResult.Failed("The new schedule needs a newer version of this app."), updater().update {})
        assertUntouched()
    }

    @Test fun nowhereToWriteKeepsOldPack() {
        publish(pack(newer), newer)
        val notAFolder = File(TestPacks.tempDir(), "blocked").apply { writeText("x") }
        assertEquals(UpdateResult.Failed("Not enough space on the phone."), updater(notAFolder).update {})
        assertEquals(original, store.current().meta.builtAt)
    }

    // A future index may list a newer-format pack beside one this app can still read.
    @Test fun anUnsupportedEntryIsSkippedWhenAUsableOneFollows() {
        val raw = pack(newer)
        publish(raw, newer)
        val index = JSONObject(String(served["index.json"]!!))
        val usable = index.getJSONArray("packs").getJSONObject(0)
        val future = JSONObject(usable.toString()).put("format_version", 2).put("url", "mini-v2.pack.gz")
        served["index.json"] = JSONObject().put("format", 1).put("packs", JSONArray().put(future).put(usable)).toString().toByteArray()
        assertTrue(updater().update {} is UpdateResult.Updated)
        assertEquals(newer, store.current().meta.builtAt)
    }

    @Test fun noConnection() {
        val offline = PackUpdater("https://example.invalid/packs/", store, workDir) { throw UnknownHostException("example.invalid") }
        assertEquals(UpdateResult.Failed("No connection."), offline.update {})
        assertUntouched()
    }

    @Test fun anIndexWithoutThisAgencyMeansUpToDate() {
        served["index.json"] = """{"format":1,"packs":[]}""".toByteArray()
        assertEquals(UpdateResult.UpToDate, updater().update {})
    }

    @Test fun aBrokenIndexIsAFailureNotACrash() {
        served["index.json"] = "<html>Not Found</html>".toByteArray()
        assertEquals(UpdateResult.Failed("Download didn't complete. Try again."), updater().update {})
        assertUntouched()
    }
}

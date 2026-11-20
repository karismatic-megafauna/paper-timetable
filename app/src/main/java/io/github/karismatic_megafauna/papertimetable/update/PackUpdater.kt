package io.github.karismatic_megafauna.papertimetable.update

import io.github.karismatic_megafauna.papertimetable.pack.PackDb
import io.github.karismatic_megafauna.papertimetable.pack.PackException
import io.github.karismatic_megafauna.papertimetable.pack.PackMeta
import io.github.karismatic_megafauna.papertimetable.pack.PackStore
import io.github.karismatic_megafauna.papertimetable.pack.UnsupportedPackException
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

data class UpdateInfo(val url: URL, val size: Long, val sha256: String, val builtAt: Instant, val validTo: Int)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Updated(val meta: PackMeta) : UpdateResult
    /** [reason] is written for the rider and shown as is. */
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Fetches a newer schedule pack when the rider asks for one. It never runs on
 * its own. Whatever goes wrong, the installed pack is left exactly as it was:
 * the download is verified in a work folder and only swapped in once it opens.
 *
 * The index it reads is written by the converter (writer.py, write_index).
 */
class PackUpdater(
    private val baseUrl: String,
    private val store: PackStore,
    private val workDir: File,
    private val open: (URL) -> InputStream = { url ->
        url.openConnection().apply { connectTimeout = 15_000; readTimeout = 30_000 }.getInputStream()
    },
) {
    private sealed interface Lookup {
        data object Nothing : Lookup
        data object NeedsNewerApp : Lookup
        data class Newer(val info: UpdateInfo) : Lookup
    }

    private class Damaged : IOException("downloaded pack failed verification")
    private class NoRoom(cause: IOException) : IOException(cause)

    /** The newer pack on offer for the installed agency, or null if there is none this app can use. */
    fun check(): UpdateInfo? = (lookup() as? Lookup.Newer)?.info

    private fun lookup(): Lookup {
        val installed = store.current().meta
        val text = open(URL(baseUrl + "index.json")).use { it.readBytes().toString(Charsets.UTF_8) }
        val packs = JSONObject(text).getJSONArray("packs")
        var onlyUnreadable = false
        for (i in 0 until packs.length()) {
            val entry = packs.getJSONObject(i)
            if (entry.getString("agency_id") != installed.agencyId) continue
            val builtAt = Instant.parse(entry.getString("built_at"))
            if (!builtAt.isAfter(installed.builtAt)) continue
            // A later index may list packs in a newer format beside one this app can still read.
            if (entry.getInt("format_version").toString() != PackDb.FORMAT) {
                onlyUnreadable = true
                continue
            }
            return Lookup.Newer(UpdateInfo(URL(baseUrl + entry.getString("url")), entry.getLong("size"), entry.getString("sha256"), builtAt, entry.getInt("valid_to")))
        }
        return if (onlyUnreadable) Lookup.NeedsNewerApp else Lookup.Nothing
    }

    /** [progress] receives a percentage, at most once per ten points. */
    fun update(progress: (Int) -> Unit): UpdateResult {
        val download = File(workDir, "download.pack")
        try {
            val info = when (val found = lookup()) {
                Lookup.Nothing -> return UpdateResult.UpToDate
                Lookup.NeedsNewerApp -> return UpdateResult.Failed(NEEDS_NEWER_APP)
                is Lookup.Newer -> found.info
            }
            fetch(info, download, progress)
            return UpdateResult.Updated(store.replaceWith(download).meta)
        } catch (e: UnsupportedPackException) {
            return UpdateResult.Failed(NEEDS_NEWER_APP)
        } catch (e: PackException) {
            return UpdateResult.Failed(DAMAGED)
        } catch (e: NoRoom) {
            return UpdateResult.Failed("Not enough space on the phone.")
        } catch (e: Damaged) {
            return UpdateResult.Failed(DAMAGED)
        } catch (e: ZipException) {
            return UpdateResult.Failed(DAMAGED)
        } catch (e: java.io.EOFException) { // gzip stream ended early
            return UpdateResult.Failed(DAMAGED)
        } catch (e: UnknownHostException) {
            return UpdateResult.Failed(NO_CONNECTION)
        } catch (e: ConnectException) {
            return UpdateResult.Failed(NO_CONNECTION)
        } catch (e: SocketTimeoutException) {
            return UpdateResult.Failed(NO_CONNECTION)
        } catch (e: IOException) {
            return UpdateResult.Failed(INCOMPLETE)
        } catch (e: JSONException) { // the index was not an index (a captive portal page, say)
            return UpdateResult.Failed(INCOMPLETE)
        } catch (e: RuntimeException) { // malformed dates or addresses in the index
            return UpdateResult.Failed(INCOMPLETE)
        } finally {
            download.delete()
        }
    }

    private fun fetch(info: UpdateInfo, target: File, progress: (Int) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        var compressed = 0L
        var reported = -1
        val counting = object : java.io.FilterInputStream(open(info.url)) {
            override fun read(): Int = super.read().also { if (it >= 0) compressed++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) compressed += it }
        }
        counting.use { raw ->
            val out = try {
                workDir.mkdirs()
                target.outputStream()
            } catch (e: IOException) {
                throw NoRoom(e)
            }
            out.use { file ->
                val unzipped = GZIPInputStream(raw)
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = unzipped.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    try {
                        file.write(buffer, 0, n)
                    } catch (e: IOException) {
                        throw NoRoom(e)
                    }
                    val tenth = if (info.size > 0) (compressed * 10 / info.size).toInt().coerceIn(0, 9) else 0
                    if (tenth > reported) {
                        reported = tenth
                        progress(tenth * 10)
                    }
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(info.sha256, ignoreCase = true)) throw Damaged()
        progress(100)
    }

    private companion object {
        const val NO_CONNECTION = "No connection."
        const val INCOMPLETE = "Download didn't complete. Try again."
        const val DAMAGED = "The downloaded schedule was damaged. Try again."
        const val NEEDS_NEWER_APP = "The new schedule needs a newer version of this app."
    }
}

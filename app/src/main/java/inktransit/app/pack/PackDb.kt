package inktransit.app.pack

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import inktransit.app.time.dateOf
import inktransit.app.time.dayOf
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The file is not a usable schedule pack. */
open class PackException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The pack was made for a newer (or older) version of the app. */
class UnsupportedPackException(val found: String?) : PackException("pack format $found is not supported")

data class PackMeta(
    val agencyId: String,
    val agencyName: String,
    val zone: ZoneId,
    /** First and last day (YYYYMMDD) for which every line's schedule is as published. */
    val validFrom: Int,
    val validTo: Int,
    /** First and last day held at all; days after [validTo] are partly carried forward. */
    val firstDay: Int,
    val lastDay: Int,
    val builtAt: Instant,
)

/**
 * One agency's schedule, read-only. The schema is defined by the converter
 * (converter/src/packbuilder/writer.py); [FORMAT] must match its FORMAT_VERSION.
 */
class PackDb private constructor(internal val sql: SQLiteDatabase, val meta: PackMeta, val file: File) : Closeable {

    /**
     * The day whose schedule stands in for [day]: itself when the pack holds
     * it, otherwise the nearest held day with the same weekday.
     */
    fun effectiveDay(day: Int): Int? {
        if (day in meta.firstDay..meta.lastDay) return day
        val target = dateOf(day)
        val first = dateOf(meta.firstDay)
        val last = dateOf(meta.lastDay)
        val candidate = if (day > meta.lastDay) {
            last.minusDays(((last.dayOfWeek.value - target.dayOfWeek.value + 7) % 7).toLong())
        } else {
            first.plusDays(((target.dayOfWeek.value - first.dayOfWeek.value + 7) % 7).toLong())
        }
        return if (candidate.isBefore(first) || candidate.isAfter(last)) null else dayOf(candidate)
    }

    override fun close() = sql.close()

    companion object {
        const val FORMAT = "1"

        // Android's default handler deletes a database it thinks is corrupt.
        // A pack that fails to open must be left exactly where it is.
        private val leaveFileAlone = DatabaseErrorHandler { }

        fun open(file: File): PackDb {
            val db = try {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY, leaveFileAlone)
            } catch (e: SQLiteException) {
                throw PackException("${file.name} could not be opened", e)
            }
            try {
                val values = HashMap<String, String>()
                db.rawQuery("SELECT key, value FROM meta", null).use { c ->
                    while (c.moveToNext()) values[c.getString(0)] = c.getString(1)
                }
                if (values["format_version"] != FORMAT) throw UnsupportedPackException(values["format_version"])
                fun need(key: String) = values[key] ?: throw PackException("${file.name} has no $key")
                val meta = PackMeta(
                    need("agency_id"), need("agency_name"), ZoneId.of(need("timezone")),
                    need("valid_from").toInt(), need("valid_to").toInt(), need("first_day").toInt(), need("last_day").toInt(),
                    Instant.parse(need("built_at")).truncatedTo(ChronoUnit.SECONDS),
                )
                return PackDb(db, meta, file)
            } catch (e: PackException) {
                db.close()
                throw e
            } catch (e: RuntimeException) { // SQLiteException, bad numbers, unknown zone
                db.close()
                throw PackException("${file.name} is not a schedule pack", e)
            }
        }
    }
}

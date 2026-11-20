package inktransit.app.pack

import inktransit.app.time.dayOf
import inktransit.app.time.toLocal
import java.time.LocalDateTime

data class RouteRow(val id: Int, val key: String, val shortName: String, val longName: String, val kind: Int)

data class StopRow(val id: Int, val key: String, val placeId: Int, val name: String)

/**
 * One departure. [otherHeadsign] is set only when this trip does not go where
 * the direction usually goes (a short turn or a branch).
 */
data class Departure(
    val routeId: Int,
    val dir: Int,
    val stopId: Int,
    val time: LocalDateTime,
    val serviceDay: Int,
    val otherHeadsign: String?,
)

data class BoardRow(
    val route: RouteRow,
    val dir: Int,
    val headsign: String,
    val stopId: Int,
    val stopName: String,
    val departures: List<Departure>,
)

private const val ROUTE_COLUMNS = "id, key, short_name, long_name, kind"

private fun android.database.Cursor.routeRow(offset: Int = 0) =
    RouteRow(getInt(offset), getString(offset + 1), getString(offset + 2), getString(offset + 3), getInt(offset + 4))

fun PackDb.route(key: String): RouteRow? =
    sql.rawQuery("SELECT $ROUTE_COLUMNS FROM route WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.routeRow() else null }

fun PackDb.routes(): List<RouteRow> =
    sql.rawQuery("SELECT $ROUTE_COLUMNS FROM route ORDER BY sort", null).use { c ->
        buildList { while (c.moveToNext()) add(c.routeRow()) }
    }

fun PackDb.stop(key: String): StopRow? =
    sql.rawQuery("SELECT id, key, place_id, name FROM stop WHERE key = ?", arrayOf(key)).use {
        if (it.moveToFirst()) StopRow(it.getInt(0), it.getString(1), it.getInt(2), it.getString(3)) else null
    }

private fun PackDb.directionHeadsign(routeId: Int, dir: Int): String =
    sql.rawQuery("SELECT headsign FROM direction WHERE route_id = ? AND dir = ?", arrayOf("$routeId", "$dir")).use {
        if (it.moveToFirst()) it.getString(0) else ""
    }

/**
 * Every departure of [routeId]/[dir] from [stopId] on service day [day], in
 * time order. A trip's final stop is never a departure: nobody can board there.
 */
fun PackDb.dayDepartures(stopId: Int, routeId: Int, dir: Int, day: Int): List<Departure> {
    val held = effectiveDay(day) ?: return emptyList()
    val usual = directionHeadsign(routeId, dir)
    val out = ArrayList<Departure>()
    sql.rawQuery(
        """SELECT ps.seq, t.start, t.deltas, p.headsign
           FROM pattern_stop ps
           JOIN pattern p ON p.id = ps.pattern_id
           JOIN trip t ON t.pattern_id = p.id
           JOIN service_day sd ON sd.service_id = t.service_id AND sd.day = ?
           WHERE ps.stop_id = ? AND p.route_id = ? AND p.dir = ? AND ps.seq < p.stop_count - 1""",
        arrayOf("$held", "$stopId", "$routeId", "$dir"),
    ).use { c ->
        while (c.moveToNext()) {
            val sec = c.getInt(1) + Varint.offsetAt(c.getBlob(2), c.getInt(0))
            val headsign = c.getString(3)
            out.add(Departure(routeId, dir, stopId, toLocal(meta.zone, day, sec), day, headsign.takeIf { it != usual }))
        }
    }
    out.sortBy { it.time }
    return out
}

/**
 * The next [limit] departures at or after [from]. Looks at the previous
 * service day first (its after-midnight trips), then forward up to a week.
 */
fun PackDb.nextDepartures(stopId: Int, routeId: Int, dir: Int, from: LocalDateTime, limit: Int): List<Departure> {
    val found = ArrayList<Departure>()
    var date = from.toLocalDate().minusDays(1)
    val lastDate = from.toLocalDate().plusDays(7)
    while (!date.isAfter(lastDate)) {
        dayDepartures(stopId, routeId, dir, dayOf(date)).filterTo(found) { !it.time.isBefore(from) }
        found.sortBy { it.time }
        // A service day can run past midnight, so the next day may still hold
        // something earlier than what we have; stop only once that is impossible.
        val nextDayStart = date.plusDays(1).atStartOfDay()
        if (found.size >= limit && found[limit - 1].time.isBefore(nextDayStart)) break
        date = date.plusDays(1)
    }
    return found.take(limit)
}

/**
 * Everything that leaves from a place: one row per line, direction and stop,
 * with its next departures. A row with nothing in the coming week is kept, with
 * no departures, so the rider can see the line stops here but is not running.
 */
fun PackDb.placeBoard(placeId: Int, from: LocalDateTime, perRow: Int): List<BoardRow> {
    data class Key(val route: RouteRow, val dir: Int, val headsign: String, val stopId: Int, val stopName: String)
    val keys = ArrayList<Key>()
    sql.rawQuery(
        """SELECT DISTINCT r.id, r.key, r.short_name, r.long_name, r.kind, p.dir, d.headsign, s.id, s.name
           FROM stop s
           JOIN pattern_stop ps ON ps.stop_id = s.id
           JOIN pattern p ON p.id = ps.pattern_id AND ps.seq < p.stop_count - 1
           JOIN route r ON r.id = p.route_id
           JOIN direction d ON d.route_id = p.route_id AND d.dir = p.dir
           WHERE s.place_id = ?
           ORDER BY r.sort, p.dir, s.id""",
        arrayOf("$placeId"),
    ).use { c ->
        while (c.moveToNext()) keys.add(Key(c.routeRow(), c.getInt(5), c.getString(6), c.getInt(7), c.getString(8)))
    }
    return keys.map { BoardRow(it.route, it.dir, it.headsign, it.stopId, it.stopName, nextDepartures(it.stopId, it.route.id, it.dir, from, perRow)) }
}

package io.github.karismatic_megafauna.papertimetable.pack

import io.github.karismatic_megafauna.papertimetable.time.dayOf
import io.github.karismatic_megafauna.papertimetable.time.toLocal
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
    val stopKey: String,
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
    data class Key(val route: RouteRow, val dir: Int, val headsign: String, val stopId: Int, val stopKey: String, val stopName: String)
    val keys = ArrayList<Key>()
    sql.rawQuery(
        """SELECT DISTINCT r.id, r.key, r.short_name, r.long_name, r.kind, p.dir, d.headsign, s.id, s.key, s.name
           FROM stop s
           JOIN pattern_stop ps ON ps.stop_id = s.id
           JOIN pattern p ON p.id = ps.pattern_id AND ps.seq < p.stop_count - 1
           JOIN route r ON r.id = p.route_id
           JOIN direction d ON d.route_id = p.route_id AND d.dir = p.dir
           WHERE s.place_id = ?
           ORDER BY r.sort, p.dir, s.id""",
        arrayOf("$placeId"),
    ).use { c ->
        while (c.moveToNext()) keys.add(Key(c.routeRow(), c.getInt(5), c.getString(6), c.getInt(7), c.getString(8), c.getString(9)))
    }
    return keys.map { BoardRow(it.route, it.dir, it.headsign, it.stopId, it.stopKey, it.stopName, nextDepartures(it.stopId, it.route.id, it.dir, from, perRow)) }
}

data class DirectionRow(val routeId: Int, val dir: Int, val headsign: String)

data class PlaceRow(val id: Int, val name: String, val lat: Double, val lon: Double)

/** [band] indexes FrequencyWords.BAND_NAMES. [headwayMin] is null when the band has fewer than two trips. */
data class FreqRow(val dir: Int, val band: Int, val trips: Int, val headwayMin: Int?)

/** First and last departure of a direction, in seconds after the service day's start. */
data class SpanRow(val dir: Int, val first: Int, val last: Int)

/** 0 Monday–Friday, 1 Saturday, 2 Sunday. */
fun dayTypeOf(day: Int): Int = when (io.github.karismatic_megafauna.papertimetable.time.dateOf(day).dayOfWeek.value) {
    6 -> 1
    7 -> 2
    else -> 0
}

fun PackDb.directions(routeId: Int): List<DirectionRow> =
    sql.rawQuery("SELECT dir, headsign FROM direction WHERE route_id = ? ORDER BY dir", arrayOf("$routeId")).use { c ->
        buildList { while (c.moveToNext()) add(DirectionRow(routeId, c.getInt(0), c.getString(1))) }
    }

fun PackDb.directionStops(routeId: Int, dir: Int): List<StopRow> =
    sql.rawQuery(
        """SELECT s.id, s.key, s.place_id, s.name FROM direction_stop ds JOIN stop s ON s.id = ds.stop_id
           WHERE ds.route_id = ? AND ds.dir = ? ORDER BY ds.seq""",
        arrayOf("$routeId", "$dir"),
    ).use { c -> buildList { while (c.moveToNext()) add(StopRow(c.getInt(0), c.getString(1), c.getInt(2), c.getString(3))) } }

fun PackDb.frequency(routeId: Int, dayType: Int): List<FreqRow> =
    sql.rawQuery(
        "SELECT dir, band, trips, headway_min FROM frequency WHERE route_id = ? AND day_type = ? ORDER BY dir, band",
        arrayOf("$routeId", "$dayType"),
    ).use { c -> buildList { while (c.moveToNext()) add(FreqRow(c.getInt(0), c.getInt(1), c.getInt(2), if (c.isNull(3)) null else c.getInt(3))) } }

fun PackDb.spans(routeId: Int, dayType: Int): List<SpanRow> =
    sql.rawQuery(
        "SELECT dir, first, last FROM span WHERE route_id = ? AND day_type = ? ORDER BY dir",
        arrayOf("$routeId", "$dayType"),
    ).use { c -> buildList { while (c.moveToNext()) add(SpanRow(c.getInt(0), c.getInt(1), c.getInt(2))) } }

/** Route id -> frequency phrase, for the whole lines list in one query. */
fun PackDb.allFrequencyTags(dayType: Int): Map<Int, String> {
    val rows = HashMap<Int, MutableList<FreqRow>>()
    sql.rawQuery("SELECT route_id, dir, band, trips, headway_min FROM frequency WHERE day_type = ?", arrayOf("$dayType")).use { c ->
        while (c.moveToNext()) {
            rows.getOrPut(c.getInt(0)) { ArrayList() }.add(FreqRow(c.getInt(1), c.getInt(2), c.getInt(3), if (c.isNull(4)) null else c.getInt(4)))
        }
    }
    return rows.mapValues { io.github.karismatic_megafauna.papertimetable.text.FrequencyWords.tag(it.value) }
}

fun PackDb.place(id: Int): PlaceRow =
    sql.rawQuery("SELECT id, name, lat, lon FROM place WHERE id = ?", arrayOf("$id")).use {
        check(it.moveToFirst()) { "no place $id" }
        PlaceRow(it.getInt(0), it.getString(1), it.getDouble(2), it.getDouble(3))
    }

private fun searchable(text: String): String = text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

/**
 * Places whose name contains every word typed, in any order ("vermont wilshire"
 * finds "Wilshire / Vermont Station"). Names starting with the first word come first.
 */
/** The lines that can be boarded at each of [placeIds], rail first, then buses in line order. */
fun PackDb.placeLines(placeIds: List<Int>): Map<Int, List<String>> {
    if (placeIds.isEmpty()) return emptyMap()
    val out = LinkedHashMap<Int, MutableList<String>>()
    sql.rawQuery(
        """SELECT DISTINCT s.place_id, r.short_name, r.sort
           FROM stop s
           JOIN pattern_stop ps ON ps.stop_id = s.id
           JOIN pattern p ON p.id = ps.pattern_id AND ps.seq < p.stop_count - 1
           JOIN route r ON r.id = p.route_id
           WHERE s.place_id IN (${placeIds.joinToString(",")})
           ORDER BY r.sort""",
        null,
    ).use { c -> while (c.moveToNext()) out.getOrPut(c.getInt(0)) { ArrayList() }.add(c.getString(1)) }
    return out
}

fun PackDb.searchPlaces(q: String, limit: Int): List<PlaceRow> {
    val words = searchable(q).split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val hits = ArrayList<Pair<PlaceRow, String>>()
    sql.rawQuery("SELECT id, name, lat, lon FROM place", null).use { c ->
        while (c.moveToNext()) {
            val name = searchable(c.getString(1))
            if (words.all { name.contains(it) }) hits.add(PlaceRow(c.getInt(0), c.getString(1), c.getDouble(2), c.getDouble(3)) to name)
        }
    }
    return hits.sortedWith(compareBy({ !it.second.startsWith(words[0]) }, { it.first.name })).take(limit).map { it.first }
}

fun PackDb.routeById(id: Int): RouteRow? =
    sql.rawQuery("SELECT id, key, short_name, long_name, kind FROM route WHERE id = ?", arrayOf("$id")).use {
        if (it.moveToFirst()) RouteRow(it.getInt(0), it.getString(1), it.getString(2), it.getString(3), it.getInt(4)) else null
    }

fun PackDb.stopById(id: Int): StopRow? =
    sql.rawQuery("SELECT id, key, place_id, name FROM stop WHERE id = ?", arrayOf("$id")).use {
        if (it.moveToFirst()) StopRow(it.getInt(0), it.getString(1), it.getInt(2), it.getString(3)) else null
    }

private fun metresBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val a = Math.pow(Math.sin((p2 - p1) / 2), 2.0) + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2.0)
    return 2 * 6_371_000 * Math.asin(Math.sqrt(a))
}

/**
 * The [limit] closest places with their distance in metres. Searches a small
 * box first and widens it; gives up beyond about 50 km, so someone far from
 * the network gets an empty list instead of a meaningless one.
 */
fun PackDb.nearestPlaces(lat: Double, lon: Double, limit: Int): List<Pair<PlaceRow, Int>> {
    var box = 0.01
    var found: List<Pair<PlaceRow, Int>> = emptyList()
    while (box <= 0.5) {
        val args = arrayOf("${lat - box}", "${lat + box}", "${lon - box * 1.3}", "${lon + box * 1.3}")
        found = sql.rawQuery("SELECT id, name, lat, lon FROM place WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?", args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val place = PlaceRow(c.getInt(0), c.getString(1), c.getDouble(2), c.getDouble(3))
                    add(place to metresBetween(lat, lon, place.lat, place.lon).toInt())
                }
            }
        }
        if (found.size >= limit) break
        box *= 2
    }
    return found.sortedBy { it.second }.take(limit)
}

/** "350 ft" / "0.4 mi" where people use miles, "120 m" / "1.2 km" elsewhere. Rounded: a GPS fix is not that exact. */
fun formatDistance(m: Int, locale: java.util.Locale): String {
    if (locale.country == "US") {
        val feet = m * 3.28084
        return if (feet < 1000) "${maxOf(50, Math.round(feet / 50).toInt() * 50)} ft" else String.format(java.util.Locale.ROOT, "%.1f mi", feet / 5280)
    }
    return if (m < 1000) "${maxOf(10, Math.round(m / 10.0).toInt() * 10)} m" else String.format(java.util.Locale.ROOT, "%.1f km", m / 1000.0)
}

/** True when [routeId]/[dir] has any trip that can be boarded at [stopId] (calling there, and not only to terminate). */
fun PackDb.servesStop(stopId: Int, routeId: Int, dir: Int): Boolean =
    sql.rawQuery(
        """SELECT 1 FROM pattern_stop ps JOIN pattern p ON p.id = ps.pattern_id
           WHERE ps.stop_id = ? AND p.route_id = ? AND p.dir = ? AND ps.seq < p.stop_count - 1 LIMIT 1""",
        arrayOf("$stopId", "$routeId", "$dir"),
    ).use { it.moveToFirst() }

/**
 * The service day a rider at [at] is in. Just after midnight that is still
 * yesterday's, for as long as yesterday's last departures have not left: at
 * 12:30a the trains about to go belong to the night before.
 */
fun PackDb.serviceDayFor(stopId: Int, routeId: Int, dir: Int, at: LocalDateTime): Int {
    val previous = dayOf(at.toLocalDate().minusDays(1))
    return if (dayDepartures(stopId, routeId, dir, previous).any { !it.time.isBefore(at) }) previous else dayOf(at.toLocalDate())
}

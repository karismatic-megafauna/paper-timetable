package inktransit.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * A route the rider saved: a line, a direction and the stop they board at.
 * It is identified by keys that survive schedule updates (the line's short
 * name and the stop's published id), never by a pack's internal numbers. The
 * names are kept so the route can still be shown if an update removes it.
 */
data class SavedRoute(
    val agencyId: String,
    val routeKey: String,
    val dir: Int,
    val stopKey: String,
    val routeName: String,
    val headsign: String,
    val stopName: String,
) {
    fun sameRouteAs(agencyId: String, routeKey: String, dir: Int, stopKey: String) =
        this.agencyId == agencyId && this.routeKey == routeKey && this.dir == dir && this.stopKey == stopKey
}

class SavedRoutes(private val file: File) {
    private var routes: MutableList<SavedRoute> = load()

    fun all(): List<SavedRoute> = routes.toList()

    fun contains(agencyId: String, routeKey: String, dir: Int, stopKey: String) =
        routes.any { it.sameRouteAs(agencyId, routeKey, dir, stopKey) }

    fun add(r: SavedRoute) {
        if (contains(r.agencyId, r.routeKey, r.dir, r.stopKey)) return
        routes.add(r)
        save()
    }

    fun remove(r: SavedRoute) {
        if (routes.removeAll { it.sameRouteAs(r.agencyId, r.routeKey, r.dir, r.stopKey) }) save()
    }

    fun move(r: SavedRoute, up: Boolean) {
        val from = routes.indexOfFirst { it.sameRouteAs(r.agencyId, r.routeKey, r.dir, r.stopKey) }
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in routes.indices) return
        routes.add(to, routes.removeAt(from))
        save()
    }

    private fun load(): MutableList<SavedRoute> {
        if (!file.exists()) return mutableListOf()
        return try {
            val array = JSONArray(file.readText())
            MutableList(array.length()) { i ->
                val o = array.getJSONObject(i)
                SavedRoute(
                    o.getString("agency"), o.getString("route"), o.getInt("dir"), o.getString("stop"),
                    o.getString("routeName"), o.getString("headsign"), o.getString("stopName"),
                )
            }
        } catch (e: JSONException) {
            // Keep the unreadable file for recovery instead of silently discarding someone's routes.
            file.copyTo(File(file.parentFile, "saved.bad"), overwrite = true)
            mutableListOf()
        }
    }

    private fun save() {
        val array = JSONArray()
        routes.forEach {
            array.put(
                JSONObject().put("agency", it.agencyId).put("route", it.routeKey).put("dir", it.dir).put("stop", it.stopKey)
                    .put("routeName", it.routeName).put("headsign", it.headsign).put("stopName", it.stopName),
            )
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}

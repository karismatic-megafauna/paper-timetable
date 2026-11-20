package inktransit.app.ui

import androidx.compose.runtime.compositionLocalOf
import inktransit.app.pack.PackDb
import inktransit.app.pack.directions
import inktransit.app.pack.place
import inktransit.app.pack.routeById

/**
 * The app has no back button of its own; the phone's back key steps out. So
 * every screen says where it sits: one small line naming the screens behind it.
 */
val LocalCrumb = compositionLocalOf<String?> { null }

private fun PackDb.lineLabel(routeId: Int): String {
    val route = routeById(routeId) ?: return "Line"
    return if (route.kind == 0) "${route.shortName} Line" else "Line ${route.shortName}"
}

private fun label(db: PackDb, screen: Screen): String = when (screen) {
    Screen.MyRoutes -> "Home"
    Screen.Lines -> "Lines"
    is Screen.LineOverview -> db.lineLabel(screen.routeId)
    is Screen.DirectionStops -> "→ " + db.directions(screen.routeId).firstOrNull { it.dir == screen.dir }?.headsign.orEmpty()
    is Screen.StopView -> db.place(screen.placeId).name
    is Screen.DayTimes -> "Times"
    Screen.WhenPicker -> "When"
    Screen.Search -> "Search"
    Screen.NearMe -> "Near me"
    Screen.About -> "About"
}

/** A name for each screen behind the one on show, oldest first. Empty on the home screen. */
fun crumbLabels(db: PackDb, trail: List<Screen>): List<String> = trail.dropLast(1).map { label(db, it) }

/** The trail as one line. When it is too long the oldest steps go first; the nearest one always stays. */
fun crumbText(labels: List<String>, maxChars: Int = 38): String? {
    if (labels.isEmpty()) return null
    val full = labels.joinToString(" › ")
    if (full.length <= maxChars) return full
    var kept = labels.drop(1)
    while (kept.size > 1 && ("… › " + kept.joinToString(" › ")).length > maxChars) kept = kept.drop(1)
    return "… › " + kept.joinToString(" › ")
}

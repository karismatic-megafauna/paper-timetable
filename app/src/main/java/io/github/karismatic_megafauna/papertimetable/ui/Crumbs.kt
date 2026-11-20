package io.github.karismatic_megafauna.papertimetable.ui

import androidx.compose.runtime.compositionLocalOf
import io.github.karismatic_megafauna.papertimetable.pack.PackDb
import io.github.karismatic_megafauna.papertimetable.pack.directions
import io.github.karismatic_megafauna.papertimetable.pack.place
import io.github.karismatic_megafauna.papertimetable.pack.routeById

/** The back button a screen shows above its title: where it leads, and how to go there. */
class Back(val label: String, val go: () -> Unit)

/**
 * A plain "Back" says nothing about where you are, so the button names the
 * screen it returns to. The phone's back key does the same thing.
 */
val LocalBack = compositionLocalOf<Back?> { null }

private fun PackDb.lineLabel(routeId: Int): String {
    val route = routeById(routeId) ?: return "Line"
    return if (route.kind == 0) "${route.shortName} Line" else "Line ${route.shortName}"
}

private fun label(db: PackDb, screen: Screen): String = when (screen) {
    Screen.MyRoutes -> "Home"
    Screen.Lines -> "Lines"
    is Screen.LineOverview -> db.lineLabel(screen.routeId)
    is Screen.DirectionStops -> "${db.routeById(screen.routeId)?.shortName.orEmpty()} → ${db.directions(screen.routeId).firstOrNull { it.dir == screen.dir }?.headsign.orEmpty()}"
    is Screen.StopView -> db.place(screen.placeId).name
    is Screen.DayTimes -> "Times"
    Screen.WhenPicker -> "When"
    Screen.Search -> "Search"
    Screen.NearMe -> "Near me"
    Screen.About -> "About"
}

/** A name for each screen behind the one on show, oldest first. Empty on the home screen. */
fun crumbLabels(db: PackDb, trail: List<Screen>): List<String> = trail.dropLast(1).map { label(db, it) }

/** What the back button says: the screen it returns to. Null on the home screen, which has none. */
fun backLabel(db: PackDb, trail: List<Screen>): String? = crumbLabels(db, trail).lastOrNull()?.let { "‹ $it" }

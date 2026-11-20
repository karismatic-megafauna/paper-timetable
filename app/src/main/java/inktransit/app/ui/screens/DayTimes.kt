package inktransit.app.ui.screens

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inktransit.app.pack.dayDepartures
import inktransit.app.pack.directions
import inktransit.app.pack.routeById
import inktransit.app.pack.serviceDayFor
import inktransit.app.time.dateOf
import inktransit.app.pack.stopById
import inktransit.app.time.dayOf
import inktransit.app.ui.AppState
import inktransit.app.ui.Ink
import inktransit.app.ui.InkText
import inktransit.app.ui.PagedList
import inktransit.app.ui.ScreenFrame
import java.time.LocalDateTime

private const val COLUMNS = 4

/** The row of a time grid that holds the first departure at or after [at]; the last row if all are earlier. */
fun gridStartRow(times: List<LocalDateTime>, at: LocalDateTime, columns: Int): Int {
    if (times.isEmpty()) return 0
    val index = times.indexOfFirst { !it.isBefore(at) }
    return (if (index < 0) times.lastIndex else index) / columns
}

/** One line, one direction, one stop: every departure of the chosen day, a screenful at a time. */
@Composable
fun DayTimesScreen(app: AppState, stopId: Int, routeId: Int, dir: Int) {
    val at = app.viewTime
    val day = remember(app.db, stopId, routeId, dir, at) { app.db.serviceDayFor(stopId, routeId, dir, at) }
    val route = remember(app.db, routeId) { app.db.routeById(routeId) }
    val stop = remember(app.db, stopId) { app.db.stopById(stopId) }
    val headsign = remember(app.db, routeId, dir) { app.db.directions(routeId).firstOrNull { it.dir == dir }?.headsign.orEmpty() }
    val departures = remember(app.db, stopId, routeId, dir, day) { app.db.dayDepartures(stopId, routeId, dir, day) }
    val others = departures.mapNotNull { it.otherHeadsign }.distinct()
    ScreenFrame("${route?.shortName.orEmpty()} → $headsign") {
        InkText("${stop?.name.orEmpty()} · ${if (day == dayOf(app.now.toLocalDate().minusDays(1))) "Last night" else app.clock.dayLabel(dateOf(day), app.now.toLocalDate()) ?: "Today"}", Ink.Small, Modifier.padding(Ink.Gutter, 4.dp), maxLines = 1)
        WhenButton(app, Modifier.fillMaxWidth())
        StaleLine(app, at)
        PagedList(
            departures.chunked(COLUMNS), rowDp = 42f, modifier = Modifier.weight(1f), resetKey = day, divided = false,
            startIndex = gridStartRow(departures.map { it.time }, at, COLUMNS),
            empty = { InkText("No service this day.", Ink.Row, Modifier.padding(Ink.Gutter, 20.dp)) },
        ) { line ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Ink.Gutter), verticalAlignment = Alignment.CenterVertically) {
                line.forEach { InkText(app.clock.format(it.time) + if (it.otherHeadsign != null) "*" else "", Ink.Row, Modifier.weight(1f), maxLines = 1) }
                repeat(COLUMNS - line.size) { InkText("", Ink.Row, Modifier.weight(1f)) }
            }
        }
        others.forEach { InkText("* only to $it", Ink.Small, Modifier.padding(Ink.Gutter, 2.dp), maxLines = 1) }
    }
}

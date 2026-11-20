package inktransit.app.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inktransit.app.data.SavedRoute
import inktransit.app.pack.BoardRow
import inktransit.app.pack.PackDb
import inktransit.app.pack.PackMeta
import inktransit.app.pack.place
import inktransit.app.pack.placeBoard
import inktransit.app.time.ServiceClock
import inktransit.app.time.dayOf
import inktransit.app.ui.AppState
import inktransit.app.ui.Ink
import inktransit.app.ui.InkButton
import inktransit.app.ui.InkText
import inktransit.app.ui.PagedList
import inktransit.app.ui.Rule
import inktransit.app.ui.Screen
import inktransit.app.ui.ScreenFrame
import inktransit.app.ui.WhenChoice
import inktransit.app.ui.tap
import java.time.LocalDateTime

/** True when the times on screen are not backed by a schedule the agency has published for that day. */
fun isBeyondPublished(meta: PackMeta, viewedDay: Int, today: Int): Boolean = today > meta.validTo || viewedDay > meta.validTo

fun staleText(meta: PackMeta, viewedDay: Int, today: Int): String? = when {
    today > meta.validTo -> "Schedule may be out of date"
    viewedDay > meta.validTo -> "Beyond published schedule — times may change"
    else -> null
}

/** The one-line warning shown under the title whenever the times might not be right. */
@Composable
fun StaleLine(app: AppState, viewed: LocalDateTime) {
    staleText(app.db.meta, dayOf(viewed.toLocalDate()), dayOf(app.now.toLocalDate()))?.let {
        InkText(it, Ink.Small, Modifier.fillMaxWidth().padding(Ink.Gutter, 3.dp), maxLines = 2)
        Rule()
    }
}

fun whenLabel(choice: WhenChoice, clock: ServiceClock, now: LocalDateTime): String = when (choice) {
    WhenChoice.Now -> "When: Now"
    is WhenChoice.At -> "When: ${clock.dayLabel(choice.time, now) ?: "Today"}, ${clock.format(choice.time)}"
}

/** Opens the picker. Drawn white-on-black while a time is chosen, so a future schedule is never mistaken for now. */
@Composable
fun WhenButton(app: AppState, modifier: Modifier = Modifier) {
    InkButton(whenLabel(app.whenChoice, app.clock, app.now), modifier, inverted = app.whenChoice != WhenChoice.Now, style = Ink.Small) {
        app.nav.push(Screen.WhenPicker)
    }
    Rule()
}

/** Filled when the route is on the home screen; tapping adds or removes it. */
@Composable
fun Star(saved: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.tap(onClick = onClick), contentAlignment = Alignment.Center) {
        InkText(if (saved) "★" else "☆", Ink.Title.copy(fontSize = Ink.Title.fontSize * 1.3f))
    }
}

/** Every line at the place with its next two departures; the stop the rider came in through is listed first. */
fun boardRows(db: PackDb, placeId: Int, at: LocalDateTime, highlightStopId: Int?): List<BoardRow> =
    db.placeBoard(placeId, at, 2).sortedBy { if (it.stopId == highlightStopId) 0 else 1 }

/** A line and direction that leaves from two poles at one place; only then is the pole worth naming. */
private fun ambiguous(rows: List<BoardRow>): Set<Pair<Int, Int>> = rows.groupBy { it.route.id to it.dir }.filterValues { it.size > 1 }.keys

/** The height the tallest row at a place needs: two big lines, plus a small one for each extra note. */
fun boardRowDp(rows: List<BoardRow>): Float {
    val twoPoles = ambiguous(rows)
    val notes = rows.maxOfOrNull { row ->
        (if ((row.route.id to row.dir) in twoPoles) 1 else 0) + row.departures.mapNotNull { it.otherHeadsign }.distinct().size
    } ?: 0
    return 68f + 21f * notes
}

@Composable
fun StopViewScreen(app: AppState, placeId: Int, highlightStopId: Int?) {
    val place = remember(app.db, placeId) { app.db.place(placeId) }
    val at = app.viewTime
    val rows = remember(app.db, placeId, at, highlightStopId) { boardRows(app.db, placeId, at, highlightStopId) }
    val ambiguous = ambiguous(rows)
    ScreenFrame(place.name) {
        WhenButton(app, Modifier.fillMaxWidth())
        StaleLine(app, at)
        PagedList(
            rows, rowDp = boardRowDp(rows), modifier = Modifier.weight(1f), resetKey = placeId,
            empty = { InkText("Nothing departs from here.", Ink.Row, Modifier.padding(Ink.Gutter, 20.dp)) },
        ) { row ->
            val saved = app.isSaved(row.route.key, row.dir, row.stopKey)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().tap { app.nav.push(Screen.DayTimes(row.stopId, row.route.id, row.dir)) }.padding(horizontal = Ink.Gutter),
                    verticalArrangement = Arrangement.Center,
                ) {
                    InkText("${row.route.shortName} → ${row.headsign}", Ink.Row, maxLines = 1)
                    if ((row.route.id to row.dir) in ambiguous) InkText("at ${row.stopName}", Ink.Small, maxLines = 1)
                    val first = row.departures.firstOrNull()
                    if (first == null) {
                        InkText("No service", Ink.Row, maxLines = 1)
                    } else {
                        val times = row.departures.joinToString("   ") { app.clock.format(it.time) + if (it.otherHeadsign != null) "*" else "" }
                        InkText(listOfNotNull(app.clock.dayLabel(first.time, at), times).joinToString("   "), Ink.Row, maxLines = 1)
                        row.departures.mapNotNull { it.otherHeadsign }.distinct().forEach { InkText("* only to $it", Ink.Small, maxLines = 1) }
                    }
                }
                Star(saved, Modifier.size(64.dp)) { app.toggleSaved(row.route, row.dir, row.stopKey, row.stopName, row.headsign) }
            }
        }
    }
}

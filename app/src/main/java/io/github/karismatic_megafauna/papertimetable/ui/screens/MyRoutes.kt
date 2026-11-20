package io.github.karismatic_megafauna.papertimetable.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.karismatic_megafauna.papertimetable.ui.VRule
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.karismatic_megafauna.papertimetable.data.SavedRoute
import io.github.karismatic_megafauna.papertimetable.pack.PackDb
import io.github.karismatic_megafauna.papertimetable.pack.nextDepartures
import io.github.karismatic_megafauna.papertimetable.pack.route
import io.github.karismatic_megafauna.papertimetable.pack.servesStop
import io.github.karismatic_megafauna.papertimetable.pack.stop
import io.github.karismatic_megafauna.papertimetable.time.ServiceClock
import io.github.karismatic_megafauna.papertimetable.ui.AppState
import io.github.karismatic_megafauna.papertimetable.ui.Ink
import io.github.karismatic_megafauna.papertimetable.ui.InkButton
import io.github.karismatic_megafauna.papertimetable.ui.InkText
import io.github.karismatic_megafauna.papertimetable.ui.PagedList
import io.github.karismatic_megafauna.papertimetable.ui.Rule
import io.github.karismatic_megafauna.papertimetable.ui.Screen
import io.github.karismatic_megafauna.papertimetable.ui.ScreenFrame
import io.github.karismatic_megafauna.papertimetable.ui.tap
import java.time.LocalDateTime

/** What one saved route shows on the home screen. */
data class RouteCard(
    val saved: SavedRoute,
    /** False when the installed schedule no longer has this line or stop. */
    val resolved: Boolean,
    val times: List<String>,
    /** "Tomorrow" or "Sat Nov 21" when the next departure is not today. */
    val dayLabel: String?,
    val placeId: Int?,
)

fun buildCards(db: PackDb, clock: ServiceClock, saved: List<SavedRoute>, now: LocalDateTime): List<RouteCard> = saved.map { s ->
    val route = if (s.agencyId == db.meta.agencyId) db.route(s.routeKey) else null
    val stop = if (route != null) db.stop(s.stopKey) else null
    // The line and the stop can both survive an update while the line stops calling there.
    if (route == null || stop == null || !db.servesStop(stop.id, route.id, s.dir)) return@map RouteCard(s, false, emptyList(), null, null)
    val next = db.nextDepartures(stop.id, route.id, s.dir, now, 3)
    RouteCard(s, true, next.map { clock.format(it.time) }, next.firstOrNull()?.let { clock.dayLabel(it.time, now) }, stop.placeId)
}

@Composable
fun MyRoutesScreen(app: AppState) {
    var editing by remember { mutableStateOf(false) }
    val cards = buildCards(app.db, app.clock, app.savedRoutes, app.now)
    ScreenFrame("MY ROUTES", trailing = {
        if (cards.isNotEmpty()) {
            VRule()
            InkButton(if (editing) "Done" else "Edit", Modifier.fillMaxHeight().widthIn(min = 72.dp), style = Ink.Small, inverted = editing) { editing = !editing }
        }
        VRule()
        InkButton("About", Modifier.fillMaxHeight().widthIn(min = 72.dp), style = Ink.Small) { app.nav.push(Screen.About) }
    }) {
        StaleLine(app, app.now)
        PagedList(
            cards, rowDp = if (editing) 136f else 92f, modifier = Modifier.weight(1f), resetKey = editing,
            empty = { InkText("No routes yet. Tap Lines, pick a line, a direction, then your stop, and tap the star.", Ink.Row, Modifier.padding(Ink.Gutter, 20.dp), maxLines = 6) },
        ) { card ->
            Column(
                Modifier.fillMaxWidth()
                    .tap(enabled = !editing && card.placeId != null) { app.nav.push(Screen.StopView(card.placeId!!, app.db.stop(card.saved.stopKey)?.id)) }
                    .padding(Ink.Gutter, 7.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                InkText("${card.saved.routeName} → ${card.saved.headsign}", Ink.Row, maxLines = 1)
                InkText(card.saved.stopName, Ink.Small, maxLines = 1)
                when {
                    editing -> Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        InkButton("Up", Modifier.weight(1f), style = Ink.Small, border = true) { app.editSaved { move(card.saved, up = true) } }
                        InkButton("Down", Modifier.weight(1f), style = Ink.Small, border = true) { app.editSaved { move(card.saved, up = false) } }
                        InkButton("Remove", Modifier.weight(1f), style = Ink.Small, border = true) { app.editSaved { remove(card.saved) } }
                    }
                    !card.resolved -> InkText("No longer in schedule", Ink.Row, maxLines = 1)
                    card.times.isEmpty() -> InkText("No service", Ink.Row, maxLines = 1)
                    else -> InkText(listOfNotNull(card.dayLabel).plus(card.times).joinToString("   "), Ink.Row, maxLines = 1)
                }
            }
        }
        Rule()
        Row(Modifier.fillMaxWidth().height(Ink.ButtonHeight)) {
            InkButton("Lines", Modifier.weight(1f)) { app.nav.push(Screen.Lines) }
            VRule()
            InkButton("Near me", Modifier.weight(1f)) { app.nav.push(Screen.NearMe) }
            VRule()
            InkButton("Search", Modifier.weight(1f)) { app.nav.push(Screen.Search) }
        }
    }
}

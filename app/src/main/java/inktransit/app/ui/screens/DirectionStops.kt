package inktransit.app.ui.screens

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inktransit.app.pack.directionStops
import inktransit.app.pack.directions
import inktransit.app.pack.routes
import inktransit.app.ui.AppState
import inktransit.app.ui.Ink
import inktransit.app.ui.InkText
import inktransit.app.ui.PagedList
import inktransit.app.ui.Rule
import inktransit.app.ui.Screen
import inktransit.app.ui.ScreenFrame
import inktransit.app.ui.tap

/** A direction's stops in travel order: the last step of "line, then direction, then my stop". */
@Composable
fun DirectionStopsScreen(app: AppState, routeId: Int, dir: Int) {
    val route = remember(app.db, routeId) { app.db.routes().first { it.id == routeId } }
    val headsign = remember(app.db, routeId, dir) { app.db.directions(routeId).firstOrNull { it.dir == dir }?.headsign.orEmpty() }
    val stops = remember(app.db, routeId, dir) { app.db.directionStops(routeId, dir) }
    ScreenFrame("${route.shortName} → $headsign") {
        PagedList(stops, rowDp = 56f, modifier = Modifier.weight(1f)) { stop ->
            Box(
                Modifier.fillMaxWidth().tap { app.nav.push(Screen.StopView(stop.placeId, stop.id)) }.padding(horizontal = Ink.Gutter),
                contentAlignment = Alignment.CenterStart,
            ) { InkText(stop.name, Ink.Row, maxLines = 1) }
        }
    }
}

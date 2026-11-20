package inktransit.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import inktransit.app.pack.FreqRow
import inktransit.app.pack.PackDb
import inktransit.app.pack.dayTypeOf
import inktransit.app.pack.directions
import inktransit.app.pack.frequency
import inktransit.app.pack.routes
import inktransit.app.pack.spans
import inktransit.app.text.FrequencyWords
import inktransit.app.time.ServiceClock
import inktransit.app.time.dayOf
import inktransit.app.time.toLocal
import inktransit.app.ui.AppState
import inktransit.app.ui.Ink
import inktransit.app.ui.InkButton
import inktransit.app.ui.InkText
import inktransit.app.ui.Rule
import inktransit.app.ui.Screen
import inktransit.app.ui.ScreenFrame

/** One part of the day. [note] is set only when the other direction runs very differently. */
data class BandLine(val name: String, val text: String, val note: String?)

/** One kind of day for a line: when it starts and stops, and how often it comes. */
data class OverviewDay(val dayType: Int, val firstLast: String?, val bands: List<BandLine>)

/**
 * Six lines, one per part of the day, worded from the busier direction. The
 * other direction is mentioned only when it differs enough to change a plan:
 * one runs and the other does not, or one comes at least twice as often.
 */
fun bandLines(rows: List<FreqRow>, headsigns: Map<Int, String>): List<BandLine> = FrequencyWords.BAND_NAMES.mapIndexed { band, name ->
    val inBand = rows.filter { it.band == band }.sortedWith(compareBy({ -it.trips }, { it.dir }))
    val main = inBand.firstOrNull() ?: return@mapIndexed BandLine(name, "no service", null)
    val other = inBand.getOrNull(1)
    val text = FrequencyWords.band(main.trips, main.headwayMin)
    val differs = other != null && when {
        (other.trips == 0) != (main.trips == 0) -> true
        main.headwayMin != null && other.headwayMin != null -> other.headwayMin >= 2 * main.headwayMin
        else -> main.trips >= 2 * other.trips && main.trips - other.trips >= 3
    }
    BandLine(name, text, if (differs) "→ ${headsigns[other!!.dir].orEmpty()}: ${FrequencyWords.band(other.trips, other.headwayMin)}" else null)
}

fun overview(db: PackDb, clock: ServiceClock, routeId: Int): List<OverviewDay> {
    val headsigns = db.directions(routeId).associate { it.dir to it.headsign }
    return (0..2).map { dayType ->
        val spans = db.spans(routeId, dayType)
        val firstLast = if (spans.isEmpty()) null else {
            // Any date works for turning seconds into a clock time; only the time of day is shown.
            val day = db.meta.firstDay
            "${clock.format(toLocal(db.meta.zone, day, spans.minOf { it.first }))} – ${clock.format(toLocal(db.meta.zone, day, spans.maxOf { it.last }))}"
        }
        OverviewDay(dayType, firstLast, bandLines(db.frequency(routeId, dayType), headsigns))
    }
}

@Composable
fun LineOverviewScreen(app: AppState, routeId: Int) {
    val route = remember(app.db, routeId) { app.db.routes().first { it.id == routeId } }
    val days = remember(app.db, routeId) { overview(app.db, app.clock, routeId) }
    val directions = remember(app.db, routeId) { app.db.directions(routeId) }
    var dayType by remember { mutableIntStateOf(dayTypeOf(dayOf(app.now.toLocalDate()))) }
    val day = days[dayType]
    ScreenFrame(if (route.kind == 0) "${route.shortName} LINE" else "LINE ${route.shortName}") {
        InkText(route.longName, Ink.Small, Modifier.padding(Ink.Gutter, 4.dp), maxLines = 2)
        Row(Modifier.fillMaxWidth()) {
            DAY_TYPE_NAMES.forEachIndexed { i, name ->
                InkButton(name, Modifier.weight(1f), inverted = i == dayType, style = Ink.Small) { dayType = i }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(Ink.Gutter, 6.dp)) {
            InkText(day.firstLast ?: "Does not run", Ink.Row, Modifier.padding(bottom = 4.dp), maxLines = 1)
            if (day.firstLast != null) day.bands.forEach { band ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    InkText(band.name, Ink.Small, Modifier.weight(1f), maxLines = 1)
                    InkText(band.text, Ink.Small, maxLines = 1)
                }
                band.note?.let { InkText(it, Ink.Small, Modifier.padding(start = 16.dp), maxLines = 2) }
            }
            Spacer(Modifier.weight(1f))
        }
        Rule()
        directions.forEach { d ->
            InkButton("→ ${d.headsign}", Modifier.fillMaxWidth()) { app.nav.push(Screen.DirectionStops(routeId, d.dir)) }
        }
    }
}

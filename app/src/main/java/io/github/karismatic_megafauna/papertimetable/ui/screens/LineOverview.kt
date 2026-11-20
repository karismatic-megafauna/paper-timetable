package io.github.karismatic_megafauna.papertimetable.ui.screens

import androidx.compose.foundation.layout.height
import io.github.karismatic_megafauna.papertimetable.ui.VRule
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
import io.github.karismatic_megafauna.papertimetable.pack.FreqRow
import io.github.karismatic_megafauna.papertimetable.pack.PackDb
import io.github.karismatic_megafauna.papertimetable.pack.dayTypeOf
import io.github.karismatic_megafauna.papertimetable.pack.directions
import io.github.karismatic_megafauna.papertimetable.pack.frequency
import io.github.karismatic_megafauna.papertimetable.pack.routes
import io.github.karismatic_megafauna.papertimetable.pack.spans
import io.github.karismatic_megafauna.papertimetable.text.FrequencyWords
import io.github.karismatic_megafauna.papertimetable.time.ServiceClock
import io.github.karismatic_megafauna.papertimetable.time.dayOf
import io.github.karismatic_megafauna.papertimetable.time.toLocal
import io.github.karismatic_megafauna.papertimetable.ui.AppState
import io.github.karismatic_megafauna.papertimetable.ui.Ink
import io.github.karismatic_megafauna.papertimetable.ui.InkButton
import io.github.karismatic_megafauna.papertimetable.ui.InkText
import io.github.karismatic_megafauna.papertimetable.ui.Rule
import io.github.karismatic_megafauna.papertimetable.ui.Screen
import io.github.karismatic_megafauna.papertimetable.ui.ScreenFrame

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
        Rule()
        Row(Modifier.fillMaxWidth().height(Ink.ButtonHeight)) {
            DAY_TYPE_NAMES.forEachIndexed { i, name ->
                if (i > 0) VRule()
                InkButton(name, Modifier.weight(1f), inverted = i == dayType, style = Ink.Small) { dayType = i }
            }
        }
        Rule()
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
        directions.forEach { d ->
            Rule()
            InkButton("→ ${d.headsign}", Modifier.fillMaxWidth()) { app.nav.push(Screen.DirectionStops(routeId, d.dir)) }
        }
    }
}

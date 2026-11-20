package inktransit.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import inktransit.app.pack.RouteRow
import inktransit.app.pack.allFrequencyTags
import inktransit.app.pack.dayTypeOf
import inktransit.app.pack.routes
import inktransit.app.time.dayOf
import inktransit.app.ui.AppState
import inktransit.app.ui.Ink
import inktransit.app.ui.InkText
import inktransit.app.ui.PagedList
import inktransit.app.ui.Rule
import inktransit.app.ui.Screen
import inktransit.app.ui.ScreenFrame
import inktransit.app.ui.tap

val DAY_TYPE_NAMES = listOf("Weekday", "Saturday", "Sunday")

/** Lines whose name starts with what was typed, then lines that merely contain it. */
fun filterLines(routes: List<RouteRow>, typed: String): List<RouteRow> {
    val t = typed.trim()
    if (t.isEmpty()) return routes
    val (starts, rest) = routes.partition { it.shortName.startsWith(t, ignoreCase = true) }
    return starts + rest.filter { it.shortName.contains(t, ignoreCase = true) }
}

/** A one-line text box. No cursor animation beyond the platform's; the border is the only decoration. */
@Composable
fun InkField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Box(modifier.border(2.dp, Ink.Black).padding(horizontal = 10.dp, vertical = 12.dp)) {
        if (value.isEmpty()) InkText(hint, Ink.Small, maxLines = 1)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), textStyle = Ink.Row, singleLine = true, cursorBrush = SolidColor(Ink.Black),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        )
    }
}

@Composable
fun LinesScreen(app: AppState) {
    var typed by remember { mutableStateOf("") }
    val dayType = dayTypeOf(dayOf(app.now.toLocalDate()))
    val routes = remember(app.db) { app.db.routes() }
    val tags = remember(app.db, dayType) { app.db.allFrequencyTags(dayType) }
    ScreenFrame("LINES", trailing = { InkText(DAY_TYPE_NAMES[dayType], Ink.Small) }) {
        InkField(typed, "Type a line: 720, D …", Modifier.fillMaxWidth().padding(Ink.Gutter, 8.dp)) { typed = it }
        Rule()
        PagedList(
            filterLines(routes, typed), pageSize = 6, modifier = Modifier.weight(1f), resetKey = typed,
            empty = { InkText("No line called “$typed”.", Ink.Row, Modifier.padding(Ink.Gutter, 20.dp)) },
        ) { route ->
            Row(
                Modifier.fillMaxWidth().tap { app.nav.push(Screen.LineOverview(route.id)) }.padding(Ink.Gutter, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkText(route.shortName, Ink.Row, Modifier.width(if (route.shortName.length > 4) 150.dp else 64.dp), maxLines = 1)
                InkText(tags[route.id] ?: "no service", Ink.Small, maxLines = 1)
            }
            Rule()
        }
    }
}

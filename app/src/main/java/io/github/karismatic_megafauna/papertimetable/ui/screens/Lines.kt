package io.github.karismatic_megafauna.papertimetable.ui.screens

import io.github.karismatic_megafauna.papertimetable.ui.VRule
import io.github.karismatic_megafauna.papertimetable.ui.InkButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.karismatic_megafauna.papertimetable.pack.RouteRow
import io.github.karismatic_megafauna.papertimetable.pack.allFrequencyTags
import io.github.karismatic_megafauna.papertimetable.pack.dayTypeOf
import io.github.karismatic_megafauna.papertimetable.pack.routes
import io.github.karismatic_megafauna.papertimetable.time.dayOf
import io.github.karismatic_megafauna.papertimetable.ui.AppState
import io.github.karismatic_megafauna.papertimetable.ui.Ink
import io.github.karismatic_megafauna.papertimetable.ui.InkText
import io.github.karismatic_megafauna.papertimetable.ui.PagedList
import io.github.karismatic_megafauna.papertimetable.ui.Rule
import io.github.karismatic_megafauna.papertimetable.ui.Screen
import io.github.karismatic_megafauna.papertimetable.ui.ScreenFrame
import io.github.karismatic_megafauna.papertimetable.ui.tap

val DAY_TYPE_NAMES = listOf("Weekday", "Saturday", "Sunday")

/** Lines whose name starts with what was typed, then lines that merely contain it. */
fun filterLines(routes: List<RouteRow>, typed: String): List<RouteRow> {
    val t = typed.trim()
    if (t.isEmpty()) return routes
    val (starts, rest) = routes.partition { it.shortName.startsWith(t, ignoreCase = true) }
    return starts + rest.filter { it.shortName.contains(t, ignoreCase = true) }
}

/** Rail or bus lines while the box is empty; once something is typed, matches from both. */
fun linesShown(routes: List<RouteRow>, typed: String, rail: Boolean): List<RouteRow> =
    if (typed.isBlank()) routes.filter { (it.kind == 0) == rail } else filterLines(routes, typed)

/**
 * Compose blinks any text cursor that has a colour, about twice a second, and
 * every blink is an E Ink refresh. An unspecified colour draws no cursor at
 * all; InkField draws a still bar after the text instead.
 */
val STILL_CURSOR: Brush = SolidColor(Color.Unspecified)

/** A one-line text box with a caret that does not blink. */
@Composable
fun InkField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Box(modifier.border(2.dp, Ink.Black).padding(horizontal = 10.dp, vertical = 12.dp)) {
        if (value.isEmpty()) InkText(hint, Ink.Small, maxLines = 1)
        var focused by remember { mutableStateOf(false) }
        // Where the typed text ends, taken from the text's own layout so the bar sits right against the last letter.
        var caret by remember { mutableStateOf(Rect.Zero) }
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.drawWithContent {
                drawContent()
                if (focused && value.isNotEmpty()) drawRect(Ink.Black, Offset(caret.left + 1.dp.toPx(), caret.top), Size(2.dp.toPx(), caret.height))
            },
            textStyle = Ink.Row, singleLine = true, cursorBrush = STILL_CURSOR,
            // Capitals are the rider's choice: matching ignores case, and a forced caps mode cannot be switched off.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            onTextLayout = { caret = it.getCursorRect(it.layoutInput.text.length) },
        )
    }
}

@Composable
fun LinesScreen(app: AppState) {
    var typed by remember { mutableStateOf("") }
    val dayType = dayTypeOf(dayOf(app.now.toLocalDate()))
    val routes = remember(app.db) { app.db.routes() }
    val tags = remember(app.db, dayType) { app.db.allFrequencyTags(dayType) }
    ScreenFrame("LINES", trailing = { InkText(DAY_TYPE_NAMES[dayType], Ink.Small, Modifier.padding(end = Ink.Gutter)) }) {
        InkField(typed, "Type a line: 720, D …", Modifier.fillMaxWidth().padding(Ink.Gutter, 8.dp)) { typed = it }
        // Neither is marked while typing: the list then holds matches from both.
        Rule()
        Row(Modifier.fillMaxWidth().height(Ink.ButtonHeight)) {
            InkButton("Rail", Modifier.weight(1f), inverted = typed.isBlank() && app.linesRail) { typed = ""; app.linesRail = true }
            VRule()
            InkButton("Bus", Modifier.weight(1f), inverted = typed.isBlank() && !app.linesRail) { typed = ""; app.linesRail = false }
        }
        Rule()
        PagedList(
            linesShown(routes, typed, app.linesRail), rowDp = 56f, modifier = Modifier.weight(1f), resetKey = typed to app.linesRail,
            empty = { InkText("No line called “$typed”.", Ink.Row, Modifier.padding(Ink.Gutter, 20.dp)) },
        ) { route ->
            Row(
                Modifier.fillMaxWidth().tap { app.nav.push(Screen.LineOverview(route.id)) }.padding(horizontal = Ink.Gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkText(route.shortName, Ink.Row, Modifier.width(if (route.shortName.length > 4) 150.dp else 64.dp), maxLines = 1)
                InkText(tags[route.id] ?: "no service", Ink.Small, maxLines = 1)
            }
        }
    }
}

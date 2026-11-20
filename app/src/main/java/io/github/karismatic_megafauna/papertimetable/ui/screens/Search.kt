package io.github.karismatic_megafauna.papertimetable.ui.screens

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import io.github.karismatic_megafauna.papertimetable.pack.PlaceRow
import io.github.karismatic_megafauna.papertimetable.pack.searchPlaces
import io.github.karismatic_megafauna.papertimetable.ui.AppState
import io.github.karismatic_megafauna.papertimetable.ui.Ink
import io.github.karismatic_megafauna.papertimetable.ui.InkText
import io.github.karismatic_megafauna.papertimetable.ui.PagedList
import io.github.karismatic_megafauna.papertimetable.ui.Rule
import io.github.karismatic_megafauna.papertimetable.ui.Screen
import io.github.karismatic_megafauna.papertimetable.ui.ScreenFrame
import io.github.karismatic_megafauna.papertimetable.ui.rowsThatFit
import io.github.karismatic_megafauna.papertimetable.ui.tap

/** A place to tap. [lines] adds a second line saying what stops there. */
@Composable
fun PlaceRowItem(app: AppState, place: PlaceRow, detail: String? = null, lines: String? = null) {
    Column(
        Modifier.fillMaxWidth().tap { app.nav.push(Screen.StopView(place.id)) }.padding(horizontal = Ink.Gutter),
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InkText(place.name, Ink.Row, Modifier.weight(1f), maxLines = 1)
            detail?.let { InkText(it, Ink.Small, Modifier.padding(start = 8.dp), maxLines = 1) }
        }
        lines?.let { InkText(it, Ink.Small, maxLines = 1) }
    }
}

/** Find a stop by name. Results appear from the second letter; nothing is looked up online. */
@Composable
fun SearchScreen(app: AppState) {
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(app.db, typed) { if (typed.trim().length >= 2) app.db.searchPlaces(typed, 60) else emptyList() }
    ScreenFrame("SEARCH") {
        InkField(typed, "Stop or station name", Modifier.fillMaxWidth().padding(Ink.Gutter, 8.dp).focusRequester(focus)) { typed = it }
        Rule()
        when {
            typed.trim().length < 2 -> InkText("Type at least two letters.", Ink.Small, Modifier.padding(Ink.Gutter, 12.dp))
            results.isEmpty() -> InkText("No stop called “${typed.trim()}”.", Ink.Row, Modifier.padding(Ink.Gutter, 12.dp))
            // The keyboard takes over half the screen; the page size follows the room that is left.
            else -> PagedList(results, rowDp = 56f, modifier = Modifier.weight(1f), resetKey = typed) { PlaceRowItem(app, it) }
        }
    }
}

package inktransit.app.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
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
import inktransit.app.pack.PlaceRow
import inktransit.app.pack.searchPlaces
import inktransit.app.ui.AppState
import inktransit.app.ui.Ink
import inktransit.app.ui.InkText
import inktransit.app.ui.PagedList
import inktransit.app.ui.Rule
import inktransit.app.ui.Screen
import inktransit.app.ui.ScreenFrame
import inktransit.app.ui.tap

@Composable
fun PlaceRowItem(app: AppState, place: PlaceRow, detail: String? = null) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().tap { app.nav.push(Screen.StopView(place.id)) }.padding(Ink.Gutter, 13.dp),
    ) {
        InkText(place.name, Ink.Row, Modifier.weight(1f), maxLines = 1)
        detail?.let { InkText(it, Ink.Small, Modifier.padding(start = 8.dp), maxLines = 1) }
    }
    Rule()
}

/** Find a stop by name. Results appear from the second letter; nothing is looked up online. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(app: AppState) {
    var typed by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(app.db, typed) { if (typed.trim().length >= 2) app.db.searchPlaces(typed, 60) else emptyList() }
    // With the keyboard up there is room for a few rows only; closing it shows the full pages.
    val keyboardUp = WindowInsets.isImeVisible
    ScreenFrame("SEARCH") {
        InkField(typed, "Stop or station name", Modifier.fillMaxWidth().padding(Ink.Gutter, 8.dp).focusRequester(focus)) { typed = it }
        Rule()
        when {
            typed.trim().length < 2 -> InkText("Type at least two letters.", Ink.Small, Modifier.padding(Ink.Gutter, 12.dp))
            results.isEmpty() -> InkText("No stop called “${typed.trim()}”.", Ink.Row, Modifier.padding(Ink.Gutter, 12.dp))
            keyboardUp -> {
                results.take(2).forEach { PlaceRowItem(app, it) }
                if (results.size > 2) InkText("${results.size - 2} more — close the keyboard to see them", Ink.Small, Modifier.padding(Ink.Gutter, 6.dp))
            }
            else -> PagedList(results, pageSize = 6, modifier = Modifier.weight(1f), resetKey = typed) { PlaceRowItem(app, it) }
        }
    }
}

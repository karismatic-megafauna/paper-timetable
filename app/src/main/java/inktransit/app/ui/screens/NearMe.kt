package inktransit.app.ui.screens

import inktransit.app.pack.placeLines
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import inktransit.app.location.AndroidLocationSource
import inktransit.app.location.Fix
import inktransit.app.location.OneShotLocation
import inktransit.app.pack.formatDistance
import inktransit.app.pack.nearestPlaces
import inktransit.app.ui.AppState
import inktransit.app.ui.Ink
import inktransit.app.ui.InkButton
import inktransit.app.ui.InkText
import inktransit.app.ui.PagedList
import inktransit.app.ui.ScreenFrame
import java.util.Locale

/**
 * The closest stops, from one location fix taken when this screen opens. The
 * permission is asked for here, the first time, and nowhere else.
 */
@Composable
fun NearMeScreen(app: AppState) {
    val context = LocalContext.current
    val source = remember { AndroidLocationSource(context.applicationContext) }
    var fix by remember { mutableStateOf<Fix?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { attempt++ }

    LaunchedEffect(attempt) {
        if (!source.hasPermission() && !asked) {
            asked = true
            askPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return@LaunchedEffect
        }
        fix = null
        fix = OneShotLocation(source).get()
    }

    ScreenFrame("NEAR ME") {
        val message = Modifier.padding(Ink.Gutter, 20.dp)
        when (val f = fix) {
            null -> InkText("Finding location…", Ink.Row, message)
            Fix.Denied -> InkText("Location permission is off. Turn it on in Settings to use Near me.", Ink.Row, message, maxLines = 5)
            Fix.Off -> InkText("Location is switched off on this phone.", Ink.Row, message, maxLines = 4)
            Fix.None -> {
                InkText("Couldn't find your location. Try again outside.", Ink.Row, message, maxLines = 4)
                InkButton("Try again", Modifier.fillMaxWidth().padding(horizontal = Ink.Gutter), border = true) { attempt++ }
            }
            is Fix.Fresh -> Nearest(app, f.lat, f.lon, null)
            is Fix.Stale -> Nearest(app, f.lat, f.lon, "Using your location from ${f.ageMinutes} min ago")
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Nearest(app: AppState, lat: Double, lon: Double, note: String?) {
    val places = remember(app.db, lat, lon) { app.db.nearestPlaces(lat, lon, 18) }
    val lines = remember(app.db, places) { app.db.placeLines(places.map { it.first.id }) }
    note?.let { InkText(it, Ink.Small, Modifier.padding(Ink.Gutter, 4.dp)) }
    PagedList(
        places, rowDp = 72f, modifier = Modifier.weight(1f),
        empty = { InkText("No ${app.db.meta.agencyName} stops near here.", Ink.Row, Modifier.padding(Ink.Gutter, 20.dp), maxLines = 3) },
    ) { (place, metres) -> PlaceRowItem(app, place, formatDistance(metres, Locale.getDefault()), lines[place.id].orEmpty().joinToString(" · ")) }
}

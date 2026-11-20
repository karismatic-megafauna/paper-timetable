package inktransit.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import inktransit.app.BuildConfig
import inktransit.app.time.dateOf
import inktransit.app.ui.AppState
import inktransit.app.ui.Ink
import inktransit.app.ui.InkButton
import inktransit.app.ui.InkText
import inktransit.app.ui.ScreenFrame
import inktransit.app.update.PackUpdater
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** Which schedule is installed, how long it is good for, and the only button in the app that uses the network. */
@Composable
fun AboutScreen(app: AppState) {
    val context = LocalContext.current
    val meta = app.db.meta
    val status = app.updateStatus
    val busy = app.updating
    ScreenFrame("ABOUT") {
        Column(Modifier.weight(1f).fillMaxWidth().padding(Ink.Gutter, 12.dp)) {
            InkText(meta.agencyName, Ink.Title)
            InkText("Schedule valid ${dateOf(meta.validFrom).format(SHORT_DATE)} – ${dateOf(meta.validTo).format(SHORT_DATE)}", Ink.Row, Modifier.padding(top = 10.dp))
            InkText("Built ${meta.builtAt.atZone(meta.zone).format(SHORT_DATE)}", Ink.Small, Modifier.padding(top = 4.dp))
            staleText(meta, inktransit.app.time.dayOf(app.now.toLocalDate()), inktransit.app.time.dayOf(app.now.toLocalDate()))?.let {
                InkText(it, Ink.Row, Modifier.padding(top = 10.dp))
            }
            status?.let { InkText(it, Ink.Row, Modifier.padding(top = 16.dp), maxLines = 4) }
            Spacer(Modifier.weight(1f))
            InkText("Works offline. Uses the network only when you tap Update schedule, and your location only when you tap Near me.", Ink.Small, maxLines = 5)
            InkText("Version ${BuildConfig.VERSION_NAME}", Ink.Small, Modifier.padding(top = 6.dp))
        }
        if (BuildConfig.PACK_BASE_URL.isEmpty()) {
            InkText("Updates not set up in this build", Ink.Small, Modifier.padding(Ink.Gutter, 12.dp))
        } else {
            InkButton(if (busy) "Updating…" else "Update schedule", Modifier.fillMaxWidth(), enabled = true, inverted = busy) {
                // The app, not this screen, owns the update: it finishes even if the rider leaves.
                val updater = PackUpdater(BuildConfig.PACK_BASE_URL, app.store, File(context.cacheDir, "update"))
                app.startUpdate { progress -> updater.update(progress) }
            }
        }
    }
}

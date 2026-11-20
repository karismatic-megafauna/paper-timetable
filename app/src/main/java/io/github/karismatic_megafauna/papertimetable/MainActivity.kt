package io.github.karismatic_megafauna.papertimetable

import io.github.karismatic_megafauna.papertimetable.ui.backLabel
import io.github.karismatic_megafauna.papertimetable.ui.LocalBack
import io.github.karismatic_megafauna.papertimetable.ui.Back
import androidx.compose.runtime.CompositionLocalProvider
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.karismatic_megafauna.papertimetable.ui.InkText
import kotlin.concurrent.thread
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import io.github.karismatic_megafauna.papertimetable.data.SavedRoutes
import io.github.karismatic_megafauna.papertimetable.pack.PackStore
import io.github.karismatic_megafauna.papertimetable.ui.AppState
import io.github.karismatic_megafauna.papertimetable.ui.EInkTheme
import io.github.karismatic_megafauna.papertimetable.ui.Screen
import io.github.karismatic_megafauna.papertimetable.ui.screens.DirectionStopsScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.LineOverviewScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.LinesScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.AboutScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.MyRoutesScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.NearMeScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.SearchScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.DayTimesScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.StopViewScreen
import io.github.karismatic_megafauna.papertimetable.ui.screens.WhenPickerScreen
import io.github.karismatic_megafauna.papertimetable.ui.WhenChoice
import java.io.File

class MainActivity : ComponentActivity() {
    // Null only while the bundled schedule is being copied out on first launch.
    private var loaded: AppState? by mutableStateOf(null)
    private val handler = Handler(Looper.getMainLooper())

    // While the app is on screen, wake at the top of each minute. Screens only
    // redraw if what they show actually changed (a departure passed).
    private val minuteTick = object : Runnable {
        override fun run() {
            loaded?.tick()
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = packageManager.getPackageInfo(packageName, 0)
        val store = PackStore(File(filesDir, "packs"), "${info.longVersionCode}-${info.lastUpdateTime}") { assets.open(BuildConfig.BUNDLED_PACK) }
        val saved = SavedRoutes(File(filesDir, "saved.json"))
        // Copying the schedule out of the app takes a moment, so that one launch does it off the
        // main thread. Every other launch opens straight onto My Routes with no extra redraw.
        if (store.needsInstall()) thread { AppState(store, saved).let { runOnUiThread { loaded = it } } }
        else loaded = AppState(store, saved)
        setContent {
            EInkTheme {
                val app = loaded
                if (app == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InkText("Preparing schedule…") }
                    return@EInkTheme
                }
                BackHandler(enabled = app.nav.canPop) { app.nav.pop() }
                CompositionLocalProvider(LocalBack provides backLabel(app.db, app.nav.trail)?.let { Back(it) { app.nav.pop() } }) {
                when (val screen = app.nav.top) {
                    Screen.MyRoutes -> MyRoutesScreen(app)
                    Screen.Lines -> LinesScreen(app)
                    is Screen.LineOverview -> LineOverviewScreen(app, screen.routeId)
                    is Screen.DirectionStops -> DirectionStopsScreen(app, screen.routeId, screen.dir)
                    is Screen.StopView -> StopViewScreen(app, screen.placeId, screen.highlightStopId)
                    is Screen.DayTimes -> DayTimesScreen(app, screen.stopId, screen.routeId, screen.dir)
                    Screen.WhenPicker -> WhenPickerScreen(app)
                    Screen.Search -> SearchScreen(app)
                    Screen.NearMe -> NearMeScreen(app)
                    Screen.About -> AboutScreen(app)
                }
                }
            }
        }
    }

    // Coming back to the app always shows the present; a chosen time is for one look ahead.
    override fun onRestart() {
        super.onRestart()
        loaded?.whenChoice = WhenChoice.Now
    }

    override fun onResume() {
        super.onResume()
        minuteTick.run()
    }

    override fun onPause() {
        handler.removeCallbacks(minuteTick)
        super.onPause()
    }
}

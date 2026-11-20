package io.github.karismatic_megafauna.papertimetable

import io.github.karismatic_megafauna.papertimetable.ui.backLabel
import io.github.karismatic_megafauna.papertimetable.ui.LocalBack
import io.github.karismatic_megafauna.papertimetable.ui.Back
import androidx.compose.runtime.CompositionLocalProvider
import android.os.Bundle
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
    private lateinit var app: AppState
    private val handler = Handler(Looper.getMainLooper())

    // While the app is on screen, wake at the top of each minute. Screens only
    // redraw if what they show actually changed (a departure passed).
    private val minuteTick = object : Runnable {
        override fun run() {
            app.tick()
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = packageManager.getPackageInfo(packageName, 0)
        val store = PackStore(File(filesDir, "packs"), "${info.longVersionCode}-${info.lastUpdateTime}") { assets.open(BuildConfig.BUNDLED_PACK) }
        app = AppState(store, SavedRoutes(File(filesDir, "saved.json")))
        setContent {
            EInkTheme {
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
        app.whenChoice = WhenChoice.Now
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

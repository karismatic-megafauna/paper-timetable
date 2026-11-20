package inktransit.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import inktransit.app.data.SavedRoutes
import inktransit.app.pack.PackStore
import inktransit.app.ui.AppState
import inktransit.app.ui.EInkTheme
import inktransit.app.ui.Screen
import inktransit.app.ui.screens.DirectionStopsScreen
import inktransit.app.ui.screens.LineOverviewScreen
import inktransit.app.ui.screens.LinesScreen
import inktransit.app.ui.screens.AboutScreen
import inktransit.app.ui.screens.MyRoutesScreen
import inktransit.app.ui.screens.NearMeScreen
import inktransit.app.ui.screens.SearchScreen
import inktransit.app.ui.screens.DayTimesScreen
import inktransit.app.ui.screens.StopViewScreen
import inktransit.app.ui.screens.WhenPickerScreen
import inktransit.app.ui.WhenChoice
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

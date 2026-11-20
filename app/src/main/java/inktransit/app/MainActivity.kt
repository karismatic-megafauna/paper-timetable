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
import inktransit.app.ui.ScreenFrame
import inktransit.app.ui.screens.MyRoutesScreen
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
                    else -> ScreenFrame(screen.toString()) {}
                }
            }
        }
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

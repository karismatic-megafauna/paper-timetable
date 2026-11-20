package inktransit.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import inktransit.app.data.SavedRoute
import inktransit.app.data.SavedRoutes
import inktransit.app.pack.PackDb
import inktransit.app.pack.PackStore
import inktransit.app.time.ServiceClock
import inktransit.app.update.UpdateResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** What moment the schedule screens show: the present, or a day and time the rider picked to plan ahead. */
sealed interface WhenChoice {
    data object Now : WhenChoice
    data class At(val time: LocalDateTime) : WhenChoice
}

/** Everything the screens share. Held by the activity; screens only read it and call its methods. */
class AppState(val store: PackStore, private val saved: SavedRoutes, private val instant: () -> Instant = Instant::now) {
    var db: PackDb by mutableStateOf(store.current())
        private set
    val clock: ServiceClock get() = ServiceClock(db.meta.zone, instant)
    val nav = Nav()

    /** The current minute in the agency's zone. Advanced by [tick], so screens redraw at most once a minute. */
    var now: LocalDateTime by mutableStateOf(currentMinute())
        private set
    var savedRoutes: List<SavedRoute> by mutableStateOf(saved.all())
        private set

    /** Kept in memory only, and reset whenever the app comes back to the front. */
    /** Which half of the Lines list is open; kept here so coming back from a line returns to it. */
    var linesRail: Boolean by mutableStateOf(true)
    var whenChoice: WhenChoice by mutableStateOf(WhenChoice.Now)

    /** The moment the schedule screens should show. My Routes ignores this and always shows [now]. */
    val viewTime: LocalDateTime get() = (whenChoice as? WhenChoice.At)?.time ?: now

    private fun currentMinute() = clock.now().truncatedTo(ChronoUnit.MINUTES)

    fun tick() {
        val minute = currentMinute()
        if (minute != now) now = minute
    }

    fun isSaved(routeKey: String, dir: Int, stopKey: String) =
        savedRoutes.any { it.sameRouteAs(db.meta.agencyId, routeKey, dir, stopKey) }

    /** Stars the route if it is not saved, unstars it if it is. */
    fun toggleSaved(route: inktransit.app.pack.RouteRow, dir: Int, stopKey: String, stopName: String, headsign: String) {
        val item = SavedRoute(db.meta.agencyId, route.key, dir, stopKey, route.shortName, headsign, stopName)
        val saved = isSaved(route.key, dir, stopKey)
        editSaved { if (saved) remove(item) else add(item) }
    }

    fun editSaved(change: SavedRoutes.() -> Unit) {
        saved.change()
        savedRoutes = saved.all()
    }

    /** Shown on the About screen. Lives here so it survives leaving that screen mid-download. */
    var updateStatus: String? by mutableStateOf(null)
        private set
    var updating: Boolean by mutableStateOf(false)
        private set
    private val scope = MainScope()

    /**
     * Runs a schedule update to completion whatever screen is showing. [run]
     * does the blocking work on [io] and reports progress as a percentage.
     */
    fun startUpdate(io: CoroutineDispatcher = Dispatchers.IO, run: (progress: (Int) -> Unit) -> UpdateResult) {
        if (updating) return
        updating = true
        updateStatus = "Checking…"
        scope.launch {
            val result = withContext(io) { run { percent -> updateStatus = "Downloading $percent%" } }
            updateStatus = when (result) {
                UpdateResult.UpToDate -> "You have the latest schedule."
                is UpdateResult.Failed -> result.reason
                is UpdateResult.Updated -> {
                    packReplaced(store.current())
                    "Schedule updated."
                }
            }
            updating = false
        }
    }

    /** Switches the screens to [newDb], then closes the pack they were using. */
    fun packReplaced(newDb: PackDb) {
        val old = db
        if (old === newDb) return
        db = newDb
        tick()
        old.close()
    }
}

package inktransit.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import inktransit.app.data.SavedRoute
import inktransit.app.data.SavedRoutes
import inktransit.app.pack.PackDb
import inktransit.app.pack.PackStore
import inktransit.app.time.ServiceClock
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

    fun editSaved(change: SavedRoutes.() -> Unit) {
        saved.change()
        savedRoutes = saved.all()
    }

    fun packReplaced(newDb: PackDb) {
        db = newDb
        tick()
    }
}

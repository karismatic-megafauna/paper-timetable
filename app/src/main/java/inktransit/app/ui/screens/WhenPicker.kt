package inktransit.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import inktransit.app.time.ServiceClock
import inktransit.app.ui.AppState
import inktransit.app.ui.InkButton
import inktransit.app.ui.PagedList
import inktransit.app.ui.ScreenFrame
import inktransit.app.ui.WhenChoice
import java.time.LocalDate

/** The next [count] days, labelled Today, Tomorrow, then "Sat Nov 21". Real dates, so holidays come out right. */
fun dayOptions(today: LocalDate, count: Int = 28): List<Pair<LocalDate, String>> = (0 until count).map { i ->
    val date = today.plusDays(i.toLong())
    date to when (i) {
        0 -> "Today"
        1 -> "Tomorrow"
        else -> date.format(ServiceClock.DAY)
    }
}

private fun hourLabel(hour: Int) = "${if (hour % 12 == 0) 12 else hour % 12}${if (hour < 12) "a" else "p"}"

/** Day, then hour, then quarter-hour, each a screen of large buttons. No spinning wheels on E Ink. */
@Composable
fun WhenPickerScreen(app: AppState) {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var hour by remember { mutableStateOf<Int?>(null) }
    val pickedDate = date
    val pickedHour = hour
    when {
        pickedDate == null -> ScreenFrame("WHEN?") {
            InkButton("Now", Modifier.fillMaxWidth(), inverted = app.whenChoice == WhenChoice.Now) {
                app.whenChoice = WhenChoice.Now
                app.nav.pop()
            }
            PagedList(dayOptions(app.now.toLocalDate()), pageSize = 5, modifier = Modifier.weight(1f)) { (day, label) ->
                InkButton(label, Modifier.fillMaxWidth()) { date = day }
            }
        }
        pickedHour == null -> ScreenFrame("WHAT HOUR?") {
            (0 until 24).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    row.forEach { h -> InkButton(hourLabel(h), Modifier.weight(1f)) { hour = h } }
                }
            }
        }
        else -> ScreenFrame("${hourLabel(pickedHour).dropLast(1)}:__ ?") {
            listOf(0, 15, 30, 45).forEach { minute ->
                InkButton("%d:%02d%s".format(if (pickedHour % 12 == 0) 12 else pickedHour % 12, minute, if (pickedHour < 12) "a" else "p"), Modifier.fillMaxWidth()) {
                    app.whenChoice = WhenChoice.At(pickedDate.atTime(pickedHour, minute))
                    app.nav.pop()
                }
            }
        }
    }
}

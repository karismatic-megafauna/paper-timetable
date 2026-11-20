package inktransit.app.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A schedule time: seconds after the start of service day [day] (YYYYMMDD). May exceed 24 h. */
data class ServiceTime(val day: Int, val sec: Int)

fun dayOf(date: LocalDate): Int = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth

fun dateOf(day: Int): LocalDate = LocalDate.of(day / 10000, day / 100 % 100, day % 100)

/**
 * Wall-clock time of a schedule time. Schedules count from "noon minus twelve
 * hours" so that they stay right on the days the clocks change.
 */
fun toLocal(zone: ZoneId, day: Int, sec: Int): LocalDateTime =
    ZonedDateTime.of(dateOf(day), LocalTime.NOON, zone).minusHours(12).plusSeconds(sec.toLong()).toLocalDateTime()

/**
 * All times in the app are the agency's local time, whatever the phone is set
 * to: a rider whose phone thinks it is in Tokyo still catches the right bus.
 */
class ServiceClock(val zone: ZoneId, private val instant: () -> Instant = Instant::now) {
    fun now(): LocalDateTime = LocalDateTime.ofInstant(instant(), zone)

    /** Where to look for departures at or after [at]: yesterday's after-midnight trips, then today's. */
    fun candidates(at: LocalDateTime): List<ServiceTime> {
        val sec = at.toLocalTime().toSecondOfDay()
        return listOf(ServiceTime(dayOf(at.toLocalDate().minusDays(1)), sec + 86400), ServiceTime(dayOf(at.toLocalDate()), sec))
    }

    /** 4:12p, 12:10a */
    fun format(t: LocalDateTime): String {
        val hour = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "%d:%02d%s".format(Locale.ROOT, hour, t.minute, if (t.hour < 12) "a" else "p")
    }

    /** null on the same day, "Tomorrow", otherwise "Sat Nov 21". */
    fun dayLabel(t: LocalDateTime, relativeTo: LocalDateTime): String? = dayLabel(t.toLocalDate(), relativeTo.toLocalDate())

    fun dayLabel(date: LocalDate, today: LocalDate): String? = when (date) {
        today -> null
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DAY)
    }

    companion object {
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)
    }
}

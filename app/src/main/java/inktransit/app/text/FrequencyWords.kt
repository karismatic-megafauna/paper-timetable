package inktransit.app.text

import inktransit.app.pack.FreqRow
import kotlin.math.abs

/**
 * Turns the pack's frequency numbers into the plain phrases a rider plans by:
 * "every 10 min" is a line you can walk up to, "about hourly" is one you time.
 */
object FrequencyWords {
    /** Matches the converter's BANDS (frequency.py): 4–6, 6–9, 9–15, 15–19, 19–22, 22–4. */
    val BAND_NAMES = listOf("Early morning", "Morning rush", "Midday", "Evening rush", "Evening", "Night")

    private val STEPS = listOf(2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 45, 60)
    private const val FEW_TRIPS = 8
    private val HOURLY = 53..75

    /** The nearest round headway; ties go to the shorter one. */
    fun snap(min: Int): Int = STEPS.minBy { abs(it - min) }

    /** One part of the day. */
    fun band(trips: Int, headwayMin: Int?): String = when {
        trips == 0 -> "no service"
        trips == 1 -> "1 trip"
        headwayMin == null || headwayMin > HOURLY.last -> "$trips trips"
        headwayMin in HOURLY -> "about hourly"
        else -> "every ${snap(headwayMin)} min"
    }

    /** One short phrase for a whole line on one kind of day, from both directions' rows. */
    fun tag(rows: List<FreqRow>): String {
        val busiest = rows.groupBy { it.dir }.values.maxOfOrNull { dir -> dir.sumOf { it.trips } } ?: 0
        if (busiest == 0) return "no service"
        if (busiest <= FEW_TRIPS) return if (busiest == 1) "1 trip a day" else "$busiest trips a day"
        val daytime = rows.filter { it.band in 1..3 && it.headwayMin != null }
        val headways = daytime.ifEmpty { rows.filter { it.headwayMin != null } }.map { round(it.headwayMin!!) }
        if (headways.isEmpty()) return "$busiest trips a day"
        val lo = headways.min()
        val hi = headways.max()
        return when {
            lo != hi -> "every $lo–$hi min"
            lo == 60 -> "about hourly"
            else -> "every $lo min"
        }
    }

    private fun round(min: Int): Int = when {
        min in HOURLY -> 60
        min > HOURLY.last -> (min + 7) / 15 * 15
        else -> snap(min)
    }
}

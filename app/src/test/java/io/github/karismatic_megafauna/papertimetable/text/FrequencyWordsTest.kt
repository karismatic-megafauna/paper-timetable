package io.github.karismatic_megafauna.papertimetable.text

import io.github.karismatic_megafauna.papertimetable.pack.FreqRow
import io.github.karismatic_megafauna.papertimetable.text.FrequencyWords.band
import io.github.karismatic_megafauna.papertimetable.text.FrequencyWords.snap
import io.github.karismatic_megafauna.papertimetable.text.FrequencyWords.tag
import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyWordsTest {
    @Test fun snaps() {
        assertEquals(6, snap(7)) // ties go down
        assertEquals(8, snap(8))
        assertEquals(10, snap(11))
        assertEquals(60, snap(58))
        assertEquals(2, snap(1))
    }

    @Test fun bandWords() {
        assertEquals("every 10 min", band(36, 10))
        assertEquals("about hourly", band(6, 60))
        assertEquals("no service", band(0, null))
        assertEquals("1 trip", band(1, null))
        assertEquals("3 trips", band(3, 120))
    }

    @Test fun sixBandNames() = assertEquals(6, FrequencyWords.BAND_NAMES.size)

    @Test fun rangeTag() = assertEquals("every 5–10 min", tag(listOf(FreqRow(0, 1, 36, 5), FreqRow(0, 2, 36, 10), FreqRow(0, 3, 48, 5))))

    @Test fun rangeUpToHourlyPrintsSixty() = assertEquals("every 30–60 min", tag(listOf(FreqRow(0, 1, 6, 30), FreqRow(0, 2, 6, 60), FreqRow(0, 3, 4, 58))))

    @Test fun noRowsIsNoService() = assertEquals("no service", tag(emptyList()))

    @Test fun fewTripsCountsTheBusierDirection() =
        assertEquals("5 trips a day", tag(listOf(FreqRow(0, 1, 3, 90), FreqRow(0, 3, 2, null), FreqRow(1, 1, 2, null))))

    @Test fun nightOnlyLineFallsBackToWhateverBandsItHas() =
        assertEquals("every 20 min", tag(listOf(FreqRow(0, 4, 9, 20), FreqRow(0, 5, 9, 20))))
}

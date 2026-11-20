package io.github.karismatic_megafauna.papertimetable.pack

/**
 * A trip stores the seconds between consecutive stops as unsigned LEB128
 * varints. This is the only decoder; the converter's encoder is
 * converter/src/packbuilder/patterns.py.
 */
object Varint {
    /** Seconds from the trip's first departure to stop [index] (0 for the first stop). */
    fun offsetAt(deltas: ByteArray, index: Int): Int {
        var total = 0
        var read = 0
        var value = 0
        var shift = 0
        var i = 0
        while (read < index) {
            require(i < deltas.size) { "trip has ${read + 1} stops, asked for stop $index" }
            val b = deltas[i++].toInt()
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 != 0) {
                shift += 7
            } else {
                total += value
                value = 0
                shift = 0
                read++
            }
        }
        return total
    }
}

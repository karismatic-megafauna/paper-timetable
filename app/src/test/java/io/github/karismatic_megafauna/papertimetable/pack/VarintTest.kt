package io.github.karismatic_megafauna.papertimetable.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VarintTest {
    private val blob = byteArrayOf(120, 0xB4.toByte(), 0x01)

    @Test fun offsets() {
        assertEquals(0, Varint.offsetAt(blob, 0))
        assertEquals(120, Varint.offsetAt(blob, 1))
        assertEquals(300, Varint.offsetAt(blob, 2))
    }

    @Test fun emptyBlobHasOnlyTheFirstStop() = assertEquals(0, Varint.offsetAt(byteArrayOf(), 0))

    @Test fun indexPastTheEndIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Varint.offsetAt(blob, 3) }
    }
}

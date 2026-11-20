package io.github.karismatic_megafauna.papertimetable

import io.github.karismatic_megafauna.papertimetable.pack.PackDb
import io.github.karismatic_megafauna.papertimetable.time.ServiceClock
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The fixture pack built by the converter from converter/tests/fixtures/mini (see the plan, Task 1). */
object TestPacks {
    val LA: ZoneId = ZoneId.of("America/Los_Angeles")

    fun tempDir(): File = Files.createTempDirectory("packs").toFile()

    fun miniFile(dir: File = tempDir(), name: String = "mini.pack"): File {
        val out = File(dir, name)
        TestPacks::class.java.getResourceAsStream("/mini.pack")!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    fun mini(): PackDb = PackDb.open(miniFile())

    fun at(text: String): LocalDateTime = LocalDateTime.parse(text)

    fun clockAt(local: String): ServiceClock {
        val instant = at(local).atZone(LA).toInstant()
        return ServiceClock(LA) { instant }
    }

    val anyClock = ServiceClock(LA) { Instant.parse("2026-11-05T00:03:00Z") }
}

package io.github.karismatic_megafauna.papertimetable.pack

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Keeps the installed schedule packs. Packs live at `<dir>/<agency>.pack` (so
 * more than one can be held later) and `<dir>/current` names the one in use.
 *
 * The app ships with a pack. [bundledStamp] identifies the app build; the
 * bundled pack is only looked at again when the stamp changes, and only
 * installed when it is newer than what is already there.
 */
class PackStore(private val dir: File, private val bundledStamp: String, private val bundled: () -> InputStream) {
    private var open: PackDb? = null
    private val currentFile get() = File(dir, "current")
    private val stampFile get() = File(dir, "bundled.stamp")

    private fun installedFile(): File? =
        currentFile.takeIf { it.exists() }?.readText()?.trim()?.let { File(dir, "$it.pack") }?.takeIf { it.exists() }

    private fun stampChanged() = !stampFile.exists() || stampFile.readText() != bundledStamp

    /**
     * True when the next [current] has to copy the bundled pack out of the app
     * (first launch, or the first launch of a new build). That takes a moment,
     * so the caller can do it off the main thread.
     */
    @Synchronized
    fun needsInstall(): Boolean = open == null && (installedFile() == null || stampChanged())

    @Synchronized
    fun current(): PackDb {
        open?.let { return it }
        dir.mkdirs()
        var installed = installedFile()
        if (installed == null || stampChanged()) {
            val candidate = File(dir, "bundled.tmp")
            try {
                bundled().use { input -> candidate.outputStream().use { input.copyTo(it) } }
                val shipped = PackDb.open(candidate).use { it.meta }
                val existing = installed?.let { runCatching { PackDb.open(it).use { db -> db.meta } }.getOrNull() }
                if (existing == null || existing.agencyId != shipped.agencyId || shipped.builtAt.isAfter(existing.builtAt)) {
                    installed = install(candidate, shipped.agencyId)
                }
                stampFile.writeText(bundledStamp)
            } catch (e: IOException) {
                // No room to look at the bundled pack (a full phone after an upgrade, say). The pack
                // already installed still works; the stamp is left alone so the next launch tries again.
                if (installed == null) throw e
            } finally {
                candidate.delete()
            }
        }
        return PackDb.open(installed!!).also { open = it }
    }

    /**
     * Makes [candidate] the pack in use and returns it. It is opened first: if
     * it is not a pack this app can read, the exception propagates and nothing
     * changes.
     *
     * The pack that was in use is deliberately NOT closed here. This runs on a
     * background thread while the screens are still drawing from the old pack;
     * its open handle keeps working after the file is replaced. Whoever was
     * using it closes it once they have switched (see AppState.packReplaced).
     */
    @Synchronized
    fun replaceWith(candidate: File): PackDb {
        val agency = PackDb.open(candidate).use { it.meta.agencyId }
        val installed = install(candidate, agency)
        return PackDb.open(installed).also { open = it }
    }

    private fun install(candidate: File, agencyId: String): File {
        dir.mkdirs()
        val target = File(dir, "$agencyId.pack")
        if (!candidate.renameTo(target)) { // different volume: copy, then swap
            val staged = File(dir, "$agencyId.pack.new")
            candidate.copyTo(staged, overwrite = true)
            if (!staged.renameTo(target)) throw PackException("could not install ${target.name}")
            candidate.delete()
        }
        currentFile.writeText(agencyId)
        return target
    }
}

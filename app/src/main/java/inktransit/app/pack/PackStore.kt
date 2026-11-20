package inktransit.app.pack

import java.io.File
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

    @Synchronized
    fun current(): PackDb {
        open?.let { return it }
        dir.mkdirs()
        var installed = currentFile.takeIf { it.exists() }?.readText()?.trim()?.let { File(dir, "$it.pack") }?.takeIf { it.exists() }
        val stampChanged = !stampFile.exists() || stampFile.readText() != bundledStamp
        if (installed == null || stampChanged) {
            val candidate = File(dir, "bundled.tmp")
            bundled().use { input -> candidate.outputStream().use { input.copyTo(it) } }
            val shipped = PackDb.open(candidate).use { it.meta }
            val existing = installed?.let { runCatching { PackDb.open(it).use { db -> db.meta } }.getOrNull() }
            if (existing == null || existing.agencyId != shipped.agencyId || shipped.builtAt.isAfter(existing.builtAt)) {
                installed = install(candidate, shipped.agencyId)
            } else {
                candidate.delete()
            }
            stampFile.writeText(bundledStamp)
        }
        return PackDb.open(installed!!).also { open = it }
    }

    /**
     * Makes [candidate] the pack in use. It is opened first: if it is not a
     * pack this app can read, the exception propagates and nothing changes.
     */
    @Synchronized
    fun replaceWith(candidate: File): PackDb {
        val agency = PackDb.open(candidate).use { it.meta.agencyId }
        open?.close()
        open = null
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

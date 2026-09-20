package org.churchpresenter.calendar

import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.withPathsRelativeTo
import org.churchpresenter.calendar.model.withPathsResolvedFrom
import org.churchpresenter.core.models.io.writeTextAtomically
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** How many previous versions of the calendar survive beside the current one. */
internal const val BACKUPS_KEPT = 3

/** The one file this module keeps; [CalendarFileWatcher] watches for it by name. */
internal const val CALENDAR_FILE = "calendar.json"
private const val BACKUP_SUFFIX = ".bak"
private const val CORRUPT_SUFFIX = ".corrupt-"
private val CORRUPT_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/**
 * Where a [CalendarDocument] came from, so the window can say so.
 *
 * The distinction is the point of this class. A planner that silently opens empty after a bad parse
 * looks exactly like a planner that has never been used, and the user would find out that a year of
 * services was gone by not seeing them — which is the failure this store exists to make impossible.
 */
enum class CalendarSource {
    /** No file yet. A first run. */
    NEW,

    /** The current file parsed. */
    FILE,

    /** The current file did not parse; a backup did. The bad file has been quarantined. */
    RECOVERED,

    /** Neither the file nor any backup parsed. Everything unreadable has been quarantined. */
    LOST,
}

/** A load, and where it came from. */
data class CalendarLoad(val document: CalendarDocument, val source: CalendarSource)

/**
 * `calendar.json`, and the backups standing behind it.
 *
 * A separate file from `settings.json` on purpose — it is the one the user asked to be able to lose
 * without losing anything else, and it is the one a sync client is most likely to catch mid-write.
 * So: every write goes through [writeTextAtomically], and every write first rotates the previous
 * content into `calendar.json.bak1`. A parse failure is never fatal — the backups are tried oldest
 * to newest, and whatever could not be read is moved aside with a timestamp rather than deleted, so
 * a file that failed for some reason this code did not anticipate is still there to be looked at.
 *
 * [folder] is a parameter rather than resolved here so the store is testable against a temp
 * directory and so this module needs no dependency on `:settings`. The app passes
 * `AppDataDir.resolve()`, which is where the rest of what the app persists already lives.
 */
class CalendarStore(private val folder: File) {

    internal val file: File = File(folder, CALENDAR_FILE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** `calendar.json.bak1` is the most recent previous version, `.bak3` the oldest kept. */
    internal fun backupFile(index: Int): File = File(folder, "$CALENDAR_FILE$BACKUP_SUFFIX$index")

    /**
     * The calendar, from the file if it parses and from the newest readable backup if it does not.
     *
     * Never throws: every outcome, including "nothing here could be read at all", is a
     * [CalendarLoad] the window can open on.
     */
    fun load(): CalendarLoad {
        if (!file.exists()) return CalendarLoad(CalendarDocument(), CalendarSource.NEW)

        decode(file)?.let { return CalendarLoad(it, CalendarSource.FILE) }

        // The current file is unreadable. Set it aside before touching anything else, so it is still
        // on disk to be inspected after this has been recovered from.
        quarantine(file)
        for (index in 1..BACKUPS_KEPT) {
            val backup = backupFile(index)
            if (!backup.exists()) continue
            decode(backup)?.let { return CalendarLoad(it, CalendarSource.RECOVERED) }
            quarantine(backup)
        }
        return CalendarLoad(CalendarDocument(), CalendarSource.LOST)
    }

    /**
     * Writes [document], rotating the previous content into the backups first.
     *
     * The rotation happens before the write and not after, so the backup is of content that was
     * definitely complete — a backup taken from a file that is in the middle of being replaced
     * would be a copy of the problem.
     */
    fun save(document: CalendarDocument) {
        folder.mkdirs()
        rotateBackups()
        val stored = document.withPathsRelativeTo(folder)
        file.writeTextAtomically(json.encodeToString(CalendarDocument.serializer(), stored))
    }

    private fun rotateBackups() {
        if (!file.exists()) return
        // Oldest first: .bak3 is dropped, .bak2 becomes .bak3, .bak1 becomes .bak2.
        backupFile(BACKUPS_KEPT).delete()
        for (index in BACKUPS_KEPT - 1 downTo 1) {
            val from = backupFile(index)
            if (from.exists()) from.renameTo(backupFile(index + 1))
        }
        runCatching { file.copyTo(backupFile(1), overwrite = true) }
    }

    /** Decodes [source], or null if it cannot be read or does not parse. */
    private fun decode(source: File): CalendarDocument? = runCatching {
        json.decodeFromString(CalendarDocument.serializer(), source.readText()).withPathsResolvedFrom(folder)
    }.getOrNull()

    /** Moves [bad] aside under a timestamped name. Best effort: a failure here must not fail a load. */
    private fun quarantine(bad: File) {
        runCatching {
            val stamp = LocalDateTime.now().format(CORRUPT_STAMP)
            bad.renameTo(File(folder, "${bad.name}$CORRUPT_SUFFIX$stamp"))
        }
    }
}

/**
 * Puts the calendar and its presets into a folder that has neither, so pointing the app at a new
 * folder starts it from what was there rather than from nothing. A file the new folder already has
 * is left alone -- joining a shared folder means taking the shared calendar, and the watcher's
 * merge is what brings this machine's own services into it afterwards. Returns whether anything
 * was copied.
 */
fun seedCalendarFolder(from: File, to: File): Boolean {
    if (from.canonicalFile == to.canonicalFile) return false
    var copied = false
    for (name in listOf(CALENDAR_FILE, PRESET_FILE)) {
        val source = File(from, name)
        val target = File(to, name)
        if (!source.isFile || target.exists()) continue
        to.mkdirs()
        source.copyTo(target)
        copied = true
    }
    return copied
}

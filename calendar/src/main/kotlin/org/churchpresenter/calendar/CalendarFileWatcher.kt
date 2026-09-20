package org.churchpresenter.calendar

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Watches `calendar.json` and `presets.json` for a write this window did not make.
 *
 * What it is for is a **shared folder**: point two machines at one Dropbox/OneDrive/Syncthing
 * directory and the other machine's save arrives here as a file change. Without this the window
 * reads the file once, when it opens, and a planner watching the screen sees nothing until they
 * reopen it — which is the difference between "it syncs" and "it syncs, eventually, if you know".
 *
 * Two details it exists to handle:
 *
 * - **Its own writes look identical.** Every save this window makes is a write to the same file, so
 *   the watcher reports them too. [savedHere] and [savedPresetsHere] are called by the stores' own
 *   save paths; a change whose content matches what was just written is not reported back.
 * - **A sync client writes more than once.** A file arriving over a network is commonly written as
 *   a temporary file and renamed, or written in parts, so one logical change is several events.
 *   They are coalesced over [QUIET_PERIOD_MS], after which the file is read once.
 */
class CalendarFileWatcher(
    private val folder: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The length of each file as this window last wrote it — see [savedHere]. */
    private val lastWrittenHere = ConcurrentHashMap<String, Long>()

    /** Called after this window saves the calendar, so its own write is not read back as somebody else's. */
    fun savedHere() = noteWritten(CALENDAR_FILE)

    /** The same for `presets.json`. */
    fun savedPresetsHere() = noteWritten(PRESET_FILE)

    private fun noteWritten(name: String) {
        lastWrittenHere[name] = File(folder, name).length()
    }

    /** Whether [name] differs from what this window last wrote, remembering the new length either way. */
    private fun changedElsewhere(name: String): Boolean {
        val length = File(folder, name).length()
        val ours = lastWrittenHere.put(name, length)
        return ours != length
    }

    /**
     * Watches until the coroutine is cancelled.
     *
     * A watch that cannot be started — a folder that is not there, a filesystem with no watch
     * service — is not an error worth showing: the window still works, it simply does not notice
     * the other machine. Editing on one machine at a time is what most of them do anyway.
     */
    suspend fun run(onChanged: suspend () -> Unit, onPresetsChanged: suspend () -> Unit = {}) {
        if (!folder.isDirectory) return
        val service = runCatching { FileSystems.getDefault().newWatchService() }.getOrNull() ?: return
        try {
            runCatching {
                folder.toPath().register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                )
            }.getOrElse { return }

            while (coroutineContext.isActive) {
                val key = withContext(io) { service.poll(POLL_MS, TimeUnit.MILLISECONDS) } ?: continue
                val events = key.pollEvents()
                key.reset()
                report(events, onChanged, onPresetsChanged)
            }
        } finally {
            runCatching { service.close() }
        }
    }

    /** What one batch of events means; the loop above hands it what the watch delivered. */
    internal suspend fun report(
        events: List<WatchEvent<*>>,
        onChanged: suspend () -> Unit,
        onPresetsChanged: suspend () -> Unit,
    ) {
        val calendar = events.any { it.touches(CALENDAR_FILE) }
        val presets = events.any { it.touches(PRESET_FILE) }
        if (!calendar && !presets) return
        // Let a sync client finish writing before reading what it wrote.
        delay(QUIET_PERIOD_MS)
        if (calendar && changedElsewhere(CALENDAR_FILE)) onChanged()
        if (presets && changedElsewhere(PRESET_FILE)) onPresetsChanged()
    }

    private fun WatchEvent<*>.touches(file: String): Boolean {
        if (kind() == StandardWatchEventKinds.OVERFLOW) return true
        val name = context()?.toString() ?: return false
        // The backups and the quarantine copies are this app's own bookkeeping; only the file
        // itself, and whatever a sync client renames into place, is a change worth reading.
        return name == file || name.startsWith("$file.sync") || name.endsWith(".tmp")
    }

    private companion object {
        /** How long to wait for a write to settle before reading it. */
        const val QUIET_PERIOD_MS = 400L

        /** How often the watch gives the coroutine a chance to be cancelled. */
        const val POLL_MS = 500L
    }
}

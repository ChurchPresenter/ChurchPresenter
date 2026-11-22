package org.churchpresenter.settings

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Moves [from] over [to], trying again for a moment when the move is refused. Windows refuses to
 * replace a file another program has open -- an antivirus scan of the file just written, the
 * search indexer, a sync client -- and lets go a moment later. Throws the last refusal.
 */
internal fun replaceWithRetries(
    from: File,
    to: File,
    attempts: Int = REPLACE_ATTEMPTS,
    pause: (Long) -> Unit = Thread::sleep,
    move: (File, File) -> Unit = ::replace,
) {
    var attempt = 1
    while (true) {
        try {
            move(from, to)
            return
        } catch (e: FileSystemException) {
            if (attempt >= attempts) throw e
            pause(REPLACE_PAUSE_MS * attempt)
            attempt++
        }
    }
}

private fun replace(from: File, to: File) {
    try {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        // Some network and FUSE filesystems cannot promise atomicity; a plain replace is
        // still a rename rather than a truncate-in-place.
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/** Pauses of 100, 200 … 700 ms between the eight tries: about three seconds in all. */
private const val REPLACE_ATTEMPTS = 8
private const val REPLACE_PAUSE_MS = 100L

package org.churchpresenter.core.models.io

import java.io.File
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Whole-file writes that a reader — or a file-sync daemon — can never catch half-finished.
 *
 * `File.writeText` truncates the target first, so anything reading it during the write sees a
 * partial document, and a sync client watching the folder replicates that partial document or
 * files it as a conflict. Every one of these files lives in `~/.churchpresenter`, which people do
 * sync between machines. Writing beside the target and moving into place makes the swap a single
 * step: the old content stands until the new content is complete.
 */

/** The scratch file [target]'s new content is built in, beside it so the move stays on one volume. */
private fun scratchFor(target: File): File = File(target.parentFile, "${target.name}.writing")

/**
 * Replaces [this] file's content with [text], atomically where the filesystem offers it.
 *
 * The scratch file is removed whatever happens, so a failure leaves the folder exactly as it was —
 * with the previous content still in place.
 */
fun File.writeTextAtomically(text: String, charset: Charset = Charsets.UTF_8) =
    writeBytesAtomically(text.toByteArray(charset))

/** [writeTextAtomically]'s binary twin. */
fun File.writeBytesAtomically(bytes: ByteArray) {
    parentFile?.mkdirs()
    val scratch = scratchFor(this)
    try {
        scratch.writeBytes(bytes)
        try {
            Files.move(
                scratch.toPath(), toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // Some network and FUSE filesystems cannot promise atomicity. A plain replace is still
            // better than a truncate-in-place: the window is a rename rather than the whole write.
            Files.move(scratch.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        scratch.delete()
    }
}

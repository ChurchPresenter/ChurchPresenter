package org.churchpresenter.slides.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.slides.utils.PictureDecoder
import java.io.File
import java.io.IOException

/** Grid tile size — the largest side a thumbnail is decoded to, never the display's resolution. */
private const val THUMBNAIL_MAX_DIMENSION = 400

/** How long to wait before re-reading a file whose first decode failed. */
private const val THUMBNAIL_RETRY_MS = 120L

/**
 * How many unreadable files one warning describes in full.
 *
 * A folder where everything failed is one problem, not a hundred, and the first handful of lines
 * already say which kind of file it is.
 */
private const val MAX_REPORTED_FAILURES = 20

/** Decodes the grid's thumbnails into [PicturesState], and reports the ones that cannot be read. */
internal class PictureThumbnails(
    private val state: PicturesState,
    /** See `PicturesViewModel`'s parameter of the same name: where every snapshot write is made. */
    private val mainDispatcher: CoroutineDispatcher,
) {

    /**
     * Decodes [file] into [PicturesState.thumbnails], or records why it could not be in
     * [PicturesState.thumbnailFailures].
     *
     * Returns a line describing the failure for [reportThumbnailFailures], or null when there is
     * nothing to report — the file decoded, or it holds no bytes at all. An empty file is a copy
     * that has not started, a cloud placeholder that has not been materialised, or a download in
     * flight; the tile still says so, but nothing about the app went wrong and reporting it buries
     * the files that genuinely could not be read.
     *
     * [attempts] exists for the folder watcher: a file being copied into a watched folder is
     * routinely seen the instant it is created and long before it is complete, so the first decode
     * of a half-written file legitimately fails and the same read succeeds moments later. The
     * initial folder load reads files that were already there and needs no retry.
     */
    suspend fun decodeThumbnail(file: File, attempts: Int = 1): String? {
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            try {
                publishThumbnail(file, loadImageBitmap(file))
                return null
            } catch (e: CancellationException) {
                // Disposing the view model cancels the decode. That is not a broken file, and
                // recording it would mark a working image failed and warn about it.
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt < attempts - 1) delay(THUMBNAIL_RETRY_MS)
            } catch (e: IllegalArgumentException) {
                // A file the decoder cannot make an image of.
                lastError = e
                if (attempt < attempts - 1) delay(THUMBNAIL_RETRY_MS)
            } catch (e: IllegalStateException) {
                lastError = e
                if (attempt < attempts - 1) delay(THUMBNAIL_RETRY_MS)
            }
        }
        val reason = lastError?.message ?: lastError?.toString() ?: "unknown"
        // Snapshot state, written from whichever thread decoded — same confinement as
        // [publishThumbnail].
        withContext(mainDispatcher) { state.thumbnailFailures[file] = reason }
        if (file.length() == 0L) return null
        // The reason names the file — the tile and the local log want that, a report does not, so
        // the name comes out here rather than at the reporting end, where the exception's own
        // wording could put it back at any time.
        return "${reason.replace(file.name, "<file>")} (${PictureDecoder.diagnose(file)})"
    }

    /**
     * Reports the thumbnails that could not be decoded during one load, as a single warning.
     *
     * One event per file made every file name its own Sentry issue — a folder of unreadable
     * pictures arrived as a folder of unrelated-looking problems, each titled with a name belonging
     * to the person who reported it. The title is constant so the whole class of failure groups
     * into one issue, the count is a tag, and what actually distinguishes the files is in the
     * detail, capped at [MAX_REPORTED_FAILURES] because the first few are enough to tell what kind
     * of file it is and the rest are the same line again.
     */
    fun reportThumbnailFailures(diagnostics: List<String>) {
        if (diagnostics.isEmpty()) return
        CrashReporter.reportWarning(
            "Pictures: thumbnails could not be decoded",
            tags = mapOf("subsystem" to "pictures", "failed.count" to diagnostics.size.toString()),
            extras = mapOf("files" to diagnostics.take(MAX_REPORTED_FAILURES).joinToString("\n"))
        )
    }

    /** Forgets every thumbnail and every failure, for a folder that is going away. */
    fun clear() {
        state.thumbnails.clear()
        state.thumbnailFailures.clear()
    }

    /**
     * Writes a decoded thumbnail into the state maps, on the thread that owns them.
     *
     * Both maps are snapshot state, and a write from a thread other than the one advancing the
     * global snapshot can lose that race — the *write* throws `Reading a state that was created
     * after the snapshot was taken or in a snapshot that has not yet been applied`. The decode
     * itself belongs on a background thread and stays there; only the publish hops.
     *
     * This used to retry the write four times instead, which cleared the window often enough to
     * look fixed. It was a retry around a race, it left the two other background writers
     * ([decodeThumbnail]'s failure record and the folder watcher) unprotected, and the watcher is
     * the one that took CI red. Confining every write to [mainDispatcher] removes the race rather
     * than out-waiting it.
     *
     * Writing inside `Snapshot.withMutableSnapshot` is still not the fix — that moves the identical
     * failure onto the readers, where the grid throws it out of composition.
     */
    private suspend fun publishThumbnail(file: File, bitmap: ImageBitmap) {
        withContext(mainDispatcher) {
            state.thumbnails[file] = bitmap
            state.thumbnailFailures.remove(file)
        }
    }

    private fun loadImageBitmap(file: File): ImageBitmap =
        // Grid tile size, not the display's — this is the thumbnails strip, never presented.
        PictureDecoder.decodeScaled(file, THUMBNAIL_MAX_DIMENSION, THUMBNAIL_MAX_DIMENSION).toComposeImageBitmap()
}

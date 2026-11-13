package org.churchpresenter.presentationengine.fonts

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Font files by key, for every font file under [dirs], that answers a lookup only once its own scan
 * has run to the end.
 *
 * The scan is shared: [SlideFontRegistry.initialize] runs it at startup through [scanAll], and
 * otherwise the first lookup does. An "is anything in it yet" check let a lookup trust a half-built
 * index: it missed an installed family and came back empty, and the Lottie font loader caches that
 * answer for the rest of the run, so a lower third drew its band with no text on it. Here a scan
 * walks every directory under a lock and lookups wait for it.
 *
 * @param dirs the directories to scan, read when the first lookup needs them
 * @param maxDepth how deep below each directory to look
 * @param key the key a file is indexed under
 */
internal class FontFileIndex(
    private val dirs: () -> List<File>,
    private val maxDepth: Int,
    private val key: (File) -> String,
) {
    private val files = ConcurrentHashMap<String, File>()
    private val lock = Any()

    @Volatile
    private var complete = false

    /** Records [file] under its key, unless a file is already there. */
    fun add(file: File) {
        files.putIfAbsent(key(file), file)
    }

    /** The file indexed under [key], after the whole scan has run if it has not yet. */
    fun find(key: String): File? {
        ensureComplete()
        return files[key]
    }

    private fun ensureComplete() {
        if (complete) return
        scanAll {}
    }

    /**
     * Walks every directory now, unless a scan already has, indexing each font file and handing it
     * to [onFile]; true when this call did the walk. [SlideFontRegistry.initialize] runs it on its
     * startup thread, so the first lookup -- a Lottie band laying out its text on the live output --
     * finds the index already complete instead of walking the font folders on the event thread
     * (CHURCH-PRESENTER-DESKTOP-K9).
     */
    fun scanAll(onFile: (File) -> Unit): Boolean = synchronized(lock) {
        if (complete) return false
        for (dir in dirs()) {
            if (!dir.isDirectory) continue
            dir.walkTopDown().maxDepth(maxDepth)
                .filter { it.isFile && it.extension.lowercase() in FONT_FILE_EXTENSIONS }
                .forEach { file ->
                    add(file)
                    onFile(file)
                }
        }
        complete = true
        true
    }

    companion object {
        /** The file types a font directory scan picks up. */
        val FONT_FILE_EXTENSIONS = setOf("ttf", "otf", "ttc")
    }
}

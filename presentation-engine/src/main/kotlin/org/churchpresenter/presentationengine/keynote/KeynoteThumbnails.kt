package org.churchpresenter.presentationengine.keynote

import java.io.File

/** Which files in a `.key` are slide thumbnails, and the order the slides they belong to run in. */
internal object KeynoteThumbnails {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "tiff", "tif")

    /** A zip entry under `Data/` named `st-…` with an image extension. */
    fun isZipThumbnail(name: String, base: String): Boolean {
        if (!base.lowercase().startsWith("st-")) return false
        if (base.substringAfterLast(".", "").lowercase() !in IMAGE_EXTENSIONS) return false
        // Thumbnails live in the Data/ directory of the bundle.
        return name.substringBeforeLast("/", "").endsWith("Data")
    }

    /** A package directory's `Data/st-…` image. */
    fun isPackageThumbnail(file: File): Boolean =
        file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS && file.name.lowercase().startsWith("st-")

    /**
     * Maps thumbnails into presentation order: `st-` files sorted by trailing id have the same
     * rank as the sorted `Slide-<id>.iwa` ids; the iwa entries' zip order is presentation order.
     */
    fun order(thumbnails: List<String>, slideIwaOrder: List<Long>): List<String> {
        if (thumbnails.isEmpty()) return thumbnails
        val sortedByStId = thumbnails.sortedBy { entry ->
            entry.substringAfterLast("/").substringBeforeLast(".").split("-").lastOrNull()?.toLongOrNull()
                ?: Long.MAX_VALUE
        }
        if (slideIwaOrder.isEmpty()) return sortedByStId
        val iwaSorted = slideIwaOrder.sorted()
        val rankToThumbnail = sortedByStId.mapIndexed { rank, entry -> rank to entry }.toMap()
        val main = slideIwaOrder.mapNotNull { id -> rankToThumbnail[iwaSorted.indexOf(id)] }.distinct()
        return main + thumbnails.filter { it !in main }
    }

    /**
     * Modern thumbnails when there are any, otherwise the ones a legacy document names for itself.
     *
     * Keynote '09 puts per-slide thumbnails in `thumbs/` as `st<n>.jpg` / `st<n>-<m>.jpg`, which
     * matches neither the `Data/` location nor the `st-` prefix the modern rule looks for — so
     * before this, a legacy deck with nine perfectly good thumbnails was reported as having none
     * and failed to open at all, with a message saying there were no thumbnails.
     *
     * Their names cannot be sorted into slide order (a real document runs `st2-1, st2-2, st3, …,
     * st2, st7, st6, st186`), but they do not need to be: the apxl states the slide-to-thumbnail
     * mapping itself, in document order. That is the only reliable source, and the same file is
     * already parsed for notes.
     */
    fun resolve(modern: List<String>, apxlXml: String?, exists: (String) -> Boolean): List<String> {
        if (modern.isNotEmpty()) return modern
        val declared = apxlXml?.let { ApxlManifest.parseThumbnails(it) } ?: return modern
        // Only offer entries the document actually contains; a stale reference must not become a
        // blank slide in the middle of a deck.
        return declared.filter(exists)
    }
}

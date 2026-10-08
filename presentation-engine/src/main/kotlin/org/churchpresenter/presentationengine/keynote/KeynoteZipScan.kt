package org.churchpresenter.presentationengine.keynote

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** What one pass over a zipped `.key`'s entries collects for [KeynoteStaticSupport.analyze]. */
internal class KeynoteZipScan {
    var hasPreviewPdf = false
        private set
    val slideIwaOrder = mutableListOf<Long>()
    val thumbnailEntries = mutableListOf<String>()
    var apxlXml: String? = null
        private set
    val iwaNotes = mutableMapOf<Long, String>()
    val allEntryNames = mutableSetOf<String>()

    /** Records [entry], a file (not a directory) of [zip]. */
    fun accept(zip: ZipFile, entry: ZipEntry) {
        val name = entry.name
        allEntryNames.add(name)
        val base = name.substringAfterLast("/")
        when {
            name.equals(KeynoteStaticSupport.PREVIEW_PDF_ENTRY, ignoreCase = true) && entry.size != 0L ->
                hasPreviewPdf = true

            base.startsWith("Slide-") && base.endsWith(".iwa") ->
                KeynoteStaticSupport.slideIwaId(base)?.let { slideIwaOrder.add(it) }

            base == "Slide.iwa" ->
                iwaNotes[-1L] = IwaNoteScanner.scan(zip.getInputStream(entry).readBytes())

            KeynoteThumbnails.isZipThumbnail(name, base) ->
                thumbnailEntries.add(name)

            base.equals("index.apxl", ignoreCase = true) && apxlXml == null ->
                apxlXml = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
        }
    }
}

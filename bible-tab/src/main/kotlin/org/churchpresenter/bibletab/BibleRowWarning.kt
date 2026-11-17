package org.churchpresenter.bibletab

import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.SpbRowReport
import java.io.File

/**
 * A translation that loaded, but with verse rows [Bible] could not read, so some verses are missing.
 *
 * Not a [org.churchpresenter.bible.BibleLoadError]: the module opened and mostly works, which is
 * exactly why nobody notices a verse is gone. This is what tells the operator, and what the banner lists.
 *
 * @property fileName the translation's own file name, which is what the operator recognises.
 * @property path where it is on disk.
 * @property report how many rows, and where the first few are.
 * @property bible the loaded module, for naming the book of an example.
 */
internal data class BibleRowWarning(
    val fileName: String,
    val path: String,
    val report: SpbRowReport,
    val bible: Bible,
) {
    /** The damaged rows as a reader would look for them: "2 Corinthians 10:5", in file order. */
    val verses: List<String>
        get() = report.examples.map { row ->
            val book = bible.getBookName(row.book) ?: row.book.toString()
            "$book ${row.chapter}:${row.verse}"
        }

    /** How many damaged rows [verses] leaves out, because only the first few are kept. */
    val moreCount: Int get() = (report.total - report.examples.size).coerceAtLeast(0)
}

/** The warnings for every loaded translation that has damaged rows, in the order they loaded. */
internal fun rowWarningsFor(
    sources: List<Pair<String, File>>,
    loaded: List<BibleViewModel.LoadedTranslation>,
): List<BibleRowWarning> = sources.mapNotNull { (identity, file) ->
    val bible = loaded.firstOrNull { it.fileName == identity }?.bible ?: return@mapNotNull null
    bible.rowReport.takeUnless { it.isClean }?.let { BibleRowWarning(file.name, file.absolutePath, it, bible) }
}

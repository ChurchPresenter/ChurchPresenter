package org.churchpresenter.planningcenter.ui

import org.churchpresenter.planningcenter.BookAbbreviationResolver
import org.churchpresenter.planningcenter.PlanningCenterPrimaryBible
import java.io.File

/**
 * What the import needs from the app that hosts it: the OAuth client the app is registered as,
 * the scripture found in a plan item's text, and how many slides a downloaded deck has.
 *
 * Scripture is looked up in the operator's own Bible ([planningCenterServices] builds that), and
 * slides are counted by the presentation engine, which stays with the app.
 */
class PlanningCenterImportServices(
    val clientId: String,
    val clientSecret: String,
    val detectScriptures: suspend (text: String) -> List<PlanningCenterScripture> = { emptyList() },
    val countSlides: (deck: File) -> Int = { 0 },
)

/**
 * A run of verses found in a plan item's text and read from the operator's Bible, ready to become a
 * schedule item.
 */
data class PlanningCenterScripture(
    val bookName: String,
    val bookId: Int,
    val chapter: Int,
    val verseNumber: Int,
    val verseText: String,
    val verseRange: String,
) {
    /** Short human-readable reference for a picker UI, e.g. "Psalm 23:1-6" or "John 3:16". */
    val displayReference: String
        get() = if (verseRange.isNotEmpty()) "$bookName $chapter:$verseRange" else "$bookName $chapter:$verseNumber"
}

/**
 * The services an app hands the import: its OAuth client, the operator's primary Bible — loaded once
 * per import, with a book typed short read through [resolveAbbreviation] — and its slide counter.
 */
fun planningCenterServices(
    clientId: String,
    clientSecret: String,
    resolveAbbreviation: BookAbbreviationResolver,
    countSlides: (deck: File) -> Int,
) = PlanningCenterImportServices(
    clientId = clientId,
    clientSecret = clientSecret,
    detectScriptures = PlanningCenterPrimaryBible(resolveAbbreviation)::detect,
    countSlides = countSlides,
)

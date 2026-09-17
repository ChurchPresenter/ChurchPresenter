package org.churchpresenter.calendar

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File

/**
 * Everything the planner needs from whoever is hosting it.
 *
 * Lambdas rather than an interface, and every one defaulted, so the window composes in a test and
 * standalone without a host at all. This is the whole surface between `:calendar` and the app: the
 * planner reads the song folder itself (that is just files), and the two things it cannot do on its
 * own — put a service into the live Schedule tab, and see what is in it — come through here.
 *
 * Deliberately **not** a ViewModel. `AGENT.md` forbids passing one across a boundary, and there is
 * no need: the app closes over its `ScheduleActions` when it builds this.
 */
data class CalendarHost(
    /**
     * Puts a planned run of show into the Schedule tab.
     *
     * [replace] true clears the current schedule first; false appends. The window asks the user
     * which, and only when there is something to lose — see `LoadServiceConfirm`.
     */
    val loadIntoSchedule: (items: List<ScheduleItem>, replace: Boolean) -> Unit = { _, _ -> },

    /**
     * Puts one item on screen, the way a tap on it in the Schedule tab would.
     *
     * What a fired cue does — see [CueRunner]. The app routes it through the same
     * `executeProjectItem` its remote clients use, so a cue can show exactly what a phone can.
     */
    val projectItem: (ScheduleItem) -> Unit = {},

    /** Clears every output — a [org.churchpresenter.calendar.model.CueAction.BLANK] cue. */
    val blankOutputs: () -> Unit = {},

    /**
     * What is in the Schedule tab right now.
     *
     * Two callers: the confirm dialog, which needs to know whether replacing would discard
     * anything, and "capture the current schedule", which is how a service built live during a
     * Sunday gets saved back onto the calendar as next week's starting point.
     */
    val currentSchedule: () -> List<ScheduleItem> = { emptyList() },

    /**
     * The TrueType font the PDF export embeds, regular or bold, or null to fall back.
     *
     * Supplied rather than bundled because the app already ships a Cyrillic-capable face
     * (`OpenSans`) and this module would otherwise carry a second copy of it. It matters: PDFBox's
     * built-in Helvetica is WinAnsi-encoded and **throws** on the first Cyrillic character, so for
     * a library like this one an un-embedded export is not a degraded export, it is a crash.
     *
     * Returning null is honest — [org.churchpresenter.calendar.model.exportRunOfShowPdf] then falls
     * back to Helvetica and replaces whatever it cannot encode.
     */
    val pdfFont: (bold: Boolean) -> ByteArray? = { null },

    /**
     * The books of the primary Bible, for the picker's book / chapter / verse grids.
     *
     * Supplied rather than loaded here: `:calendar` has no Bible and no business opening one just
     * so somebody can plan a reading. An empty list is a working state, not a broken one — the
     * picker then falls back to accepting a typed reference, which is all it could do anyway.
     */
    val bibleBooks: () -> List<CalendarBibleBook> = { emptyList() },

    /**
     * Where to save an export, or null if the user cancelled. Shown a suggested file name.
     *
     * `suspend` because the app's own `FileChooser.save` is — a native save dialog is not something
     * to run on the composing thread.
     */
    val chooseExportFile: suspend (suggestedName: String) -> File? = { null },
)

/**
 * One book of the primary Bible, flattened to exactly what the picker draws.
 *
 * [verseCounts] is indexed by chapter, so `verseCounts[0]` is chapter 1's verse count and
 * `verseCounts.size` is the chapter count — one structure rather than two parallel lookups.
 */
data class CalendarBibleBook(
    /** The canonical, Bible-agnostic book id that [ScheduleItem.BibleVerseItem] stores. */
    val bookId: Int,
    val name: String,
    val verseCounts: List<Int>,
) {
    val chapterCount: Int get() = verseCounts.size

    fun verseCount(chapter: Int): Int = verseCounts.getOrElse(chapter - 1) { 0 }
}

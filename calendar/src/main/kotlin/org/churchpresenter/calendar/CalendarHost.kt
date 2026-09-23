package org.churchpresenter.calendar

import org.churchpresenter.calendar.ui.PreviewSources
import org.churchpresenter.core.models.schedule.RowTiming
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
     * Puts a planned run of show -- rows and cue rows alike -- into the Schedule tab.
     *
     * [replace] true clears the current schedule first; false appends. The window asks the user
     * which, and only when there is something to lose — see `LoadServiceConfirm`. [armed] is the
     * service's own switch, carried over so what was armed on the calendar is armed in the
     * Schedule, where the cues actually fire from. [timing] is how each row runs -- start, length,
     * repeats, end -- keyed by row id, for the rows that have one. [startTime] is the service's
     * `HH:mm` start, which the Schedule reckons its clock times from when no row is pinned --
     * null when the load has no service behind it.
     */
    val loadIntoSchedule: (
        items: List<ScheduleItem>,
        timing: Map<String, RowTiming>,
        replace: Boolean,
        armed: Boolean,
        startTime: String?,
    ) -> Unit = { _, _, _, _, _ -> },

    /**
     * Puts one item on screen, the way a tap on it in the Schedule tab would, and plays it
     * [plays] times — 1 once, 0 until something else goes live — where the item has a run to play.
     *
     * What a fired cue does — see `fireCue`. The app routes it through the same
     * `executeProjectItem` its remote clients use, so a cue can show exactly what a phone can.
     */
    val projectItem: (item: ScheduleItem, plays: Int) -> Unit = { _, _ -> },

    /** Clears every output — a [org.churchpresenter.core.models.schedule.CueAction.BLANK] cue. */
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
     * back to Helvetica, and anything that cannot encode is drawn as outlines from a system font.
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
     * Where a failure the calendar recovers from is told to somebody, with what was being attempted.
     *
     * Supplied because `:calendar` has no crash reporting of its own. The window carries on either
     * way; this is what stops an export that quietly wrote nothing from being indistinguishable
     * from one that worked.
     */
    val reportError: (context: String, error: Throwable) -> Unit = { _, _ -> },

    /**
     * Where to save an export, or null if the user cancelled. Shown a suggested file name.
     *
     * `suspend` because the app's own `FileChooser.save` is — a native save dialog is not something
     * to run on the composing thread.
     */
    val chooseExportFile: suspend (suggestedName: String) -> File? = { null },

    /** An image for the PDF letterhead's logo, or null if the user cancelled. */
    val chooseImageFile: suspend () -> File? = { null },

    /**
     * Where a row's file has gone, or null if the user gave up looking. The pre-flight check's
     * one-click fix for a clip or a deck that was moved: [missing] is the path the row still
     * holds, which is where the dialog opens -- the file is usually a folder away.
     *
     * `suspend` for the same reason as [chooseExportFile].
     */
    val locateFile: suspend (missing: File) -> File? = { null },

    /** As [locateFile], for a picture folder. */
    val locateFolder: suspend (missing: File) -> File? = { null },

    /**
     * The canonical book id (1–66) a typed book name means, or null when nothing recognises it.
     *
     * A reference typed into the picker -- `Psalm 91:1-4` -- stores its book as text, in whatever
     * language it was typed, and the primary Bible may name its books in another. The app
     * resolves that at go-live from its abbreviation tables (the display language, then English),
     * and the pre-flight check has to judge a row by the same rule or it flags scripture that
     * would have shown perfectly well. `suspend` because those tables are Compose resources.
     */
    val resolveBookId: suspend (name: String) -> Int? = { null },

    /**
     * How long [ScheduleItem] runs by itself, in whole seconds, or null when nothing can say.
     *
     * The app answers it because the answer is the app's: a clip's duration comes from ffmpeg,
     * which it already ships for cameras, and a picture folder's is its image count times the
     * slideshow interval the operator has set. A planned length is what the run of show's clock
     * times are built from and what the engine measures a row's run by, so anything that can know
     * its own length should never have to be timed by hand.
     *
     * `suspend` because reading a file header is not something to do on the composing thread.
     */
    val itemRunSeconds: suspend (item: ScheduleItem) -> Int? = { null },

    /**
     * How long [ScheduleItem] has actually stayed on screen here, in whole seconds, or null until
     * it has been seen enough to say.
     *
     * Distinct from [itemRunSeconds], which answers "how long is it" from the clip's own header
     * and falls back to this. This one is only ever what happened -- a song's four minutes and
     * fifty seconds, measured across the Sundays it was sung -- which is what the run of show
     * shows beside a planned length that differs from it, and offers to plan instead.
     *
     * `suspend` for the same reason as its sibling: the app reads it off a log it may have to open.
     */
    val measuredSeconds: suspend (item: ScheduleItem) -> Int? = { null },

    /** What the picker's preset previews can draw with -- the app's video player and deck rasterizer. */
    val preview: PreviewSources = PreviewSources(),

    /**
     * The switch that keeps this calendar in step with the cloud and the phones, or null when the
     * app offers no such thing. Off, nothing about the calendar leaves this computer.
     */
    val cloudSync: CalendarCloudSync? = null,
)

/**
 * The cloud sync switch as the settings dialog draws it: what it reads, what a flip does, and
 * [invitePhone], which has the app show the QR a phone scans to be enrolled from anywhere.
 */
class CalendarCloudSync(
    val enabled: () -> Boolean,
    val setEnabled: (Boolean) -> Unit,
    val invitePhone: () -> Unit = {},
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

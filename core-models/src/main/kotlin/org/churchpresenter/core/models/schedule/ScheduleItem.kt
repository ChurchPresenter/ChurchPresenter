package org.churchpresenter.core.models.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline

private const val TEXT_PREVIEW_CHARS = 50
private const val TITLE_PREVIEW_CHARS = 60

@Serializable
sealed class ScheduleItem {
    abstract val id: String
    abstract val displayText: String

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.SongItem")
    data class SongItem(
        override val id: String,
        val songNumber: Int,
        val title: String,
        val songbook: String,
        /** Stable unique ID = "songbook::number". Empty on old saved schedules — fall back to title+number matching. */
        val songId: String = "",
        override val displayText: String = if (songNumber > 0) "$songNumber - $title" else title
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.BibleVerseItem")
    data class BibleVerseItem(
        override val id: String,
        val bookName: String,
        val chapter: Int,
        val verseNumber: Int,
        val verseText: String,
        /**
         * Formatted range string for multi-verse items, e.g. "16-18" or "16,18,20".
         * Empty for a single verse — [verseNumber] is used for display in that case.
         */
        val verseRange: String = "",
        /**
         * Canonical (bible-agnostic) book id, e.g. from [org.churchpresenter.app.churchpresenter.data.Bible.getBookId].
         * 0 means "unknown" — either an old saved schedule predating this field, or an item that
         * arrived via a remote/companion DTO that doesn't carry it. Lookup falls back to matching
         * [bookName] by text against the current primary Bible's book list in that case, which only
         * works when the primary Bible's language hasn't changed since the item was added.
         */
        val bookId: Int = 0,
        override val displayText: String =
            if (verseRange.isNotEmpty()) "$bookName $chapter:$verseRange"
            else "$bookName $chapter:$verseNumber"
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.LabelItem")
    data class LabelItem(
        override val id: String,
        val text: String,
        val textColor: String,
        val backgroundColor: String,
        override val displayText: String = text
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.PictureItem")
    data class PictureItem(
        override val id: String,
        val folderPath: String,
        val folderName: String,
        val imageCount: Int,
        override val displayText: String = "$folderName ($imageCount images)"
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.PresentationItem")
    data class PresentationItem(
        override val id: String,
        val filePath: String,
        val fileName: String,
        val slideCount: Int,
        val fileType: String, // "ppt", "pptx", "key", "pdf"
        override val displayText: String = "$fileName ($slideCount slides)"
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.MediaItem")
    data class MediaItem(
        override val id: String,
        val mediaUrl: String,       // local path or URL
        val mediaTitle: String,
        val mediaType: String,      // "local", "youtube", "vimeo"
        // No glyph prefix. The row draws a type chip carrying the same clapperboard, so a marked
        // title put two of them side by side — which is not what the other kinds do, and not what
        // the chip is for. It was added when the list had no chip to tell kinds apart.
        override val displayText: String = mediaTitle,
        val subtitleUrl: String = "" // external subtitle file, blank for none
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.LowerThirdItem")
    data class LowerThirdItem(
        override val id: String,
        val presetId: String,
        val presetLabel: String,
        val pauseAtFrame: Boolean,
        val pauseDurationMs: Long,
        // As with MediaItem above: the chip already draws this triangle.
        override val displayText: String = presetLabel
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.AnnouncementItem")
    data class AnnouncementItem(
        override val id: String,
        val text: String,
        val textColor: String = "#FFFFFF",
        val backgroundColor: String = "#000000",
        val fontSize: Int = 48,
        val fontType: String = "Arial",
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val shadow: Boolean = false,
        val shadowColor: String = "#000000",
        val shadowSize: Int = 100,
        val shadowOpacity: Int = 78,
        val horizontalAlignment: String = "center",
        val position: String = "center",
        val animationType: String = "SLIDE_FROM_BOTTOM",
        val animationDuration: Int = 500,
        val loopCount: Int = 0,
        val isTimer: Boolean = false,
        val timerHours: Int = 0,
        val timerMinutes: Int = 0,
        val timerSeconds: Int = 0,
        val timerTextColor: String = "#FFFFFF",
        val timerExpiredText: String = "",
        val timerMode: String = TimerModes.DURATION,
        val targetHour: Int = 0,
        val targetMinute: Int = 0,
        val targetSecond: Int = 0,
        val liveClockFormat: String = "HH:mm:ss",
        /** The band and box drawn behind the text, kept with the rest of its look. */
        val backdrop: TextBackdrop = TextBackdrop(),
        val outline: TextOutline = TextOutline(),
        override val displayText: String = if (isTimer) {
            when (timerMode) {
                TimerModes.CLOCK -> "Until %02d:%02d:%02d".format(targetHour, targetMinute, targetSecond)
                TimerModes.COUNT_UP -> "Duration Timer"
                TimerModes.CLOCK_DISPLAY -> "Clock"
                else -> if (timerHours > 0) {
                    "Timer %d:%02d:%02d".format(timerHours, timerMinutes, timerSeconds)
                } else {
                    "Timer %02d:%02d".format(timerMinutes, timerSeconds)
                }
            }
        } else {
            "${text.take(TEXT_PREVIEW_CHARS)}${if (text.length > TEXT_PREVIEW_CHARS) "…" else ""}"
        }
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.WebsiteItem")
    data class WebsiteItem(
        override val id: String,
        val url: String,
        val title: String = url,
        override val displayText: String = websiteDisplayText(title)
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.SceneItem")
    data class SceneItem(
        override val id: String,
        val sceneId: String,
        val sceneName: String,
        override val displayText: String = "Scene: $sceneName"
    ) : ScheduleItem()

    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.DictionaryItem")
    data class DictionaryItem(
        override val id: String,
        val number: String,
        val word: String,
        val transliteration: String,
        val definition: String,
        override val displayText: String = "$word ($number)"
    ) : ScheduleItem()

    /**
     * Something in the order of service that does not go on screen -- a poem read, a violin
     * solo, a prayer, a word of welcome. Planned and timed like any other row, so the run of
     * show's clock and its printed order carry it; **never loaded into the Schedule tab**, which
     * holds only what the outputs can show. The kind of row a musician or reader adds from a
     * phone so their slot is on the plan.
     *
     * [detail] is who, or how -- `Anna, violin`.
     */
    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.MinistryItem")
    data class MinistryItem(
        override val id: String,
        val title: String,
        val detail: String = "",
        override val displayText: String = title
    ) : ScheduleItem()

    /**
     * A timed action: at [absoluteTime] -- or [offsetMinutes] from the service's start until it is
     * pinned -- do [action], with [payload] as what it puts on screen where the action shows
     * something. A row like any other, so a planned service and the live schedule carry their
     * automation in the same list as their songs, and loading one into the other is a copy.
     *
     * [payload] is a copy of the row it was chosen from, not a reference: a cue keeps firing what
     * it was set to even if that row is later replaced.
     */
    @Serializable
    @SerialName("org.churchpresenter.app.churchpresenter.models.ScheduleItem.CueItem")
    data class CueItem(
        override val id: String,
        /** A [CueAction] constant. Strings rather than an enum so a file written by a later version still opens. */
        val action: String,
        val label: String = "",
        /** Minutes relative to the service start; negative is before it. Ignored once [absoluteTime] is set. */
        val offsetMinutes: Int = 0,
        /** `09:45` on the wall clock. Set when pinned by hand, and when the row is loaded into the live schedule. */
        val absoluteTime: String = "",
        val payload: ScheduleItem? = null,
        /** How many times a shown item plays through: 1 once, 0 until something else goes live, N that many. */
        val plays: Int = 1,
        /** Off is "skip this one" -- the row stays, greyed, and fires again once re-ticked. */
        val enabled: Boolean = true,
        override val displayText: String = label.ifBlank { action }
    ) : ScheduleItem() {
        fun isPinned(): Boolean = absoluteTime.isNotEmpty()
    }
}

/**
 * What a [ScheduleItem.CueItem] does. An unknown action is simply never fired.
 */
object CueAction {
    /**
     * Puts [ScheduleItem.CueItem.payload] -- a slideshow, a deck, a clip -- on screen, and plays it
     * [ScheduleItem.CueItem.plays] times.
     */
    const val PROJECT = "project"
    /** Starts a countdown: the payload if it is a timer, otherwise one counting to the service's start. */
    const val COUNTDOWN = "countdown"
    /**
     * Loads the run of show into the Schedule and puts an item on screen -- the payload, else the
     * first row that can be projected.
     */
    const val GO_LIVE = "goLive"
    /** Puts a canvas scene -- the payload, a scene item -- on screen. */
    const val SCENE = "scene"
    /** Clears every output. */
    const val BLANK = "blank"
    const val OBS_SCENE = "obsScene"
    const val ATEM_KEY = "atemKey"

    /** The actions offered in the cue sheet, in the design's order. */
    val offered: List<String> = listOf(COUNTDOWN, PROJECT, GO_LIVE, SCENE, BLANK)

    /** The actions that point at an item, and so show a target list. */
    val withTarget: Set<String> = setOf(COUNTDOWN, PROJECT, GO_LIVE, SCENE)

    /** The actions whose item has a run to play, and so take a play count. */
    val withPlays: Set<String> = setOf(PROJECT, GO_LIVE)
}

/** The [ScheduleItem.CueItem.plays] value that means "keep going". */
const val LOOP_FOREVER: Int = 0

/**
 * The schedule row's label for a website, truncated so a long page title cannot push the row out of
 * shape.
 *
 * Named rather than inlined into [ScheduleItem.WebsiteItem]'s default because `copy()` does **not**
 * re-apply constructor defaults — it carries the current instance's value for every parameter it is
 * not given. Anything that changes the title therefore has to pass the new label explicitly, and
 * both sides have to derive it the same way or the row and the title drift apart.
 */
fun websiteDisplayText(title: String): String =
    "${title.take(TITLE_PREVIEW_CHARS)}${if (title.length > TITLE_PREVIEW_CHARS) "…" else ""}"


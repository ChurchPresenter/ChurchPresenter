package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_to_schedule
import org.churchpresenter.strings.generated.resources.helper_hint_add_to_schedule
import org.churchpresenter.strings.generated.resources.helper_hint_schedule_add
import org.churchpresenter.strings.generated.resources.helper_hint_schedule_add_files
import org.churchpresenter.strings.generated.resources.helper_hint_schedule_new
import org.churchpresenter.strings.generated.resources.helper_hint_schedule_notes
import org.churchpresenter.strings.generated.resources.helper_hint_schedule_open
import org.churchpresenter.strings.generated.resources.helper_hint_schedule_remove
import org.churchpresenter.strings.generated.resources.helper_hint_schedule_reorder
import org.churchpresenter.strings.generated.resources.helper_hint_schedule_save
import org.churchpresenter.strings.generated.resources.schedule_add_files
import org.jetbrains.compose.resources.StringResource

/**
 * "How do I add a video to the schedule", "move an item in the schedule", "save the schedule": tours
 * of the schedule and of each tab's Add to Schedule button. Planning a service ahead is the
 * Calendar's; [plan] is the empty schedule's suggestion. A request that names
 * a song or a verse to add is the add rule's, which adds it.
 */
internal object ScheduleTopics {
    /** The tour for [normalized], or null when it is not about the schedule. */
    fun find(normalized: String): GuideTour? {
        val words = normalized.split(' ')
        if (words.none { it in SCHEDULE }) return null
        return when {
            words.any { it in REMOVE } -> row(Res.string.helper_hint_schedule_remove)
            words.any { it in MOVE } -> row(Res.string.helper_hint_schedule_reorder)
            words.any { it in NOTE } -> row(Res.string.helper_hint_schedule_notes)
            words.any { it in SAVE } -> one(GuideTargets.SCHEDULE_SAVE, Res.string.helper_hint_schedule_save)
            words.any { it in NEW } -> one(GuideTargets.SCHEDULE_NEW, Res.string.helper_hint_schedule_new)
            words.any { it in OPEN } && "calendar" !in words ->
                one(GuideTargets.SCHEDULE_OPEN, Res.string.helper_hint_schedule_open)
            else -> kindOf(normalized, words)?.let(::addFrom) ?: addAnything().takeIf { words.any { it in ADD } }
        }
    }

    /** Filling today's empty schedule: the schedule, a song, a verse, then saving it. */
    fun plan() = GuideTour(
        listOf(scheduleStep()) + addFrom(Kind.SONG).steps + addFrom(Kind.VERSE).steps +
            GuideStep(GuideTargets.SCHEDULE_SAVE, helperText(Res.string.helper_hint_schedule_save)),
    )

    private enum class Kind(val tab: Tabs, val button: GuideTarget) {
        SONG(Tabs.SONGS, GuideTargets.SONGS_ADD_TO_SCHEDULE),
        VERSE(Tabs.BIBLE, GuideTargets.BIBLE_ADD_TO_SCHEDULE),
        PICTURES(Tabs.PICTURES, GuideTargets.PICTURES_ADD_TO_SCHEDULE),
        PRESENTATION(Tabs.PRESENTATION, GuideTargets.PRESENTATION_ADD_TO_SCHEDULE),
        VIDEO(Tabs.MEDIA, GuideTargets.MEDIA_ADD_TO_SCHEDULE),
        LOWER_THIRD(Tabs.LOWER_THIRD, GuideTargets.LOWER_THIRD_ADD_TO_SCHEDULE),
        ANNOUNCEMENT(Tabs.ANNOUNCEMENTS, GuideTargets.ANNOUNCEMENT_ADD_TO_SCHEDULE),
        WEBSITE(Tabs.WEB, GuideTargets.WEB_ADD_TO_SCHEDULE),
        SCENE(Tabs.CANVAS, GuideTargets.CANVAS_ADD_TO_SCHEDULE),
    }

    private fun kindOf(normalized: String, words: List<String>): Kind? = when {
        normalized.containsPhrase("lower third") -> Kind.LOWER_THIRD
        words.any { it in Vocabulary.PHOTOS } -> Kind.PICTURES
        words.any { it in Vocabulary.PRESENTATION } -> Kind.PRESENTATION
        words.any { it in Vocabulary.VIDEO } -> Kind.VIDEO
        words.any { it in ANNOUNCEMENT } -> Kind.ANNOUNCEMENT
        words.any { it in WEBSITE } -> Kind.WEBSITE
        words.any { it in SCENE } -> Kind.SCENE
        words.any { it in VERSE } -> Kind.VERSE
        words.any { it in SONG } -> Kind.SONG
        else -> null
    }

    /** [kind]'s tab, then its Add to Schedule button. */
    private fun addFrom(kind: Kind) = GuideTour(
        listOf(
            tabStep(kind.tab),
            GuideStep(
                kind.button,
                helperText(Res.string.helper_hint_add_to_schedule, helperText(Res.string.add_to_schedule)),
                before = HelperAction.SelectTab(kind.tab),
            ),
        ),
    )

    /** Anything at all: the schedule, which says every tab has the button, then Add Files. */
    private fun addAnything() = GuideTour(
        listOf(
            scheduleStep(),
            GuideStep(
                GuideTargets.SCHEDULE_ADD_FILES,
                helperText(Res.string.helper_hint_schedule_add_files, helperText(Res.string.schedule_add_files)),
            ),
        ),
    )

    private fun scheduleStep() =
        GuideStep(GuideTargets.SCHEDULE_PANEL, helperText(Res.string.helper_hint_schedule_add))

    private fun row(hint: StringResource) = one(GuideTargets.SCHEDULE_FIRST_ROW, hint)

    private fun one(target: GuideTarget, hint: StringResource) =
        GuideTour(listOf(GuideStep(target, helperText(hint))))

    private val SCHEDULE = setOf("schedule", "schedules", "setlist", "playlist", "lineup")
    private val ADD = setOf("add", "put", "insert", "include", "get")
    private val REMOVE = setOf("remove", "delete", "take", "drop")
    private val MOVE = setOf("move", "reorder", "rearrange", "order", "drag", "up", "down")
    private val NOTE = setOf("note", "notes", "comment", "timing")
    private val SAVE = setOf("save", "export", "keep")
    private val NEW = setOf("new", "empty", "fresh", "blank")
    // Not "load": loading a planned service into the schedule is the Calendar's.
    private val OPEN = setOf("open", "reopen")
    private val ANNOUNCEMENT = setOf("announcement", "announcements", "message", "timer", "countdown", "clock")
    private val WEBSITE = setOf("website", "websites", "web", "webpage", "url", "page")
    private val SCENE = setOf("scene", "scenes", "canvas", "camera")
    private val VERSE = setOf("verse", "verses", "scripture", "bible", "passage", "reading")
    private val SONG = setOf("song", "songs", "hymn", "hymns", "lyrics")
}

/**
 * A schedule how-to — before the add rule, which would read "add a video to the schedule" as a song
 * called "a video". What that rule reads as a real song or verse stays its own to add.
 */
internal fun scheduleTopicsRule(r: Request): Resolution? {
    val added = (addToScheduleRule(r) as? Resolution.Act)?.action
    val namesOne = added is HelperAction.AddVerseToSchedule ||
        (added is HelperAction.AddSongToSchedule && added.query.split(' ').any { it !in KIND_WORDS })
    // "Add it to the schedule" means what is on screen, which only the add rule could know.
    val meansWhatIsShown = r.has(PRONOUNS) && !r.has(QUESTIONS)
    if (namesOne || meansWhatIsShown) return null
    return ScheduleTopics.find(r.text)?.let { act(HelperAction.Highlight(it)) }
}

private val PRONOUNS = setOf("it", "this", "that")
private val QUESTIONS = setOf("how", "where", "can")

private val KIND_WORDS = setOf(
    "a", "an", "the", "my", "some", "this", "it", "to", "on", "in", "into", "onto", "schedule", "song", "songs",
    "verse", "verses", "bible", "scripture", "video", "videos", "picture", "pictures", "photo", "photos", "image",
    "images", "presentation", "slides", "pdf", "powerpoint", "lower", "third", "announcement", "message", "timer",
    "countdown", "website", "web", "page", "scene", "item", "items", "something", "things", "file", "files",
)

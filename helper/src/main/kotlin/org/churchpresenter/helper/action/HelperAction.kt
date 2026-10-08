package org.churchpresenter.helper.action

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs

/** Which content an appearance change is for. */
enum class ContentScope { SONG, BIBLE, ALL }

/** What about planned services the operator asked — each opens the Calendar Manager with its own how-to. */
enum class CalendarTopic { PLAN, REPEAT, TEMPLATE, LOAD, AUTOMATE }

/** Whether a background change is kept, or only shown for the rest of this service. */
enum class Persistence { SAVED, THIS_SERVICE }

/** One step of a guided tour: open what [before] opens, then ring [target] and say [hint]. */
data class GuideStep(val target: GuideTarget, val hint: HelperText, val before: HelperAction? = null)

/** A run of [GuideStep]s — "where do I add a song" is the Songs tab, then the New Song button. */
data class GuideTour(val steps: List<GuideStep>)

/**
 * Everything the helper can do. The rule parser — and a model-backed resolver later — only ever
 * picks one of these, so nothing the helper does is outside this list.
 */
sealed interface HelperAction {
    /** Changes something, so it is confirmed first. Pointing at things and showing tips are not. */
    val needsConfirmation: Boolean get() = true

    /** Changes what is on screen right now; the confirmation says so. */
    val affectsLive: Boolean get() = false

    data class ShowBibleVerse(
        val book: String,
        val chapter: Int,
        val verse: Int,
        val lastVerse: Int,
        val display: String,
    ) : HelperAction {
        override val affectsLive get() = true
    }

    data object NextSlide : HelperAction {
        override val affectsLive get() = true
    }

    data object PreviousSlide : HelperAction {
        override val affectsLive get() = true
    }

    data object ClearOutput : HelperAction {
        override val affectsLive get() = true
    }

    data object Take : HelperAction {
        override val affectsLive get() = true
    }

    /** [hex] is `#RRGGBB`; [colorName] is how the operator said it, shown back beside a swatch. */
    data class SetBackgroundColor(
        val scope: ContentScope,
        val hex: String,
        val colorName: String,
        val persistence: Persistence = Persistence.SAVED,
    ) : HelperAction {
        override val affectsLive get() = persistence == Persistence.THIS_SERVICE
    }

    /** [direction] is +1 for bigger, -1 for smaller. */
    data class ChangeFontSize(val scope: ContentScope, val direction: Int) : HelperAction

    data class OpenSettings(val page: SettingsPage) : HelperAction {
        override val needsConfirmation get() = false
    }

    data object OpenSetupWizard : HelperAction
    data object OpenKeyboardShortcuts : HelperAction {
        override val needsConfirmation get() = false
    }

    data object StartDisplaySetup : HelperAction {
        override val needsConfirmation get() = false
    }

    /** Make [screen] the audience screen the main output goes to. */
    data class AssignAudienceScreen(val screen: HelperScreen) : HelperAction

    /** Show the outputs and put each screen's number on it. */
    data object IdentifyScreens : HelperAction {
        override val affectsLive get() = true
    }

    data object ToggleOutputWindows : HelperAction {
        override val affectsLive get() = true
    }

    data class SelectTab(val tab: Tabs) : HelperAction {
        override val needsConfirmation get() = false
    }

    /** Put a hidden tab back in the tab row. */
    data class ShowTab(val tab: Tabs) : HelperAction

    /** Ring [tour]'s controls in turn; [label] names it when it is one of several choices. */
    data class Highlight(val tour: GuideTour, val label: HelperText? = null) : HelperAction {
        override val needsConfirmation get() = false
    }

    data class ShowShortcut(val action: ShortcutAction) : HelperAction {
        override val needsConfirmation get() = false
    }

    /** Take back the last change the helper made. */
    data object UndoLast : HelperAction

    /**
     * Open the Converter on its Songs tab, with [sourceId] — a converter song-source id — chosen
     * when the operator named the program; [sourceName] is how that program writes its name.
     */
    data class OpenConverter(val sourceId: String? = null, val sourceName: String? = null) : HelperAction {
        override val needsConfirmation get() = false
    }

    /** Open the Calendar Manager, saying how to do the [topic] asked about. */
    data class OpenCalendar(val topic: CalendarTopic = CalendarTopic.PLAN) : HelperAction {
        override val needsConfirmation get() = false
    }

    /**
     * Open the Song Library Manager, where many songs are edited at once — saying how to batch edit
     * them, or, when [compare], how to check a song's languages against each other.
     */
    data class OpenSongLibrary(val compare: Boolean = false) : HelperAction {
        override val needsConfirmation get() = false
    }

    /** A countdown of [minutes] on screen, from the Announcements tab's timer. */
    data class StartCountdown(val minutes: Int) : HelperAction {
        override val affectsLive get() = true
    }

    /** [text] on screen as an announcement, typed as the operator wrote it. */
    data class ShowAnnouncement(val text: String) : HelperAction {
        override val affectsLive get() = true
    }

    /** The song [query] names — by number or title — opened on the Songs tab, ready to go live. */
    data class FindSong(val query: String) : HelperAction {
        override val needsConfirmation get() = false
    }

    /** The song [query] names, added to the end of the schedule. */
    data class AddSongToSchedule(val query: String) : HelperAction

    /** A verse or a run of verses, added to the end of the schedule; [display] is how it reads. */
    data class AddVerseToSchedule(
        val book: String,
        val chapter: Int,
        val verse: Int,
        val lastVerse: Int,
        val display: String,
    ) : HelperAction

    /** The schedule's next row ([forward]) or the one before, made ready to show. */
    data class ScheduleStep(val forward: Boolean) : HelperAction {
        override val affectsLive get() = true
    }

    /** The schedule row whose name contains [name], made ready to show. */
    data class ScheduleGoTo(val name: String) : HelperAction {
        override val affectsLive get() = true
    }

    /** "Help", "commands": every request Wick understands, in a table. */
    data object ShowCommands : HelperAction {
        override val needsConfirmation get() = false
    }

    /** Say what is on screen now. */
    data object WhatsLive : HelperAction {
        override val needsConfirmation get() = false
    }

    /** Say which version this is, and look for a newer one. */
    data object CheckForUpdates : HelperAction {
        override val needsConfirmation get() = false
    }

    /** Open CCLI Reports: how often each song was used, the passages shown, and when. */
    data object OpenStatistics : HelperAction {
        override val needsConfirmation get() = false
    }

    /** "Hello", "help", "what can you do": say hello, with examples of what to ask. */
    data object Greet : HelperAction {
        override val needsConfirmation get() = false
    }

    /** "Thanks": say you're welcome. */
    data object Thanks : HelperAction {
        override val needsConfirmation get() = false
    }
}

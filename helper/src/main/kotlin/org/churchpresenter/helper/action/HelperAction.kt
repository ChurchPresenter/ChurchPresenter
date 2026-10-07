package org.churchpresenter.helper.action

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs

/** Which content an appearance change is for. */
enum class ContentScope { SONG, BIBLE, ALL }

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

    data class Highlight(val tour: GuideTour) : HelperAction {
        override val needsConfirmation get() = false
    }

    data class ShowShortcut(val action: ShortcutAction) : HelperAction {
        override val needsConfirmation get() = false
    }

    /** Take back the last change the helper made. */
    data object UndoLast : HelperAction

    /** "Hello", "help", "what can you do": say hello, with examples of what to ask. */
    data object Greet : HelperAction {
        override val needsConfirmation get() = false
    }

    /** "Thanks": say you're welcome. */
    data object Thanks : HelperAction {
        override val needsConfirmation get() = false
    }
}

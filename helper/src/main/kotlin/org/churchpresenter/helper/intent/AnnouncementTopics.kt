package org.churchpresenter.helper.intent

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_source_clock
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.strings.generated.resources.helper_hint_announcement_go_live
import org.churchpresenter.strings.generated.resources.helper_hint_announcement_text
import org.churchpresenter.strings.generated.resources.helper_hint_timer_clock
import org.churchpresenter.strings.generated.resources.helper_hint_timer_count_up
import org.churchpresenter.strings.generated.resources.helper_hint_timer_countdown
import org.churchpresenter.strings.generated.resources.helper_hint_timer_go_live
import org.churchpresenter.strings.generated.resources.timer_clock_format
import org.churchpresenter.strings.generated.resources.timer_mode_clock
import org.churchpresenter.strings.generated.resources.timer_mode_duration
import org.churchpresenter.strings.generated.resources.timer_title

/**
 * The Announcements tab: a text announcement — paging a parent from the nursery, a parking notice —
 * and its timer, counting down, counting up or showing the clock. A tour of the tab, from where it
 * is set up to its Go Live.
 */
internal object AnnouncementTopics {
    /**
     * The tour for [normalized], or null when it is about none of these. A tab named outright —
     * "open the announcements tab" — is left to the tab rules. A countdown clock is a countdown.
     */
    fun find(normalized: String): GuideTour? {
        if (normalized.containsPhrase("tab")) return null
        fun says(phrases: List<String>) = phrases.any { normalized.containsPhrase(it) }
        return when {
            says(Vocabulary.COUNT_UP) -> timer(timerCountUp())
            says(Vocabulary.COUNTDOWN) -> timer(timerCountdown())
            says(Vocabulary.CLOCK) -> timer(timerClock())
            says(Vocabulary.ANNOUNCEMENT) -> announcement()
            else -> null
        }
    }

    private fun announcement() = GuideTour(
        listOf(
            tabStep(Tabs.ANNOUNCEMENTS),
            GuideStep(
                GuideTargets.ANNOUNCEMENT_TEXT,
                helperText(Res.string.helper_hint_announcement_text),
                before = HelperAction.SelectTab(Tabs.ANNOUNCEMENTS),
            ),
            GuideStep(
                GuideTargets.ANNOUNCEMENT_GO_LIVE,
                helperText(Res.string.helper_hint_announcement_go_live, helperText(Res.string.go_live)),
            ),
        ),
    )

    /** The tab, the timer's modes with [modeHint] saying which to pick, then the timer's Go Live. */
    private fun timer(modeHint: GuideStep) = GuideTour(
        listOf(
            tabStep(Tabs.ANNOUNCEMENTS),
            modeHint,
            GuideStep(
                GuideTargets.TIMER_GO_LIVE,
                helperText(Res.string.helper_hint_timer_go_live, helperText(Res.string.go_live)),
            ),
        ),
    )

    private fun timerCountdown() = modeStep(
        helperText(
            Res.string.helper_hint_timer_countdown,
            helperText(Res.string.timer_title),
            helperText(Res.string.timer_mode_clock),
        ),
    )

    private fun timerCountUp() =
        modeStep(helperText(Res.string.helper_hint_timer_count_up, helperText(Res.string.timer_mode_duration)))

    private fun timerClock() = modeStep(
        helperText(
            Res.string.helper_hint_timer_clock,
            helperText(Res.string.canvas_source_clock),
            helperText(Res.string.timer_clock_format),
        ),
    )

    private fun modeStep(hint: HelperText) =
        GuideStep(GuideTargets.TIMER_MODES, hint, before = HelperAction.SelectTab(Tabs.ANNOUNCEMENTS))
}

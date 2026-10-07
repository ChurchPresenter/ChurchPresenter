package org.churchpresenter.helper.intent

import org.churchpresenter.helper.HelperText
import org.churchpresenter.strings.generated.resources.helper_hint_stage_mode
import org.churchpresenter.strings.generated.resources.profile_display_mode
import org.churchpresenter.strings.generated.resources.profile_mode_stage
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.content_announcements
import org.churchpresenter.strings.generated.resources.helper_hint_stage_arrangement
import org.churchpresenter.strings.generated.resources.helper_hint_stage_bible
import org.churchpresenter.strings.generated.resources.helper_hint_stage_chords
import org.churchpresenter.strings.generated.resources.helper_hint_stage_content
import org.churchpresenter.strings.generated.resources.helper_hint_stage_message_send
import org.churchpresenter.strings.generated.resources.helper_hint_stage_message_text
import org.churchpresenter.strings.generated.resources.helper_hint_stage_open_page
import org.churchpresenter.strings.generated.resources.helper_hint_stage_profile
import org.churchpresenter.strings.generated.resources.helper_hint_stage_text
import org.churchpresenter.strings.generated.resources.helper_hint_stage_timer_send
import org.churchpresenter.strings.generated.resources.helper_hint_stage_what_goes_where
import org.churchpresenter.strings.generated.resources.helper_hint_stage_zones
import org.churchpresenter.strings.generated.resources.helper_hint_timer_countdown
import org.churchpresenter.strings.generated.resources.profile_applies_to
import org.churchpresenter.strings.generated.resources.profile_group_what_goes_where
import org.churchpresenter.strings.generated.resources.profile_nav_content
import org.churchpresenter.strings.generated.resources.profile_nav_stage_layout
import org.churchpresenter.strings.generated.resources.profile_stage_arrangement
import org.churchpresenter.strings.generated.resources.profile_stage_zones
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.stage_monitor_quadrant_clock
import org.churchpresenter.strings.generated.resources.stage_monitor_quadrant_next
import org.churchpresenter.strings.generated.resources.stage_monitor_quadrant_notes
import org.churchpresenter.strings.generated.resources.stage_monitor_show_chords
import org.churchpresenter.strings.generated.resources.stage_monitor_zone_none
import org.churchpresenter.strings.generated.resources.timer_mode_clock
import org.churchpresenter.strings.generated.resources.timer_title
import org.churchpresenter.strings.generated.resources.tooltip_send_to_stage_monitor
import org.jetbrains.compose.resources.StringResource

/**
 * What shows on the stage monitor: its zones and how they sit, what goes in each, chords, its text,
 * and a message or a countdown sent to it. Only for a request about the stage monitor; one that
 * names it with none of these is the stage monitor's setup, the output rules' to answer.
 */
internal object StageTopics {
    fun find(normalized: String): GuideTour? {
        if (!isAboutStage(normalized)) return null
        val words = normalized.split(' ')
        fun says(phrases: List<String>) = phrases.any { normalized.containsPhrase(it) }
        val chords = normalized.containsWordPrefix(Vocabulary.CHORD) ||
            words.any { it in Vocabulary.CHORD_MISSPELLINGS }
        val content = contentKind(normalized)
        return when {
            chords -> StageTours.chords()
            says(Vocabulary.STAGE_MESSAGE) -> StageTours.message()
            says(Vocabulary.COUNTDOWN) -> StageTours.timer()
            says(Vocabulary.STAGE_LAYOUT) -> StageTours.layout()
            says(Vocabulary.STAGE_TEXT) -> StageTours.text()
            content != null || says(Vocabulary.STAGE_CONTENT) -> StageTours.whatGoesWhere(content)
            else -> null
        }
    }

    private fun isAboutStage(normalized: String) =
        Vocabulary.STAGE_MONITOR.any { normalized.containsPhrase(it) } ||
            normalized.split(' ').any { it == "stage" || it == "confidence" || it == "foldback" }

    /** The one kind of content the request names, if it names one. */
    private fun contentKind(normalized: String): StageMonitorContentType? {
        fun has(vararg phrases: String) = phrases.any { normalized.containsPhrase(it) }
        return when {
            has("bible", "scripture", "verse", "verses") -> StageMonitorContentType.BIBLE
            has("next", "upcoming", "coming up") -> StageMonitorContentType.NEXT
            has("song", "songs", "lyrics", "words") -> StageMonitorContentType.SONGS
            has("clock", "time") -> StageMonitorContentType.CLOCK
            has("notes", "speaker notes", "presenter notes", "sermon notes") ->
                StageMonitorContentType.PRESENTATION_NOTES
            has("announcement", "announcements") -> StageMonitorContentType.ANNOUNCEMENT_TEXT
            else -> null
        }
    }
}

/** What shows on the stage monitor, and how — asked or told. Before its setup and the general rules. */
internal fun stageTopicsRule(r: Request): Resolution? =
    StageTopics.find(r.text)?.let { act(HelperAction.Highlight(it)) }

/** The stage monitor tours [StageTopics] chooses between. */
internal object StageTours {
    fun layout() = onStageLayout(
        GuideStep(GuideTargets.STAGE_ZONES, hint(Res.string.helper_hint_stage_zones, Res.string.profile_stage_zones)),
        GuideStep(
            GuideTargets.STAGE_ARRANGEMENT,
            hint(Res.string.helper_hint_stage_arrangement, Res.string.profile_stage_arrangement),
        ),
    )

    fun whatGoesWhere(kind: StageMonitorContentType?): GuideTour {
        val none = helperText(Res.string.stage_monitor_zone_none)
        val step = when (kind) {
            null -> GuideStep(
                GuideTargets.stageContent(StageMonitorContentType.BIBLE.name),
                helperText(
                    Res.string.helper_hint_stage_what_goes_where,
                    helperText(Res.string.profile_group_what_goes_where),
                    none,
                ),
            )
            StageMonitorContentType.BIBLE -> GuideStep(
                GuideTargets.stageContent(kind.name),
                helperText(
                    Res.string.helper_hint_stage_bible,
                    helperText(Res.string.bible),
                    helperText(Res.string.profile_nav_content),
                ),
            )
            else -> GuideStep(
                GuideTargets.stageContent(kind.name),
                helperText(Res.string.helper_hint_stage_content, helperText(contentLabel(kind)), none),
            )
        }
        return onStageLayout(step)
    }

    private fun contentLabel(kind: StageMonitorContentType): StringResource = when (kind) {
        StageMonitorContentType.SONGS -> Res.string.songs
        StageMonitorContentType.NEXT -> Res.string.stage_monitor_quadrant_next
        StageMonitorContentType.CLOCK -> Res.string.stage_monitor_quadrant_clock
        StageMonitorContentType.PRESENTATION_NOTES -> Res.string.stage_monitor_quadrant_notes
        StageMonitorContentType.ANNOUNCEMENT_TEXT -> Res.string.content_announcements
        else -> Res.string.bible
    }

    fun text() = onStageLayout(
        GuideStep(GuideTargets.STAGE_TEXT_ZONE, hint(Res.string.helper_hint_stage_text, Res.string.profile_applies_to)),
    )

    fun chords() = GuideTour(
        listOf(
            pickProfile(),
            stageMode(),
            GuideStep(
                GuideTargets.PROFILE_CONTENT_PAGE,
                hint(Res.string.helper_hint_stage_open_page, Res.string.profile_nav_content),
            ),
            GuideStep(
                GuideTargets.STAGE_SHOW_CHORDS,
                helperText(
                    Res.string.helper_hint_stage_chords,
                    helperText(Res.string.stage_monitor_show_chords),
                    helperText(Res.string.songs),
                ),
            ),
        ),
    )

    fun message() = GuideTour(
        listOf(
            tabStep(Tabs.ANNOUNCEMENTS),
            GuideStep(
                GuideTargets.ANNOUNCEMENT_TEXT,
                helperText(Res.string.helper_hint_stage_message_text),
                before = HelperAction.SelectTab(Tabs.ANNOUNCEMENTS),
            ),
            GuideStep(
                GuideTargets.ANNOUNCEMENT_TO_STAGE,
                helperText(
                    Res.string.helper_hint_stage_message_send,
                    helperText(Res.string.tooltip_send_to_stage_monitor),
                    helperText(Res.string.content_announcements),
                ),
            ),
        ),
    )

    fun timer() = GuideTour(
        listOf(
            tabStep(Tabs.ANNOUNCEMENTS),
            GuideStep(
                GuideTargets.timerMode(Constants.TIMER_MODE_DURATION),
                helperText(
                    Res.string.helper_hint_timer_countdown,
                    helperText(Res.string.timer_title),
                    helperText(Res.string.timer_mode_clock),
                ),
                before = HelperAction.SelectTab(Tabs.ANNOUNCEMENTS),
            ),
            GuideStep(
                GuideTargets.TIMER_TO_STAGE,
                hint(Res.string.helper_hint_stage_timer_send, Res.string.tooltip_send_to_stage_monitor),
            ),
        ),
    )

    /** Settings on the Profiles page, the stage monitor's Stage layout page, then [steps]. */
    private fun onStageLayout(vararg steps: GuideStep) = GuideTour(
        listOf(
            pickProfile(),
            stageMode(),
            GuideStep(
                GuideTargets.STAGE_LAYOUT_PAGE,
                hint(Res.string.helper_hint_stage_open_page, Res.string.profile_nav_stage_layout),
            ),
        ) + steps,
    )

    /** [res], naming the control [label] in the app's own words. */
    private fun hint(res: StringResource, label: StringResource): HelperText = helperText(res, helperText(label))
}

/**
 * The profile's Stage monitor segment: Stage layout and Show Chords only exist for a stage
 * monitor, so a profile still in another mode shows neither. Clicking it, set or not, moves on.
 */
private fun stageMode() = GuideStep(
    GuideTargets.displayMode(Constants.DISPLAY_MODE_STAGE_MONITOR),
    helperText(
        Res.string.helper_hint_stage_mode,
        helperText(Res.string.profile_display_mode),
        helperText(Res.string.profile_mode_stage),
        helperText(Res.string.profile_nav_stage_layout),
    ),
)

private fun pickProfile() = GuideStep(
    GuideTargets.settingsPage(SettingsPage.PROFILES),
    helperText(Res.string.helper_hint_stage_profile),
    before = HelperAction.OpenSettings(SettingsPage.PROFILES),
)

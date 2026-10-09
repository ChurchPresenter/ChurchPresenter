package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.strings.generated.resources.helper_hint_output_profile_name
import org.churchpresenter.strings.generated.resources.profile_name
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.strings.generated.resources.helper_example_lower_third
import org.churchpresenter.strings.generated.resources.helper_example_lower_third_output
import org.churchpresenter.strings.generated.resources.helper_clarify_lower_third
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_hint_output_assign
import org.churchpresenter.strings.generated.resources.helper_hint_output_assign_lower_third
import org.churchpresenter.strings.generated.resources.helper_hint_output_display_mode
import org.churchpresenter.strings.generated.resources.helper_hint_output_new_profile
import org.churchpresenter.strings.generated.resources.helper_hint_output_profiles
import org.churchpresenter.strings.generated.resources.helper_hint_output_projection_page
import org.churchpresenter.strings.generated.resources.profile_display_mode
import org.churchpresenter.strings.generated.resources.profile_mode_full
import org.churchpresenter.strings.generated.resources.profile_mode_lower_third
import org.churchpresenter.strings.generated.resources.profile_mode_stage
import org.churchpresenter.strings.generated.resources.screen_assignment
import org.jetbrains.compose.resources.StringResource

/**
 * "How do I set up a stage monitor / a lower third display / a full screen display": an output's
 * kind is its profile's display mode, so the tour makes the profile, sets its mode, then gives it a
 * screen on the Projection page. A lower third on its own — the graphic — is the media tours'.
 */
internal object OutputTopics {
    /** The tour for [normalized], or null when it is not about setting up one of these outputs. */
    fun find(normalized: String): GuideTour? {
        val fullScreen = Vocabulary.FULL_SCREEN.firstOrNull { normalized.containsPhrase(it) }
        // "Full screen" says "screen" itself: what is left once it is taken out decides whether this
        // is about an output — "make the video full screen" is not.
        val rest = fullScreen?.let { " $normalized ".replace(" $it ", " ").trim() } ?: normalized
        val aboutOutput = rest.split(' ').any { it in Vocabulary.OUTPUT_WORDS } || rest.containsPhrase("set up")
        return when {
            Vocabulary.STAGE_MONITOR.any { normalized.containsPhrase(it) } ->
                output(Res.string.profile_mode_stage, Constants.DISPLAY_MODE_STAGE_MONITOR)
            fullScreen != null && aboutOutput -> output(Res.string.profile_mode_full, Constants.DISPLAY_MODE_FULLSCREEN)
            else -> null
        }
    }

    /** A lower third's display: its profile in Lower third mode, on a screen or an NDI output. */
    fun lowerThirdDisplay() =
        output(
            Res.string.profile_mode_lower_third,
            Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            Res.string.helper_hint_output_assign_lower_third,
        )

    /**
     * Settings, the Profiles page, a profile and its name, its [mode] (the segment for [modeValue]), then the
     * Projection page and the first screen's profile. The pages are left for the operator to open:
     * switching one from under a click on the step before would swallow that click.
     */
    private fun output(
        mode: StringResource,
        modeValue: String,
        assignHint: StringResource = Res.string.helper_hint_output_assign,
    ) =
        GuideTour(
            listOf(
                GuideStep(
                    GuideTargets.settingsPage(SettingsPage.PROFILES),
                    helperText(Res.string.helper_hint_output_profiles),
                    before = HelperAction.OpenSettings(SettingsPage.PROFILES),
                ),
                GuideStep(GuideTargets.PROFILE_NEW, helperText(Res.string.helper_hint_output_new_profile)),
                GuideStep(
                    GuideTargets.PROFILE_NAME,
                    helperText(Res.string.helper_hint_output_profile_name, helperText(Res.string.profile_name)),
                ),
                // The segment itself, not the row: it is where to click.
                GuideStep(
                    GuideTargets.displayMode(modeValue),
                    helperText(
                        Res.string.helper_hint_output_display_mode,
                        helperText(Res.string.profile_display_mode),
                        helperText(mode),
                    ),
                ),
                GuideStep(
                    GuideTargets.settingsPage(SettingsPage.PROJECTION),
                    helperText(Res.string.helper_hint_output_projection_page),
                ),
                GuideStep(
                    GuideTargets.SCREEN_PROFILE_PICKER,
                    helperText(assignHint, helperText(Res.string.screen_assignment)),
                ),
            ),
        )
}

/** Setting up a stage monitor or a full screen display, asked or told. */
internal fun outputTopicsRule(r: Request): Resolution? =
    OutputTopics.find(r.text)?.let { act(HelperAction.Highlight(it)) }

/**
 * A lower third: made on the Lower Third tab, or shown by a display set up for it. A request that
 * names an output is the display; one that names making it is the tab; anything else asks which.
 */
internal fun lowerThirdRule(r: Request): Resolution? {
    if (Vocabulary.LOWER_THIRD.none { r.text.containsPhrase(it) }) return null
    if (r.has(Vocabulary.CLEAR) || r.first in Vocabulary.TAKE_DOWN) return null
    // "Make the lower third text bigger" is a text size. "Lower" is a smaller-word itself, so it is
    // left out of the check.
    val resizing = r.has(Vocabulary.BIGGER) || r.words.any { it in Vocabulary.SMALLER && it != "lower" } ||
        r.hasPhrase(Vocabulary.TOO_SMALL) || r.hasPhrase(Vocabulary.TOO_BIG)
    if (resizing) return null
    // "set up" alone says nothing either way, so it is not an output word here.
    val aboutOutput = r.words.any { it in Vocabulary.OUTPUT_WORDS && it != "setup" }
    val display = HelperAction.Highlight(
        OutputTopics.lowerThirdDisplay(),
        label = helperText(Res.string.helper_example_lower_third_output),
    )
    val make = HelperAction.Highlight(
        MediaTopics.lowerThird(),
        label = helperText(Res.string.helper_example_lower_third),
    )
    return when {
        aboutOutput -> act(display)
        r.has(Vocabulary.MAKE) -> act(make)
        else -> Resolution.Clarify(helperText(Res.string.helper_clarify_lower_third), listOf(make, display))
    }
}

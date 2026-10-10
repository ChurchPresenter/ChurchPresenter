package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.ProfileFocus
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_background_choice_page
import org.churchpresenter.strings.generated.resources.helper_background_choice_profile
import org.churchpresenter.strings.generated.resources.helper_background_choice_service
import org.churchpresenter.strings.generated.resources.helper_clarify_background
import org.churchpresenter.strings.generated.resources.helper_hint_background
import org.churchpresenter.strings.generated.resources.helper_hint_background_page
import org.churchpresenter.strings.generated.resources.helper_hint_background_profile

/**
 * "Change the background", with no colour and no one song named: there are three, so Wick asks which —
 * the Backgrounds page (every song and verse), one screen's own (its profile), or the quick background
 * for this service only. A named profile ("…on Stage") picks that profile's page.
 */
internal fun backgroundChoiceRule(r: Request): Resolution? {
    if (!r.has(Vocabulary.BACKGROUND) || r.has(Vocabulary.SETTINGS) || r.hasPhrase(Vocabulary.ONE_SONG)) return null
    return Resolution.Clarify(helperText(Res.string.helper_clarify_background), backgroundChoices())
}

/** The three backgrounds, each a one-step tour to where it is set. */
internal fun backgroundChoices(): List<HelperAction> = listOf(
    HelperAction.Highlight(
        GuideTour(
            listOf(
                GuideStep(
                    GuideTargets.settingsPage(SettingsPage.BACKGROUND),
                    helperText(Res.string.helper_hint_background_page),
                    HelperAction.OpenSettings(SettingsPage.BACKGROUND),
                ),
            ),
        ),
        helperText(Res.string.helper_background_choice_page),
    ),
    HelperAction.Highlight(
        GuideTour(
            listOf(
                GuideStep(
                    GuideTargets.settingsRow("profile_bg_row"),
                    helperText(Res.string.helper_hint_background_profile),
                    HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(page = "BACKGROUND")),
                ),
            ),
        ),
        helperText(Res.string.helper_background_choice_profile),
    ),
    HelperAction.Highlight(
        GuideTour(listOf(GuideStep(GuideTargets.BACKGROUND_BUTTON, helperText(Res.string.helper_hint_background)))),
        helperText(Res.string.helper_background_choice_service),
    ),
)

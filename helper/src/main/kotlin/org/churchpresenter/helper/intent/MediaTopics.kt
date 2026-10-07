package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.strings.generated.resources.helper_hint_lower_third_info
import org.churchpresenter.strings.generated.resources.helper_hint_lower_third_name
import org.churchpresenter.strings.generated.resources.helper_hint_lower_third_save
import org.churchpresenter.strings.generated.resources.generate_lower_third
import org.churchpresenter.strings.generated.resources.helper_hint_lower_third_generate
import org.churchpresenter.strings.generated.resources.helper_hint_lower_third_go_live
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.go_live
import org.churchpresenter.strings.generated.resources.helper_hint_media_file
import org.churchpresenter.strings.generated.resources.helper_hint_media_go_live
import org.churchpresenter.strings.generated.resources.helper_hint_pictures_folder
import org.churchpresenter.strings.generated.resources.helper_hint_pictures_go_live
import org.churchpresenter.strings.generated.resources.helper_hint_pictures_play
import org.churchpresenter.strings.generated.resources.helper_hint_presentation_file
import org.churchpresenter.strings.generated.resources.helper_hint_presentation_go_live
import org.churchpresenter.strings.generated.resources.media_select_file
import org.churchpresenter.strings.generated.resources.play
import org.churchpresenter.strings.generated.resources.select_folder
import org.churchpresenter.strings.generated.resources.select_presentation_file_button
import org.jetbrains.compose.resources.StringResource

/**
 * "How do I add photos / show a slideshow / show a PDF / play a video / make a lower third": a tour
 * of the tab that does it, from the button that brings the files in to the one that puts them on
 * screen.
 */
internal object MediaTopics {
    /**
     * The tour for [normalized], or null when it is about none of these. A tab named outright —
     * "open the pictures tab" — is left to the tab rules.
     */
    fun find(normalized: String): GuideTour? {
        if (normalized.containsPhrase("tab")) return null
        val words = normalized.split(' ')
        return when {
            Vocabulary.LOWER_THIRD.any { normalized.containsPhrase(it) } -> lowerThird()
            Vocabulary.SLIDESHOW.any { normalized.containsPhrase(it) } -> slideshow()
            words.any { it in Vocabulary.PHOTOS } ->
                if (words.any { it in Vocabulary.ADD }) addPhotos() else showPictures()
            words.any { it in Vocabulary.PRESENTATION } -> showPresentation()
            words.any { it in Vocabulary.VIDEO } -> showVideo()
            else -> null
        }
    }

    private fun addPhotos() = GuideTour(listOf(tabStep(Tabs.PICTURES), pictureFolder()))

    private fun showPictures() = GuideTour(
        listOf(tabStep(Tabs.PICTURES), pictureFolder(), picturesGoLive()),
    )

    private fun slideshow() = GuideTour(
        listOf(
            tabStep(Tabs.PICTURES),
            pictureFolder(),
            picturesGoLive(),
            step(GuideTargets.PICTURES_PLAY, Res.string.helper_hint_pictures_play, Res.string.play),
        ),
    )

    private fun showPresentation() = GuideTour(
        listOf(
            tabStep(Tabs.PRESENTATION),
            step(
                GuideTargets.PRESENTATION_SELECT_FILE,
                Res.string.helper_hint_presentation_file,
                Res.string.select_presentation_file_button,
                before = HelperAction.SelectTab(Tabs.PRESENTATION),
            ),
            step(GuideTargets.PRESENTATION_GO_LIVE, Res.string.helper_hint_presentation_go_live, Res.string.go_live),
        ),
    )

    private fun showVideo() = GuideTour(
        listOf(
            tabStep(Tabs.MEDIA),
            step(
                GuideTargets.MEDIA_SELECT_FILE,
                Res.string.helper_hint_media_file,
                Res.string.media_select_file,
                before = HelperAction.SelectTab(Tabs.MEDIA),
            ),
            step(GuideTargets.MEDIA_GO_LIVE, Res.string.helper_hint_media_go_live, Res.string.go_live),
        ),
    )

    /** Made in the generator the tab opens, then put live from the tab. */
    private fun lowerThird() = GuideTour(
        listOf(
            tabStep(Tabs.LOWER_THIRD),
            step(
                GuideTargets.LOWER_THIRD_GENERATE,
                Res.string.helper_hint_lower_third_generate,
                Res.string.generate_lower_third,
                before = HelperAction.SelectTab(Tabs.LOWER_THIRD),
            ),
            // In the generator's own window. Clicking into each field moves the tour on; saving
            // moves it to Go Live, back on the tab.
            GuideStep(GuideTargets.LOWER_THIRD_NAME, helperText(Res.string.helper_hint_lower_third_name)),
            GuideStep(GuideTargets.LOWER_THIRD_INFO, helperText(Res.string.helper_hint_lower_third_info)),
            GuideStep(GuideTargets.LOWER_THIRD_SAVE, helperText(Res.string.helper_hint_lower_third_save)),
            step(GuideTargets.LOWER_THIRD_GO_LIVE, Res.string.helper_hint_lower_third_go_live, Res.string.go_live),
        ),
    )

    private fun pictureFolder() = step(
        GuideTargets.PICTURES_SELECT_FOLDER,
        Res.string.helper_hint_pictures_folder,
        Res.string.select_folder,
        before = HelperAction.SelectTab(Tabs.PICTURES),
    )

    private fun picturesGoLive() =
        step(GuideTargets.PICTURES_GO_LIVE, Res.string.helper_hint_pictures_go_live, Res.string.go_live)

    /** A step whose [hint] names the [button] it rings, in the button's own words. */
    private fun step(target: GuideTarget, hint: StringResource, button: StringResource, before: HelperAction? = null) =
        GuideStep(target, helperText(hint, helperText(button)), before)
}

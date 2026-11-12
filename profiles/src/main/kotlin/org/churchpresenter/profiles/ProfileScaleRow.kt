package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.desktop_view
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.mobile_view
import org.churchpresenter.strings.generated.resources.pictures
import org.churchpresenter.strings.generated.resources.profile_web_page_view
import org.churchpresenter.strings.generated.resources.profile_web_page_view_follow
import org.churchpresenter.strings.generated.resources.profile_web_page_view_note
import org.churchpresenter.sharedui.utils.label
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.settings.WebPageView
import org.jetbrains.compose.resources.stringResource

/**
 * How pictures and video meet this profile's screens -- fit, fill or stretch -- one row for each it
 * shows.
 *
 * Per profile because it answers to the screen's shape. The Pictures and Media tabs' own scale
 * buttons are a shortcut that sets it on every profile at once.
 */
@Composable
internal fun ScaleRows(profile: OutputProfile, onProfileChange: (OutputProfile) -> Unit) {
    if (profile.look.media.pictures) {
        SettingsRow(Res.string.pictures, paths = listOf("pictureScaleMode")) {
            ScaleSegments(profile.pictureScaleMode) { onProfileChange(profile.copy(pictureScaleMode = it)) }
        }
    }
    if (profile.look.media.video) {
        SettingsRow(Res.string.media, paths = listOf("mediaScaleMode")) {
            ScaleSegments(profile.mediaScaleMode) { onProfileChange(profile.copy(mediaScaleMode = it)) }
        }
    }
    if (profile.look.slide.web) {
        SettingsRow(
            Res.string.profile_web_page_view,
            sub = stringResource(Res.string.profile_web_page_view_note),
            paths = listOf(WEB_PAGE_VIEW_PATH),
        ) {
            RowSegmented(
                options = listOf(
                    RowOption(WebPageView.FOLLOW_WEB_TAB, stringResource(Res.string.profile_web_page_view_follow)),
                    RowOption(WebPageView.MOBILE, stringResource(Res.string.mobile_view)),
                    RowOption(WebPageView.DESKTOP, stringResource(Res.string.desktop_view)),
                ),
                selected = profile.webPageView,
                onSelect = { onProfileChange(profile.copy(webPageView = it)) },
            )
        }
    }
}

/** Which layout of a website this profile's screens show. */
internal const val WEB_PAGE_VIEW_PATH = "webPageView"

@Composable
private fun ScaleSegments(selected: OutputScaleMode, onSelect: (OutputScaleMode) -> Unit) {
    RowSegmented(
        options = OutputScaleMode.entries.map { RowOption(it, stringResource(it.label)) },
        selected = selected,
        onSelect = onSelect,
    )
}

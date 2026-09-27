package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background
import churchpresenter.composeapp.generated.resources.customize_bible
import churchpresenter.composeapp.generated.resources.customize_songs
import churchpresenter.composeapp.generated.resources.media_subtitles
import churchpresenter.composeapp.generated.resources.profile_nav_appearance
import churchpresenter.composeapp.generated.resources.profile_nav_content
import churchpresenter.composeapp.generated.resources.profile_nav_general
import churchpresenter.composeapp.generated.resources.profile_nav_live_captions
import churchpresenter.composeapp.generated.resources.profile_nav_outputs
import churchpresenter.composeapp.generated.resources.profile_nav_profile
import churchpresenter.composeapp.generated.resources.profile_nav_stage_layout
import churchpresenter.composeapp.generated.resources.profile_nav_stage_monitor
import churchpresenter.composeapp.generated.resources.tab_dictionary
import churchpresenter.composeapp.generated.resources.tab_qa
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * One page of the Profiles editor -- one entry of the section list between the profiles and the
 * settings.
 *
 * Three pages are about the profile itself -- what it is called and what kind of screen it is for,
 * which outputs use it, and what it shows -- and the rest are its appearance, one per kind of
 * content, each the [CustomizePane] it has always been.
 */
internal sealed interface ProfilePage {
    data object General : ProfilePage
    data object Outputs : ProfilePage
    data object Content : ProfilePage
    data class Appearance(val pane: CustomizePane) : ProfilePage
}

/** A titled group of the section list. */
internal data class ProfileNavSection(val title: String, val pages: List<ProfilePage>)

/**
 * The section list for [profile]: the profile's own three pages, then its appearance.
 *
 * A stage monitor's appearance is its zone layout and the two overlays it can carry, so its second
 * group is titled for it and lists those; every other screen lists every kind of content, whether
 * the profile shows it or not -- a page for content that is switched off says so rather than
 * vanishing, so the list does not reshuffle under the operator as switches are flipped.
 */
@Composable
internal fun profileNavSections(profile: OutputProfile): List<ProfileNavSection> {
    val stage = profile.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
    return listOf(
        ProfileNavSection(
            stringResource(Res.string.profile_nav_profile),
            listOf(ProfilePage.General, ProfilePage.Outputs, ProfilePage.Content),
        ),
        ProfileNavSection(
            stringResource(if (stage) Res.string.profile_nav_stage_monitor else Res.string.profile_nav_appearance),
            customizePanes(profile.displayMode).map { ProfilePage.Appearance(it) },
        ),
    )
}

/** What the section list calls [this]. */
@Composable
internal fun ProfilePage.label(): String = when (this) {
    ProfilePage.General -> stringResource(Res.string.profile_nav_general)
    ProfilePage.Outputs -> stringResource(Res.string.profile_nav_outputs)
    ProfilePage.Content -> stringResource(Res.string.profile_nav_content)
    is ProfilePage.Appearance -> pane.navLabel()
}

/** An appearance page's name in the section list -- fuller than the old tab labels had room for. */
@Composable
internal fun CustomizePane.navLabel(): String = when (this) {
    CustomizePane.STAGE_MONITOR -> stringResource(Res.string.profile_nav_stage_layout)
    CustomizePane.BIBLE -> stringResource(Res.string.customize_bible)
    CustomizePane.SONGS -> stringResource(Res.string.customize_songs)
    CustomizePane.BACKGROUND -> stringResource(Res.string.background)
    CustomizePane.CAPTIONS -> stringResource(Res.string.profile_nav_live_captions)
    CustomizePane.SUBTITLES -> stringResource(Res.string.media_subtitles)
    CustomizePane.QA -> stringResource(Res.string.tab_qa)
    CustomizePane.DICTIONARY -> stringResource(Res.string.tab_dictionary)
}

/** The icon beside [this] in the section list. */
internal val ProfilePage.icon: ImageVector
    get() = when (this) {
        ProfilePage.General -> Icons.Filled.Tune
        ProfilePage.Outputs -> Icons.Filled.Tv
        ProfilePage.Content -> Icons.Filled.Checklist
        is ProfilePage.Appearance -> when (pane) {
            CustomizePane.STAGE_MONITOR -> Icons.Filled.Dashboard
            CustomizePane.BIBLE -> Icons.AutoMirrored.Filled.MenuBook
            CustomizePane.SONGS -> Icons.Filled.MusicNote
            CustomizePane.BACKGROUND -> Icons.Filled.Wallpaper
            CustomizePane.CAPTIONS -> Icons.Filled.ClosedCaption
            CustomizePane.SUBTITLES -> Icons.Filled.Subtitles
            CustomizePane.QA -> Icons.Filled.Forum
            CustomizePane.DICTIONARY -> Icons.Filled.Translate
        }
    }

/**
 * Whether the page's content is one this profile shows at all. A page for content switched off is
 * still listed, dimmed, and says so at its top.
 */
internal fun ProfilePage.isShownBy(profile: OutputProfile): Boolean = when (this) {
    is ProfilePage.Appearance -> pane in stylePanesFor(profile)
    else -> true
}

/** Whether the header offers Basic / Advanced on this page: every page but General and Outputs. */
internal val ProfilePage.hasDetailSwitch: Boolean
    get() = when (this) {
        ProfilePage.General, ProfilePage.Outputs -> false
        ProfilePage.Content -> true
        is ProfilePage.Appearance -> true
    }

/** Test handle for one entry of the section list. Appearance pages keep their Style-tab tags. */
internal fun ProfilePage.navTag(): String = when (this) {
    ProfilePage.General -> "profile_nav_general"
    ProfilePage.Outputs -> "profile_nav_outputs"
    ProfilePage.Content -> "profile_nav_content"
    is ProfilePage.Appearance -> railTag(pane.name)
}

/** Test handle for the Text group's element switch. */
internal const val CUSTOMIZE_ELEMENT_ROW_TAG = "customize_element_row"

/** Test handle for the Text group's "Applies to" switch. */
internal const val CUSTOMIZE_TRANSLATION_ROW_TAG = "customize_translation_row"

/** Test handle for one "Applies to" target, by its position in the stack ([ALL_TRANSLATIONS] for All). */
internal fun translationChipTag(index: Int): String = "customize_translation_$index"

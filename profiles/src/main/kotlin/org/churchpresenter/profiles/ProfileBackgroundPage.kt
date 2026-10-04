package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_bg_app_default
import org.churchpresenter.strings.generated.resources.profile_bg_default_group
import org.churchpresenter.strings.generated.resources.profile_bg_default_word
import org.churchpresenter.strings.generated.resources.profile_bg_open
import org.churchpresenter.strings.generated.resources.profile_bg_own
import org.churchpresenter.strings.generated.resources.profile_bg_row
import org.churchpresenter.strings.generated.resources.profile_bg_set_for
import org.churchpresenter.strings.generated.resources.profile_bg_sub_app_default
import org.churchpresenter.strings.generated.resources.profile_bg_used_by
import org.churchpresenter.strings.generated.resources.profile_bg_uses_default
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.stringResource

/**
 * The Background page: the profile's own default background, which its Bible and Songs use unless
 * they have one of their own -- and which falls back in turn to the app's default.
 *
 * A lower-third profile edits the default band; a full-screen one the default screen. USED BY lists
 * the two content backgrounds and whether each takes this one, each opening its own page.
 */
@Composable
internal fun ProfileBackgroundPage(
    draft: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenPage: (ProfilePage) -> Unit,
) {
    val scope = if (profile.isLowerThird) BackgroundScope.DEFAULT_LOWER_THIRD else BackgroundScope.DEFAULT
    val owned = scope.name in profile.backgroundOverrides
    val backgrounds = draft.backgroundSettings
    SettingsGroup(
        stringResource(Res.string.profile_bg_default_group),
        key = "default_background",
        paths = scope.surfacePaths(),
        summary = {
            if (owned) {
                backgroundSummary(stringResource(Res.string.profile_bg_own), backgrounds.configFor(scope))
            } else {
                stringResource(Res.string.profile_bg_app_default)
            }
        },
    ) {
        SettingsRow(
            stringResource(Res.string.profile_bg_row),
            sub = if (owned) {
                stringResource(
                    Res.string.profile_bg_set_for,
                    profile.displayName(),
                    stringResource(Res.string.profile_bg_default_word),
                )
            } else {
                stringResource(Res.string.profile_bg_sub_app_default)
            },
        ) {
            RowSegmented(
                options = listOf(
                    RowOption(false, stringResource(Res.string.profile_bg_app_default), BG_APP_DEFAULT_TAG),
                    RowOption(true, stringResource(Res.string.profile_bg_own), BG_OWN_TAG),
                ),
                selected = owned,
                onSelect = { own ->
                    onProfileChange(
                        if (own) {
                            profile.withOwnSurface(scope, backgrounds, backgrounds.configFor(scope))
                        } else {
                            profile.copy(backgroundOverrides = profile.backgroundOverrides - scope.name)
                        },
                    )
                },
            )
        }
        if (owned) BackgroundSurfaceRows(scope, draft, onSettingsChange)
    }
    SettingsGroup(stringResource(Res.string.profile_bg_used_by), key = "used_by") {
        listOf(
            Triple(BackgroundScope.BIBLE, BackgroundScope.BIBLE_LOWER_THIRD, CustomizePane.BIBLE),
            Triple(BackgroundScope.SONG, BackgroundScope.SONG_LOWER_THIRD, CustomizePane.SONGS),
        ).forEach { (full, band, pane) ->
            val surface = if (profile.isLowerThird) band else full
            val config = backgrounds.configFor(surface)
            val usesDefault = config.backgroundType == surface.inheritType
            SettingsRow(
                pane.navLabel(),
                sub = if (usesDefault) {
                    stringResource(Res.string.profile_bg_uses_default)
                } else {
                    stringResource(Res.string.profile_bg_own) + " · " + describeBackground(config)
                },
            ) {
                LinkText(stringResource(Res.string.profile_bg_open), { onOpenPage(ProfilePage.Appearance(pane)) })
            }
        }
    }
}

/** Test handle for the Background page's App default segment. */
internal const val BG_APP_DEFAULT_TAG = "profile_bg_app_default"

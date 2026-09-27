package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_bg_app_default
import churchpresenter.composeapp.generated.resources.profile_bg_comes_from
import churchpresenter.composeapp.generated.resources.profile_bg_follow_app
import churchpresenter.composeapp.generated.resources.profile_bg_level_uses_before
import churchpresenter.composeapp.generated.resources.profile_bg_open_background
import churchpresenter.composeapp.generated.resources.profile_bg_own
import churchpresenter.composeapp.generated.resources.profile_bg_profile_default
import churchpresenter.composeapp.generated.resources.profile_bg_profile_default_level
import churchpresenter.composeapp.generated.resources.profile_bg_row
import churchpresenter.composeapp.generated.resources.profile_bg_set_for
import churchpresenter.composeapp.generated.resources.profile_bg_sub_app
import churchpresenter.composeapp.generated.resources.profile_bg_sub_app_surface
import churchpresenter.composeapp.generated.resources.profile_bg_sub_profile
import churchpresenter.composeapp.generated.resources.profile_group_background
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.BackgroundSurface
import org.churchpresenter.settings.fieldKeys
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.stringResource

/**
 * [profile] carrying its own copy of [scope], set to [config].
 *
 * The profile's background settings are written whole from [draft] -- the resolved document the
 * editor shows -- exactly as every other background edit on this tab writes them: resolution reads
 * a surface the profile does not own not at all, so carrying the rest costs nothing.
 */
internal fun OutputProfile.withOwnSurface(
    scope: BackgroundScope,
    draft: BackgroundSettings,
    config: BackgroundConfig,
): OutputProfile = copy(
    backgroundOverrides = backgroundOverrides + scope.name,
    backgroundSettings = draft.withConfigFor(scope, config),
)

/** Where the background of [this] surface is stored on a profile. */
internal fun BackgroundScope.surfacePaths(): List<String> =
    BackgroundSurface.valueOf(name).fieldKeys.map { "backgroundSettings.$it" }

/** Where a content surface's background comes from: the profile's default, or its own. */
private enum class ContentBackgroundSource { PROFILE_DEFAULT, OWN }

/**
 * The BACKGROUND group at the top of the Bible and Songs pages.
 *
 * Three levels decide what is behind the text -- the app's default (Settings → Background), the
 * profile's default (its Background page), and this content's own -- and each either uses the level
 * before it or has one of its own. The row picks between the last two; the strip underneath shows
 * all three with the one in effect ticked, and opens the Background page.
 *
 * A profile that still follows the app's own Bible or Songs background -- every profile made before
 * this page, until it is changed -- shows that background as its own and says where it comes from,
 * and can be sent back to it with the link at the strip's end.
 */
@Composable
internal fun ContentBackgroundGroup(
    scope: BackgroundScope,
    contentLabel: String,
    draft: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpenBackground: () -> Unit,
) {
    val backgrounds = draft.backgroundSettings
    val config = backgrounds.configFor(scope)
    val defaultScope = if (scope.lowerThird) BackgroundScope.DEFAULT_LOWER_THIRD else BackgroundScope.DEFAULT
    val owned = scope.name in profile.backgroundOverrides
    val source = if (config.backgroundType == scope.inheritType) {
        ContentBackgroundSource.PROFILE_DEFAULT
    } else {
        ContentBackgroundSource.OWN
    }
    val name = profile.displayName()
    val sub = when {
        source == ContentBackgroundSource.OWN && !owned -> stringResource(Res.string.profile_bg_sub_app_surface)
        source == ContentBackgroundSource.OWN -> stringResource(Res.string.profile_bg_set_for, name, contentLabel)
        defaultScope.name in profile.backgroundOverrides -> stringResource(Res.string.profile_bg_sub_profile, name)
        else -> stringResource(Res.string.profile_bg_sub_app)
    }
    SettingsGroup(
        caption = stringResource(Res.string.profile_group_background),
        paths = scope.surfacePaths(),
        footer = {
            ComesFromStrip(
                scope = scope,
                defaultScope = defaultScope,
                draft = draft,
                profile = profile,
                contentLabel = contentLabel,
                source = source,
                onOpenBackground = onOpenBackground,
                onFollowApp = if (owned) {
                    { onProfileChange(profile.copy(backgroundOverrides = profile.backgroundOverrides - scope.name)) }
                } else {
                    null
                },
            )
        },
    ) {
        SettingsRow(stringResource(Res.string.profile_bg_row), sub = sub) {
            RowSegmented(
                options = listOf(
                    RowOption(
                        ContentBackgroundSource.PROFILE_DEFAULT,
                        stringResource(Res.string.profile_bg_profile_default),
                        BG_PROFILE_DEFAULT_TAG,
                    ),
                    RowOption(ContentBackgroundSource.OWN, stringResource(Res.string.profile_bg_own), BG_OWN_TAG),
                ),
                selected = source,
                onSelect = { picked ->
                    // Own on a surface still following the app's own takes it over as it is.
                    if (picked == source && (owned || picked != ContentBackgroundSource.OWN)) return@RowSegmented
                    val next = when (picked) {
                        ContentBackgroundSource.PROFILE_DEFAULT ->
                            config.copy(backgroundType = scope.inheritType.orEmpty())
                        // Seeded from what was showing, so taking it over starts from the same picture
                        // rather than from black.
                        ContentBackgroundSource.OWN -> seedFrom(backgrounds.resolvedConfigFor(scope), config)
                    }
                    onProfileChange(profile.withOwnSurface(scope, backgrounds, next))
                },
            )
        }
        if (source == ContentBackgroundSource.OWN) {
            val edit = ownershipEdit(scope, draft, profile, onProfileChange, onSettingsChange)
            // A lower-third surface can be a Lottie band as well as an ordinary picture; a full
            // screen has nowhere to play one. The template picker itself lives in the "Lower third
            // band" card, next to its height and its own App default/Own shortcut -- not here.
            BackgroundSurfaceRows(scope, draft, edit, includeLottie = scope.lowerThird, lottiePickerHere = false)
        }
    }
}

/**
 * [transform] applied through [onSettingsChange] once [scope] is this profile's own; while it still
 * follows the app's own, the *first* edit is what takes the surface over -- see [withOwnSurface] --
 * so it goes through [onProfileChange] instead, seeded from [draft], which is stale the instant that
 * write lands.
 *
 * Shared by [ContentBackgroundGroup]'s own Own/App default row and `BandGroup`'s Lottie picker, so
 * an edit made from either place claims ownership the same way.
 */
internal fun ownershipEdit(
    scope: BackgroundScope,
    draft: AppSettings,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
): ((AppSettings) -> AppSettings) -> Unit = { transform ->
    if (scope.name in profile.backgroundOverrides) {
        onSettingsChange(transform)
    } else {
        val updated = transform(draft).backgroundSettings
        onProfileChange(profile.withOwnSurface(scope, updated, updated.configFor(scope)))
    }
}

/** [resolved]'s picture on [own]'s other fields -- a surface taken over keeps the look it had. */
private fun seedFrom(resolved: BackgroundConfig, own: BackgroundConfig): BackgroundConfig = own.copy(
    backgroundType = resolved.backgroundType.takeUnless {
        it == Constants.BACKGROUND_DEFAULT || it == Constants.BACKGROUND_FOLLOW_DEFAULT
    } ?: Constants.BACKGROUND_COLOR,
    backgroundColor = resolved.backgroundColor,
    backgroundImage = resolved.backgroundImage,
    backgroundVideo = resolved.backgroundVideo,
    backgroundOpacity = resolved.backgroundOpacity,
    dim = resolved.dim,
    blur = resolved.blur,
    camera = resolved.camera,
)

/**
 * "Comes from: App default · Black › Sanctuary default · worship.jpg › Youth night Bible · uses
 * the level before", the level in effect ticked, then Open Background.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComesFromStrip(
    scope: BackgroundScope,
    defaultScope: BackgroundScope,
    draft: AppSettings,
    profile: OutputProfile,
    contentLabel: String,
    source: ContentBackgroundSource,
    onOpenBackground: () -> Unit,
    onFollowApp: (() -> Unit)?,
) {
    val palette = profilesPalette()
    val backgrounds = draft.backgroundSettings
    val profileOwnsDefault = defaultScope.name in profile.backgroundOverrides
    val usesBefore = stringResource(Res.string.profile_bg_level_uses_before)
    val app = stringResource(Res.string.profile_bg_app_default) + " · " +
        describeBackground(backgrounds.resolvedConfigFor(defaultScope))
    val profileDefault = stringResource(Res.string.profile_bg_profile_default_level, profile.displayName()) + " · " +
        if (profileOwnsDefault) describeBackground(backgrounds.resolvedConfigFor(defaultScope)) else usesBefore
    val own = stringResource(Res.string.profile_bg_set_for, profile.displayName(), contentLabel) + " · " +
        if (source == ContentBackgroundSource.OWN) describeBackground(backgrounds.configFor(scope)) else usesBefore
    val inEffect = when {
        source == ContentBackgroundSource.OWN -> 2
        profileOwnsDefault -> 1
        else -> 0
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.profile_bg_comes_from), fontSize = 11.sp, color = palette.faintText)
        listOf(app, profileDefault, own).forEachIndexed { index, text ->
            if (index > 0) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = palette.faintText,
                    modifier = Modifier.size(14.dp),
                )
            }
            LevelChip(text, inEffect = index == inEffect)
        }
        LinkText(stringResource(Res.string.profile_bg_open_background), onOpenBackground)
        if (onFollowApp != null) LinkText(stringResource(Res.string.profile_bg_follow_app), onFollowApp)
    }
}

@Composable
private fun LevelChip(text: String, inEffect: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val shape = AppShape(PILL_PERCENT)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (inEffect) scheme.primaryContainer else profilesPalette().card)
            .border(1.dp, if (inEffect) scheme.primary else profilesPalette().cardBorder, shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (inEffect) Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = if (inEffect) FontWeight.SemiBold else FontWeight.Normal,
            color = if (inEffect) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** An underlined word that does something -- "Open Background", "Undo". */
@Composable
internal fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = modifier.clip(AppShape(4.dp)).clickable(onClick = onClick).testTag(linkTag(text)).padding(2.dp),
    )
}

/** Test handle for a [LinkText], by its words. */
internal fun linkTag(text: String): String = "profile_link_$text"

/** Test handles for the Background row's two segments. */
internal const val BG_PROFILE_DEFAULT_TAG = "profile_bg_profile_default"
internal const val BG_OWN_TAG = "profile_bg_own"

/** A corner of half the height: the level chips are pills. */
private const val PILL_PERCENT = 50

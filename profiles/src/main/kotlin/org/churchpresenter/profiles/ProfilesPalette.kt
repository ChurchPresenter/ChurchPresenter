package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.churchpresenter.theme.isDarkScheme

/**
 * The layers the Profiles tab is built from, read off the theme's own roles so all thirteen themes
 * get a set that steps the same way: the profile list and the preview on the page tone, the section
 * list a shade deeper, the settings column on the lightest container, and the cards above that.
 *
 * Derived rather than named in `:theme` because every one of them is a role that already exists,
 * nudged toward the card it sits against -- the card itself is the only new tone, and on a light
 * theme it is simply white.
 */
@Immutable
internal data class ProfilesPalette(
    val rail: Color,
    val sections: Color,
    val page: Color,
    val card: Color,
    val cardBorder: Color,
    val rowDivider: Color,
    /** The third level of text: sub-lines, hints, the footer. */
    val faintText: Color,
    val banner: Color,
    val bannerBorder: Color,
)

@Composable
@ReadOnlyComposable
internal fun profilesPalette(): ProfilesPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkScheme(scheme)
    val card = lerp(scheme.surfaceContainer, Color.White, if (dark) CARD_LIFT_DARK else 1f)
    return ProfilesPalette(
        rail = scheme.background,
        sections = scheme.surface,
        page = scheme.surfaceContainer,
        card = card,
        cardBorder = lerp(scheme.outlineVariant, card, BORDER_FADE),
        rowDivider = lerp(scheme.outlineVariant, card, DIVIDER_FADE),
        faintText = lerp(scheme.onSurfaceVariant, card, FAINT_TEXT_FADE),
        banner = lerp(scheme.primaryContainer, card, BANNER_FADE),
        bannerBorder = scheme.primaryContainer,
    )
}

private const val CARD_LIFT_DARK = 0.05f
private const val BORDER_FADE = 0.45f
private const val DIVIDER_FADE = 0.72f
private const val FAINT_TEXT_FADE = 0.25f
private const val BANNER_FADE = 0.5f

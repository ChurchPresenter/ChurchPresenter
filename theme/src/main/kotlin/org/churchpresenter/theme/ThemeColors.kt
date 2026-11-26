package org.churchpresenter.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** What the output shows behind content: black, whatever the app's theme, as a projector does. */
private val OutputScreen = Color(0xFF0B0C0E)
private val OnOutputScreen = Color(0xFFFFFFFF)

/**
 * The colors components draw with, read from the app's active theme: its Material roles from
 * [scheme] and the roles Material has no name for from [semantic]. The names are the theme's own,
 * so a component asks for `live` or `onSurfaceFaint` and the theme decides what that is.
 */
@Immutable
class ThemeColors(private val scheme: ColorScheme, private val semantic: SemanticColors) {
    val primary get() = scheme.primary
    val onPrimary get() = scheme.onPrimary
    val primaryContainer get() = scheme.primaryContainer
    val onPrimaryContainer get() = scheme.onPrimaryContainer
    val secondary get() = scheme.secondary
    val onSecondary get() = scheme.onSecondary
    val secondaryContainer get() = scheme.secondaryContainer
    val onSecondaryContainer get() = scheme.onSecondaryContainer
    val tertiary get() = scheme.tertiary
    val onTertiary get() = scheme.onTertiary
    val tertiaryContainer get() = scheme.tertiaryContainer
    val onTertiaryContainer get() = scheme.onTertiaryContainer
    val error get() = scheme.error
    val onError get() = scheme.onError
    val errorContainer get() = scheme.errorContainer
    val onErrorContainer get() = scheme.onErrorContainer
    val background get() = scheme.background
    val onBackground get() = scheme.onBackground
    val surface get() = scheme.surface
    val onSurface get() = scheme.onSurface
    val surfaceVariant get() = scheme.surfaceVariant
    val onSurfaceVariant get() = scheme.onSurfaceVariant
    val surfaceContainerLowest get() = scheme.surfaceContainerLowest
    val surfaceContainerLow get() = scheme.surfaceContainerLow
    val surfaceContainer get() = scheme.surfaceContainer
    val surfaceContainerHigh get() = scheme.surfaceContainerHigh
    val surfaceContainerHighest get() = scheme.surfaceContainerHighest
    val outline get() = scheme.outline
    val outlineVariant get() = scheme.outlineVariant
    val inverseSurface get() = scheme.inverseSurface
    val inverseOnSurface get() = scheme.inverseOnSurface
    val scrim get() = scheme.scrim

    val success get() = semantic.success
    val onSuccess get() = semantic.onSuccess
    val successContainer get() = semantic.successContainer
    val onSuccessContainer get() = semantic.onSuccessContainer
    val live get() = semantic.live
    val onLive get() = semantic.onLive
    val star get() = semantic.favorite
    val onSurfaceFaint get() = semantic.onSurfaceFaint

    val song get() = semantic.contentSongs
    val bible get() = semantic.contentBible
    val presentation get() = semantic.contentPresentation
    val pictures get() = semantic.contentPictures
    val media get() = semantic.contentMedia
    val lowerThird get() = semantic.contentLowerThird
    val announcement get() = semantic.contentAnnouncement

    val verse get() = semantic.chordVerse
    val chorus get() = semantic.chordChorus
    val bridge get() = semantic.chordBridge
    val intro get() = semantic.chordIntro
    val voice get() = semantic.chordVoice
    val otherSection get() = semantic.chordOther

    /** The output's own black and white, the same in every theme. */
    val screen get() = OutputScreen
    val onScreen get() = OnOutputScreen
}

/** [mode]'s colors outside a composition -- what a test of a color rule reads. */
fun themeColorsFor(mode: ThemeMode): ThemeColors {
    val scheme = colorSchemeFor(mode)
    return ThemeColors(scheme, semanticColorsFor(scheme))
}

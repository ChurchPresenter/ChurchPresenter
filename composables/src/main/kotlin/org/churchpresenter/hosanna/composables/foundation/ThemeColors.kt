package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import org.churchpresenter.theme.SemanticColors
import org.churchpresenter.theme.semantic

/**
 * The app theme's colors as the composables read them: the Material roles of the current scheme and
 * the theme's semantic roles, under the app theme's own names.
 */
@Immutable
internal class ThemeColors(private val scheme: ColorScheme, private val semantic: SemanticColors) {
    val primary: Color get() = scheme.primary
    val onPrimary: Color get() = scheme.onPrimary
    val primaryContainer: Color get() = scheme.primaryContainer
    val onPrimaryContainer: Color get() = scheme.onPrimaryContainer
    val inversePrimary: Color get() = scheme.inversePrimary
    val secondary: Color get() = scheme.secondary
    val onSecondary: Color get() = scheme.onSecondary
    val secondaryContainer: Color get() = scheme.secondaryContainer
    val onSecondaryContainer: Color get() = scheme.onSecondaryContainer
    val tertiary: Color get() = scheme.tertiary
    val onTertiary: Color get() = scheme.onTertiary
    val tertiaryContainer: Color get() = scheme.tertiaryContainer
    val onTertiaryContainer: Color get() = scheme.onTertiaryContainer
    val background: Color get() = scheme.background
    val onBackground: Color get() = scheme.onBackground
    val surface: Color get() = scheme.surface
    val onSurface: Color get() = scheme.onSurface
    val surfaceVariant: Color get() = scheme.surfaceVariant
    val onSurfaceVariant: Color get() = scheme.onSurfaceVariant
    val surfaceTint: Color get() = scheme.surfaceTint
    val inverseSurface: Color get() = scheme.inverseSurface
    val inverseOnSurface: Color get() = scheme.inverseOnSurface
    val error: Color get() = scheme.error
    val onError: Color get() = scheme.onError
    val errorContainer: Color get() = scheme.errorContainer
    val onErrorContainer: Color get() = scheme.onErrorContainer
    val outline: Color get() = scheme.outline
    val outlineVariant: Color get() = scheme.outlineVariant
    val scrim: Color get() = scheme.scrim
    val surfaceBright: Color get() = scheme.surfaceBright
    val surfaceContainer: Color get() = scheme.surfaceContainer
    val surfaceContainerHigh: Color get() = scheme.surfaceContainerHigh
    val surfaceContainerHighest: Color get() = scheme.surfaceContainerHighest
    val surfaceContainerLow: Color get() = scheme.surfaceContainerLow
    val surfaceContainerLowest: Color get() = scheme.surfaceContainerLowest
    val surfaceDim: Color get() = scheme.surfaceDim

    val success: Color get() = semantic.success
    val onSuccess: Color get() = semantic.onSuccess
    val successContainer: Color get() = semantic.successContainer
    val onSuccessContainer: Color get() = semantic.onSuccessContainer
    val warning: Color get() = semantic.warning
    val onWarning: Color get() = semantic.onWarning
    val warningContainer: Color get() = semantic.warningContainer
    val onWarningContainer: Color get() = semantic.onWarningContainer
    val favorite: Color get() = semantic.favorite
    val faint: Color get() = semantic.faint
    val link: Color get() = semantic.link
    val live: Color get() = semantic.live
    val onLive: Color get() = semantic.onLive
    val contentBible: Color get() = semantic.contentBible
    val contentSongs: Color get() = semantic.contentSongs
    val contentPictures: Color get() = semantic.contentPictures
    val contentLowerThird: Color get() = semantic.contentLowerThird
    val contentPresentation: Color get() = semantic.contentPresentation
    val contentMedia: Color get() = semantic.contentMedia
    val contentAnnouncement: Color get() = semantic.contentAnnouncement
    val chordVerse: Color get() = semantic.chordVerse
    val chordChorus: Color get() = semantic.chordChorus
    val chordBridge: Color get() = semantic.chordBridge
    val sectionIntro: Color get() = semantic.sectionIntro
    val sectionVoice: Color get() = semantic.sectionVoice
    val sectionOther: Color get() = semantic.sectionOther
    val screen: Color get() = semantic.screen
    val onScreen: Color get() = semantic.onScreen

    companion object {
        /** The colors of the theme around the caller. */
        val current: ThemeColors
            @Composable
            @ReadOnlyComposable
            get() = ThemeColors(MaterialTheme.colorScheme, MaterialTheme.semantic)
    }
}

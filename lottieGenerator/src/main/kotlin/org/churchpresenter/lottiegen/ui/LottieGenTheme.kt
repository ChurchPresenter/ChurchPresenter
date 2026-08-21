package org.churchpresenter.lottiegen.ui

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode

/**
 * The tool's theme for the standalone window.
 *
 * The Material layer — colour scheme, typography, shapes, semantic colours — is
 * [ChurchPresenterTheme]'s, the same one every ChurchPresenter screen uses. This module used to
 * build its own: a `ColorScheme` derived from [LottieGenPalette] and a full `Typography` that
 * restated Material's defaults. Both were duplicates, and the colour one meant the tool's dialogs
 * and dropdown menus drifted from the app's whenever a theme changed on one side only.
 *
 * What stays the tool's own is the hand-drawn panel chrome: [LottieGenPalette] and the [Tokens]
 * that read it. Those are 51 roles — canvas checkerboard, transport track, live dot, badge and
 * logo chips — that Material has no equivalent for, so they are not duplication.
 *
 * [dark] picks the palette and the theme mode together; standalone stays dark, which is what the
 * generator has always looked like. Embedded in a host app it is [ProvideLottieGenPalette] that
 * runs instead — the host owns the MaterialTheme there and only the palette has to be supplied.
 */
@Composable
fun LottieGenTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    ChurchPresenterTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        ProvideLottieGenPalette(if (dark) DarkPalette else LightPalette, content)
    }
}

/**
 * Puts [palette] — and the scrollbar styling that goes with it — in scope for the panel chrome,
 * leaving the ambient MaterialTheme alone.
 *
 * This is the embedded path: ChurchPresenter has already applied its own theme around the
 * generator, so replacing it would be wrong, but the hand-drawn chrome still has to be told which
 * way it is being rendered. It also runs inside [LottieGenTheme], where its scrollbar deliberately
 * overrides the shared theme's: this tool's scrollbars sit on panel chrome, not on Material
 * surfaces.
 */
@Composable
fun ProvideLottieGenPalette(palette: LottieGenPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLottieGenPalette provides palette,
        LocalScrollbarStyle provides ScrollbarStyle(
            minimalHeight = 16.dp,
            thickness = 6.dp,
            shape = RoundedCornerShape(4.dp),
            hoverDurationMillis = 150,
            unhoverColor = palette.scrollThumb,
            hoverColor = palette.scrollThumbHover
        )
    ) {
        content()
    }
}

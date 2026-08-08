package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The colours that carry a meaning Material 3 has no role for.
 *
 * M3 gives us `error` and nothing else with a meaning: there is no "this connected", no "this is
 * still trying", no "you starred this". Every screen that needed one reached for a literal, and the
 * app ended up with four different greens for *connected* (`#4CAF50`, `#43A047`), four ambers for
 * *connecting*, and three reds for *failed* — none of which changed between the nine themes, and
 * several of which were chosen against a light background and then shown on a dark one.
 *
 * These are the roles those literals meant. They are defined once per light/dark palette here, which
 * is what makes them a theme rather than a habit: **a colour literal belongs in this file or in
 * `Theme.kt`, nowhere else.**
 *
 * Deliberately *not* in here, and deliberately still literal at their call sites:
 * - **The output.** The `presenter` package, the preview panes and the stage monitor paint black with white
 *   text because that is what a projector shows — the congregation's screen must not follow the
 *   operator's choice of app theme.
 * - **Colours being chosen or displayed as colours.** The picker's hue strip and chequerboard, the
 *   schedule's label swatches, a canvas source's own fill.
 * - **Contrast-over-arbitrary-content affordances.** The canvas editor's cyan selection handles have
 *   to stay legible over whatever image is under them, which a theme colour cannot promise.
 */
@Immutable
data class SemanticColors(
    /** Connected, running, succeeded. */
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** Connecting, degraded, needs attention but is not broken. */
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** Informational tags — a mode badge, a mirrored source. */
    val info: Color,
    /** A starred song. */
    val favorite: Color,
    /** The stripe marking the selected row in a list. */
    val marker: Color,
    /** Strong's original-language accents. */
    val hebrew: Color,
    val greek: Color,
    /** Song section accents, used by the chord preview and the lyric chips. */
    val chordVerse: Color,
    val chordChorus: Color,
    val chordBridge: Color,
    val chordTag: Color,
)

private val LightSemanticColors = SemanticColors(
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFD3EDD5),
    onSuccessContainer = Color(0xFF10391B),
    warning = Color(0xFF8A5A00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFBE7C2),
    onWarningContainer = Color(0xFF3D2800),
    info = Color(0xFF1565C0),
    favorite = Color(0xFFC28800),
    marker = Color(0xFFC4972A),
    hebrew = Color(0xFFB45309),
    greek = Color(0xFF1D4ED8),
    chordVerse = Color(0xFF8A5A00),
    chordChorus = Color(0xFF7B3FA6),
    chordBridge = Color(0xFF13704C),
    chordTag = Color(0xFF9E3B26),
)

private val DarkSemanticColors = SemanticColors(
    success = Color(0xFF6FD69A),
    onSuccess = Color(0xFF06371C),
    successContainer = Color(0xFF1E4630),
    onSuccessContainer = Color(0xFFC8EFD6),
    warning = Color(0xFFE8A33D),
    onWarning = Color(0xFF3D2800),
    warningContainer = Color(0xFF4A3413),
    onWarningContainer = Color(0xFFFBE7C2),
    info = Color(0xFF7FB4F0),
    favorite = Color(0xFFF0C04A),
    marker = Color(0xFFD8AC45),
    hebrew = Color(0xFFE8A33D),
    greek = Color(0xFF8FB3F5),
    chordVerse = Color(0xFFE8A33D),
    chordChorus = Color(0xFFD9A0F0),
    chordBridge = Color(0xFF6FD69A),
    chordTag = Color(0xFFF5A08E),
)

/**
 * The set matching [scheme], chosen by how light its surface is rather than by listing the nine
 * themes again — a list that would have to be updated in step with `colorSchemeFor` and would
 * silently hand a tenth theme the wrong half if it were not.
 */
internal fun semanticColorsFor(scheme: ColorScheme): SemanticColors =
    if (scheme.surface.luminance() < 0.5f) DarkSemanticColors else LightSemanticColors

internal val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** `MaterialTheme.semantic.success` — reads like the M3 roles it sits beside. */
val MaterialTheme.semantic: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current

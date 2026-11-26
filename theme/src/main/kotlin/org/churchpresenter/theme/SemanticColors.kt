package org.churchpresenter.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val DARK_LUMINANCE_THRESHOLD = 0.5f

/**
 * The colours that carry a meaning Material 3 has no role for.
 *
 * M3 gives us `error` and nothing else with a meaning: there is no "this connected", no "this is
 * still trying", no "you starred this". Every screen that needed one reached for a literal, and the
 * app ended up with four different greens for *connected* (`#4CAF50`, `#43A047`), four ambers for
 * *connecting*, and three reds for *failed* — none of which changed between the nine themes, and
 * several of which were chosen against a light background and then shown on a dark one.
 *
 * These are the roles those literals meant. Every theme defines its own set of them in `Theme.kt`,
 * beside its color scheme, so all of a theme's colors are in one place -- which is what makes them a
 * theme rather than a habit: **a colour literal belongs in `Theme.kt` (or this file), nowhere else.**
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
    /** Something that is on air right now: the LIVE badge and the edge round a live thumbnail. */
    val live: Color,
    val onLive: Color,
    /** Text and marks quieter than [ColorScheme.onSurfaceVariant]: placeholders, ticks, a paused bar. */
    val onSurfaceFaint: Color,
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
    val chordIntro: Color,
    val chordVoice: Color,
    /** A section of no named kind. */
    val chordOther: Color,
    /**
     * The eight kinds of content the app stores, colour-coded where they are listed side by side —
     * the storage folders in System settings. They carry no state: the hue says *which kind of
     * content this row is about*, never whether it is healthy, which `success`/`warning`/`error`
     * say beside them.
     */
    val contentBible: Color,
    val contentSongs: Color,
    val contentPictures: Color,
    val contentLowerThird: Color,
    val contentPresentation: Color,
    val contentMedia: Color,
    val contentCalendar: Color,
    val contentSchedule: Color,
    val contentAnnouncement: Color,
    /**
     * A setting a linked profile has changed from its master: the dot beside its label, and the
     * "3 changes" chip in the profile list.
     */
    val override: Color,
    val overrideContainer: Color,
    val onOverrideContainer: Color,
    /** The dashed edge round a value a linked profile takes from its master. */
    val inherited: Color,
    /** A value set for one translation or language rather than all of them -- the "Only KJV" chip. */
    val targetContainer: Color,
    val onTargetContainer: Color,
    /**
     * Handles drawn over a settings preview: margins and moving in [adjustHandle], size and band
     * height in [adjustAccent]. The preview is always a dark picture of an output, so these are
     * the same in every theme.
     */
    val adjustHandle: Color,
    val onAdjustHandle: Color,
    val adjustAccent: Color,
    val onAdjustAccent: Color,
    /** The line showing where a dragged row will land. */
    val dropIndicator: Color,
)

/**
 * The set matching [scheme], chosen by how light its surface is rather than by listing the nine
 * themes again — a list that would have to be updated in step with `colorSchemeFor` and would
 * silently hand a tenth theme the wrong half if it were not.
 */

fun isDarkScheme(scheme: ColorScheme): Boolean =
    scheme.surface.luminance() < DARK_LUMINANCE_THRESHOLD

internal val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** `MaterialTheme.semantic.success` — reads like the M3 roles it sits beside. */
val MaterialTheme.semantic: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current

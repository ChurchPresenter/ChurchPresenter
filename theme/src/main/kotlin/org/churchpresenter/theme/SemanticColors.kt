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

private val OutputScreen = Color(0xFF0B0C0E)
private val OnOutputScreen = Color(0xFFFFFFFF)

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
    /** On screen now: the live badge, the bar on a live row. */
    val live: Color,
    val onLive: Color,
    /** Text quieter than `onSurfaceVariant`: hints, placeholders, a disabled-looking edge that still reads. */
    val faint: Color,
    /** A link in running text. */
    val link: Color,
    /** The announcement content type, beside the eight above. */
    val contentAnnouncement: Color,
    /** Song section accents the chord colours do not cover. */
    val sectionIntro: Color,
    val sectionVoice: Color,
    val sectionOther: Color,
    /**
     * What an output screen shows behind and on top of content — a thumbnail's letterbox, the badges
     * drawn over a picture. The same in every theme, as the projector is.
     */
    val screen: Color,
    val onScreen: Color,
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
    contentBible = Color(0xFF2F6FD0),
    contentSongs = Color(0xFF6A4FC4),
    contentPictures = Color(0xFF158A76),
    contentLowerThird = Color(0xFFA06C14),
    contentPresentation = Color(0xFFB23F70),
    contentMedia = Color(0xFF1D78B4),
    contentCalendar = Color(0xFFC0392B),
    contentSchedule = Color(0xFF4E7F1C),
    override = Color(0xFFC27C0E),
    overrideContainer = Color(0xFFFBEBD0),
    onOverrideContainer = Color(0xFF8A5A0A),
    inherited = Color(0xFFC4C8CF),
    targetContainer = Color(0xFFE3EEFB),
    onTargetContainer = Color(0xFF1F4F8A),
    adjustHandle = Color(0xFF90CAF9),
    onAdjustHandle = Color(0xFF003258),
    adjustAccent = Color(0xFFFFB74D),
    onAdjustAccent = Color(0xFF3A2600),
    dropIndicator = Color(0xFF2A78D6),
    live = Color(0xFFD23B3B),
    onLive = Color(0xFFFFFFFF),
    faint = Color(0xFF878E97),
    link = Color(0xFF2F6FD0),
    contentAnnouncement = Color(0xFFA43A9C),
    sectionIntro = Color(0xFF4F6B8A),
    sectionVoice = Color(0xFF0F7F86),
    sectionOther = Color(0xFF6B7079),
    screen = OutputScreen,
    onScreen = OnOutputScreen,
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
    contentBible = Color(0xFF7FB2F7),
    contentSongs = Color(0xFFA894F5),
    contentPictures = Color(0xFF63CFBA),
    contentLowerThird = Color(0xFFE8AB5F),
    contentPresentation = Color(0xFFE88BAD),
    contentMedia = Color(0xFF6CBDF0),
    contentCalendar = Color(0xFFF08A7E),
    contentSchedule = Color(0xFFA5D37C),
    override = Color(0xFFE8A33D),
    overrideContainer = Color(0xFF4A3413),
    onOverrideContainer = Color(0xFFFBE7C2),
    inherited = Color(0xFF5C6068),
    targetContainer = Color(0xFF1E3552),
    onTargetContainer = Color(0xFFA9CBF5),
    adjustHandle = Color(0xFF90CAF9),
    onAdjustHandle = Color(0xFF003258),
    adjustAccent = Color(0xFFFFB74D),
    onAdjustAccent = Color(0xFF3A2600),
    dropIndicator = Color(0xFF7FB4F0),
    live = Color(0xFFFF6B6B),
    onLive = Color(0xFF2A0A0A),
    faint = Color(0xFF7C7F87),
    link = Color(0xFF8EBBF8),
    contentAnnouncement = Color(0xFFE597DE),
    sectionIntro = Color(0xFF9DB6D3),
    sectionVoice = Color(0xFF5CCFD4),
    sectionOther = Color(0xFFA4A7AE),
    screen = OutputScreen,
    onScreen = OnOutputScreen,
)

/** [LightSemanticColors] for the Light scheme itself: the same roles in its Hosanna palette. */
private val StandardLightSemanticColors = LightSemanticColors.copy(
    success = Color(0xFF0E7546),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFE0F4EA),
    onSuccessContainer = Color(0xFF0E7546),
    warning = Color(0xFF8A5A10),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFBEBD0),
    onWarningContainer = Color(0xFF8A5A10),
    info = Color(0xFF2F6FD0),
    favorite = Color(0xFFE0A526),
    marker = Color(0xFF2F6FD0),
    chordVerse = Color(0xFF9A6512),
    chordChorus = Color(0xFF8340B5),
    chordBridge = Color(0xFF1C7F5A),
    contentBible = Color(0xFF14855A),
    contentSongs = Color(0xFF2F6FD0),
    contentPictures = Color(0xFFC8501E),
    contentLowerThird = Color(0xFF0F7F86),
    contentPresentation = Color(0xFFC2416A),
    contentMedia = Color(0xFF5B4FC7),
    dropIndicator = Color(0xFF2F6FD0),
)

/** [DarkSemanticColors] for the Dark scheme itself: the same roles in its Hosanna palette. */
private val StandardDarkSemanticColors = DarkSemanticColors.copy(
    success = Color(0xFF6FDCAE),
    onSuccess = Color(0xFF0B3324),
    successContainer = Color(0xFF32433E),
    onSuccessContainer = Color(0xFF6FDCAE),
    warning = Color(0xFFF0C27A),
    onWarning = Color(0xFF3A2604),
    warningContainer = Color(0xFF4D4131),
    onWarningContainer = Color(0xFFF0C27A),
    info = Color(0xFF8EBBF8),
    favorite = Color(0xFFE0A526),
    marker = Color(0xFF9CC6F7),
    chordVerse = Color(0xFFE8B45A),
    chordChorus = Color(0xFFC79CF0),
    chordBridge = Color(0xFF6FD6A8),
    contentBible = Color(0xFF5FD3A0),
    contentSongs = Color(0xFF7AB0F8),
    contentPictures = Color(0xFFF4A072),
    contentLowerThird = Color(0xFF5CCFD4),
    contentPresentation = Color(0xFFF08AA8),
    contentMedia = Color(0xFFA99CF7),
    dropIndicator = Color(0xFF9CC6F7),
)

/**
 * The set matching [scheme], chosen by how light its surface is rather than by listing the nine
 * themes again — a list that would have to be updated in step with `colorSchemeFor` and would
 * silently hand a tenth theme the wrong half if it were not.
 */
fun semanticColorsFor(scheme: ColorScheme): SemanticColors = when {
    scheme === LightColorScheme -> StandardLightSemanticColors
    scheme === DarkColorScheme -> StandardDarkSemanticColors
    isDarkScheme(scheme) -> DarkSemanticColors
    else -> LightSemanticColors
}

/** The Light or Dark preset's own set: what the Custom theme starts from on that base. */
internal fun presetSemanticColors(dark: Boolean): SemanticColors =
    if (dark) StandardDarkSemanticColors else StandardLightSemanticColors

fun isDarkScheme(scheme: ColorScheme): Boolean =
    scheme.surface.luminance() < DARK_LUMINANCE_THRESHOLD

internal val LocalSemanticColors = staticCompositionLocalOf { StandardLightSemanticColors }

/** `MaterialTheme.semantic.success` — reads like the M3 roles it sits beside. */
val MaterialTheme.semantic: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current

package org.churchpresenter.app.churchpresenter.composables

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.backdrop_mode_both
import churchpresenter.composeapp.generated.resources.backdrop_mode_border
import churchpresenter.composeapp.generated.resources.backdrop_mode_fill
import churchpresenter.composeapp.generated.resources.backdrop_mode_off
import churchpresenter.composeapp.generated.resources.backdrop_preset_black_bar
import churchpresenter.composeapp.generated.resources.backdrop_preset_rounded_plate
import churchpresenter.composeapp.generated.resources.backdrop_preset_soft_shade
import churchpresenter.composeapp.generated.resources.backdrop_preset_thin_outline
import org.churchpresenter.core.models.text.TextBackdrop
import org.jetbrains.compose.resources.StringResource

/**
 * The four states a [TextBackdrop] can be in, as one choice rather than two switches.
 *
 * The record itself carries two independent booleans, which is what the renderer reads. It is not
 * what the person setting them is deciding: both answer one question ("what sits behind these
 * words?"), and asking it twice meant two buttons and two dialogs to see one result — and a pair of
 * answers, "band per line" and "box round the block", that contradict each other when both are
 * given. So the editor collapses the pair into this, and writes it back through [withMode].
 */
internal enum class TextBackdropMode(val label: StringResource) {
    OFF(Res.string.backdrop_mode_off),
    FILL(Res.string.backdrop_mode_fill),
    BORDER(Res.string.backdrop_mode_border),
    BOTH(Res.string.backdrop_mode_both);

    val drawsFill: Boolean get() = this == FILL || this == BOTH
    val drawsBorder: Boolean get() = this == BORDER || this == BOTH
}

/** Which of the four this record is currently in. */
internal val TextBackdrop.mode: TextBackdropMode
    get() = when {
        lineBackground && border -> TextBackdropMode.BOTH
        lineBackground -> TextBackdropMode.FILL
        border -> TextBackdropMode.BORDER
        else -> TextBackdropMode.OFF
    }

/**
 * The same record switched to [mode], keeping every colour and measurement.
 *
 * Turning a half off leaves its settings in place, so flipping back — which the toolbar button does
 * on a single click — returns the look that was there rather than the defaults.
 */
internal fun TextBackdrop.withMode(mode: TextBackdropMode): TextBackdrop =
    copy(lineBackground = mode.drawsFill, border = mode.drawsBorder)

/**
 * One finished look, applied whole.
 *
 * These cover what nearly every service actually wants — a hard black bar, a softer shade, a plain
 * outline, a rounded plate — so the common case is one click and the fields below are for tuning
 * rather than for building a look from defaults.
 */
internal class TextBackdropPreset(
    val label: StringResource,
    private val mode: TextBackdropMode,
    private val shape: TextBackdrop.() -> TextBackdrop,
) {
    /** [current] restyled to this preset: its own half is replaced, the other half is left alone. */
    fun applyTo(current: TextBackdrop): TextBackdrop = current.shape().withMode(mode)

    /** What the swatch draws — the preset's look on its own, not the settings it would land in. */
    val preview: TextBackdrop = TextBackdrop().shape().withMode(mode)
}

internal val TEXT_BACKDROP_PRESETS = listOf(
    TextBackdropPreset(Res.string.backdrop_preset_black_bar, TextBackdropMode.FILL) {
        copy(
            lineBackgroundColor = "#000000",
            lineBackgroundOpacity = 100,
            lineBackgroundHeight = 24,
            lineBackgroundOffset = 2,
        )
    },
    TextBackdropPreset(Res.string.backdrop_preset_soft_shade, TextBackdropMode.FILL) {
        copy(
            lineBackgroundColor = "#000000",
            lineBackgroundOpacity = 55,
            lineBackgroundHeight = 34,
            lineBackgroundOffset = 6,
        )
    },
    TextBackdropPreset(Res.string.backdrop_preset_thin_outline, TextBackdropMode.BORDER) {
        copy(
            borderColor = "#FFFFFF",
            borderOpacity = 100,
            borderWidth = 3,
            borderPadding = 18,
            borderRadius = 0,
        )
    },
    // With both halves on the padding is what opens the gap on all four sides, so this asks for no
    // extra height of its own -- a plate, not a plate with a band grown inside it.
    TextBackdropPreset(Res.string.backdrop_preset_rounded_plate, TextBackdropMode.BOTH) {
        copy(
            lineBackgroundColor = "#7A1246",
            lineBackgroundOpacity = 88,
            lineBackgroundHeight = 0,
            lineBackgroundOffset = 0,
            borderColor = "#FFFFFF",
            borderOpacity = 100,
            borderWidth = 2,
            borderPadding = 18,
            borderRadius = 14,
        )
    },
)

/**
 * One swatch in the presets row: what it draws, what it is called, and what picking it produces.
 *
 * The row mixes two kinds of entry and they do not behave the same. A saved look is applied
 * *whole*, mode included, because it is a finished thing the operator kept. A built-in replaces
 * only its own half and leaves the other alone -- see [TextBackdropPreset.applyTo] -- so picking
 * "Thin outline" over a plate keeps the plate's fill. Rather than teach the row about both, each
 * entry carries its own [apply].
 */
internal class BackdropChoice(
    /** What the swatch draws, and for a saved look what it is compared by. */
    val preview: TextBackdrop,
    /** A built-in's name; `null` marks one of the operator's own. */
    val label: StringResource?,
    val apply: (TextBackdrop) -> TextBackdrop,
)

/**
 * [saved] first, then as many of [TEXT_BACKDROP_PRESETS] as still fit.
 *
 * A built-in that matches a saved look is left out rather than drawn twice -- saving the black bar
 * unchanged should move it to the front of the row, not put a second copy at the back of it.
 */
internal fun backdropChoices(
    saved: List<TextBackdrop>,
    limit: Int = SavedTextBackdrops.MAX,
): List<BackdropChoice> {
    val mine = saved.map { look -> BackdropChoice(look, null) { look } }
    val builtIn = TEXT_BACKDROP_PRESETS
        .filterNot { it.preview in saved }
        .map { preset -> BackdropChoice(preset.preview, preset.label) { current -> preset.applyTo(current) } }
    return (mine + builtIn).take(limit)
}

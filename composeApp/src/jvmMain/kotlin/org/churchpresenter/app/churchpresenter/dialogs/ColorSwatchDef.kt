/*
 * What the Background panel's Colors category offers: the custom color first, then four solids and
 * four gradients.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_background_color_black
import churchpresenter.composeapp.generated.resources.song_background_color_dusk
import churchpresenter.composeapp.generated.resources.song_background_color_ember
import churchpresenter.composeapp.generated.resources.song_background_color_forest
import churchpresenter.composeapp.generated.resources.song_background_color_navy
import churchpresenter.composeapp.generated.resources.song_background_color_plum
import churchpresenter.composeapp.generated.resources.song_background_color_slate
import churchpresenter.composeapp.generated.resources.song_background_color_teal
import churchpresenter.composeapp.generated.resources.song_background_custom_color
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.jetbrains.compose.resources.StringResource

/**
 * One tile of the Colors grid. [own] marks the custom-colour tile, which draws the design's plus
 * badge and takes whatever colour the hex field currently holds instead of a fixed one.
 */
internal data class ColorSwatchDef(
    val label: StringResource,
    val color: String,
    val colorEnd: String? = null,
    val own: Boolean = false,
) {
    val gradient: Boolean get() = colorEnd != null

    /**
     * [background] switched to this tile, keeping its dim, blur and opacity.
     *
     * The custom tile has to land on a colour that is **not** one of [namedColors]: both its own
     * selected state and the hex field in the Look column key on the colour not being one of the
     * palette's, so keeping a named solid left the tile the user had just clicked reporting itself
     * unselected with no hex field — "Custom color" appeared to do nothing at all. It only appeared
     * to work after a *gradient* tile, whose near colour is not in the named set.
     */
    fun applyTo(
        background: SongBackground,
        namedColors: Set<String> = SONG_BACKGROUND_NAMED_COLORS,
    ): SongBackground = when {
        own -> background.copy(
            type = SongBackgroundType.COLOR,
            color = if (background.color.lowercase() in namedColors) color else background.color,
        )
        gradient -> background.copy(
            type = SongBackgroundType.GRADIENT,
            color = color,
            colorEnd = colorEnd.orEmpty(),
        )
        else -> background.copy(type = SongBackgroundType.COLOR, color = color)
    }

    /** Whether [background] is currently sitting on this tile. */
    fun selects(background: SongBackground, customColors: Set<String>): Boolean = when {
        own -> background.type == SongBackgroundType.COLOR && background.color.lowercase() !in customColors
        gradient -> background.type == SongBackgroundType.GRADIENT &&
            background.color.equals(color, ignoreCase = true) &&
            background.colorEnd.equals(colorEnd, ignoreCase = true)
        else -> background.type == SongBackgroundType.COLOR && background.color.equals(color, ignoreCase = true)
    }
}

internal val SONG_BACKGROUND_COLORS = listOf(
    // The custom tile leads: it is the one tile that is not a fixed choice, so it should not be
    // hunted for at the end of a scrolled grid.
    ColorSwatchDef(Res.string.song_background_custom_color, "#1b2436", own = true),
    ColorSwatchDef(Res.string.song_background_color_black, "#000000"),
    ColorSwatchDef(Res.string.song_background_color_navy, "#0d1b2a"),
    ColorSwatchDef(Res.string.song_background_color_plum, "#2a1130"),
    ColorSwatchDef(Res.string.song_background_color_forest, "#0f2018"),
    ColorSwatchDef(Res.string.song_background_color_ember, "#3b1408", "#7a2c10"),
    ColorSwatchDef(Res.string.song_background_color_dusk, "#131a3a", "#3a2352"),
    ColorSwatchDef(Res.string.song_background_color_teal, "#062a2e", "#0d4f52"),
    ColorSwatchDef(Res.string.song_background_color_slate, "#1a1f26", "#2f3945"),
)

/** The named solids, so the custom tile can tell it is holding a colour of the user's own. */
internal val SONG_BACKGROUND_NAMED_COLORS: Set<String> =
    SONG_BACKGROUND_COLORS.filter { !it.own && !it.gradient }.map { it.color.lowercase() }.toSet()

/** The six one-click colours the design puts under the hex field. */
internal val SONG_BACKGROUND_SUGGESTIONS =
    listOf("#000000", "#0d1b2a", "#1b2436", "#2a1130", "#0f2018", "#3a2a12")

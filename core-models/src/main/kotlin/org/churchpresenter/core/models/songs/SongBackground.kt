package org.churchpresenter.core.models.songs

import kotlinx.serialization.Serializable

/** The background kinds a song can carry. Blank is the absence of one — inherit from settings. */
object SongBackgroundType {
    const val INHERIT = ""
    const val COLOR = "color"
    const val GRADIENT = "gradient"
    const val IMAGE = "image"
    const val VIDEO = "video"

    val ALL = listOf(COLOR, GRADIENT, IMAGE, VIDEO)
}

/** The widest blur the editor offers, in reference pixels. */
const val SONG_BACKGROUND_MAX_BLUR = 24

/** A fully opaque background — what one is drawn at unless the operator says otherwise. */
const val SONG_BACKGROUND_FULL_OPACITY = 100

/**
 * A background a single song brings with it, overriding the global Background settings while that
 * song is live.
 *
 * Written into the `.song` file rather than into `AppSettings`, so it travels with the song into
 * any schedule and onto any machine — unlike [SongTuning], which is per-machine by design. Image
 * and video are absolute paths, so a song moved to a machine that lacks the file falls back to the
 * global background exactly as an unset one does.
 *
 * [type] blank means inherit; nothing else in the class is read in that case.
 */
@Serializable
data class SongBackground(
    val type: String = SongBackgroundType.INHERIT,
    val color: String = "#000000",
    /** The far end of a gradient; [color] is the near one. Unread for every other type. */
    val colorEnd: String = "#000000",
    val image: String = "",
    val video: String = "",
    /** Percent of black washed over the background, 0–100. The mockup's "Dim". */
    val dim: Int = 0,
    /** Blur radius in reference pixels, 0–[SONG_BACKGROUND_MAX_BLUR]. */
    val blur: Int = 0,
    /** How opaque the background is drawn, 0–100. 100 is the ordinary case. */
    val opacity: Int = SONG_BACKGROUND_FULL_OPACITY,
) {
    /** True when this song overrides the global background rather than inheriting it. */
    val isCustom: Boolean get() = type in SongBackgroundType.ALL

    /** The path this background draws from, empty for a colour or an inherited background. */
    val mediaPath: String get() = when (type) {
        SongBackgroundType.IMAGE -> image
        SongBackgroundType.VIDEO -> video
        else -> ""
    }
}

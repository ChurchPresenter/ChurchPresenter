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

/** The header key a song's full-screen background is written under, and the root of its siblings. */
const val SONG_BACKGROUND_PREFIX = "background"

/** The same for the lower-third band. */
const val SONG_LOWER_THIRD_BACKGROUND_PREFIX = "lower-third-background"

private const val SONG_BACKGROUND_PERCENT_MAX = 100

/**
 * Every key one background occupies under [prefix]: the type itself plus its value keys.
 *
 * The same vocabulary serves the `.song` header, where it is written `key: value` a line apart, and
 * a per-section directive in the lyrics, where it is written `[key: value]` — one grammar rather
 * than two, so a background reads the same wherever it is stored.
 */
fun songBackgroundKeys(prefix: String): List<String> =
    listOf(prefix) + listOf("color", "color-end", "image", "video", "dim", "blur").map { "$prefix-$it" }

/**
 * The [SongBackground] held under [prefix] in [fields], or an inheriting one when nothing there
 * names a type this build knows.
 */
fun songBackgroundFrom(fields: Map<String, String>, prefix: String): SongBackground {
    val type = fields[prefix].orEmpty().trim().lowercase()
    if (type !in SongBackgroundType.ALL) return SongBackground()
    return SongBackground(
        type = type,
        color = fields["$prefix-color"]?.takeIf { it.isNotBlank() } ?: "#000000",
        colorEnd = fields["$prefix-color-end"]?.takeIf { it.isNotBlank() } ?: "#000000",
        image = fields["$prefix-image"].orEmpty(),
        video = fields["$prefix-video"].orEmpty(),
        dim = fields["$prefix-dim"]?.toIntOrNull()?.coerceIn(0, SONG_BACKGROUND_PERCENT_MAX) ?: 0,
        blur = fields["$prefix-blur"]?.toIntOrNull()?.coerceIn(0, SONG_BACKGROUND_MAX_BLUR) ?: 0,
    )
}

/**
 * [background] as the key/value pairs that record it under [prefix], in writing order — empty when
 * it inherits, since an inherited background is stored by writing nothing at all.
 *
 * Only what the type actually reads is written: a colour background records no gradient end, and
 * dim and blur appear only when they are set to something.
 */
fun songBackgroundFields(background: SongBackground, prefix: String): List<Pair<String, String>> {
    if (!background.isCustom) return emptyList()
    return buildList {
        add(prefix to background.type)
        when (background.type) {
            SongBackgroundType.COLOR -> add("$prefix-color" to background.color)
            SongBackgroundType.GRADIENT -> {
                add("$prefix-color" to background.color)
                add("$prefix-color-end" to background.colorEnd)
            }
            SongBackgroundType.IMAGE -> add("$prefix-image" to background.image)
            SongBackgroundType.VIDEO -> add("$prefix-video" to background.video)
        }
        if (background.dim > 0) add("$prefix-dim" to background.dim.toString())
        if (background.blur > 0) add("$prefix-blur" to background.blur.toString())
    }
}

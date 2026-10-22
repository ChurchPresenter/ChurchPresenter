package org.churchpresenter.core.models.songs

import kotlinx.serialization.Serializable

/**
 * One song's own changes to how its lyrics look, over whatever the output's profile says: its
 * font, its size, its text colour and its second voice's colour. Blank or 100% means the profile
 * decides, so a song that sets nothing looks exactly as every other song does.
 *
 * Written in the `.song` header as `font:`, `size:`, `color:` and `voice2:` — see
 * [songTextOverridesFrom] and [songTextOverrideFields].
 */
@Serializable
data class SongTextOverrides(
    /** A font family name, or blank for the profile's. */
    val font: String = "",
    /** The profile's lyric size scaled by this percentage; auto-fit still shrinks it to fit. */
    val sizePercent: Int = FULL_SIZE_PERCENT,
    /** A `#RRGGBB` lyric colour, or blank for the profile's. */
    val color: String = "",
    /** A `#RRGGBB` colour for the second voice, or blank for the profile's. */
    val secondVoiceColor: String = "",
) {
    /** True when the song changes anything at all. */
    val isCustom: Boolean get() = this != SongTextOverrides()

    /** [sizePercent] as a factor, held to the range the editor offers. */
    val sizeScale: Float get() = sizePercent.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT) / FULL_SIZE_PERCENT.toFloat()

    companion object {
        const val FULL_SIZE_PERCENT = 100
        const val MIN_SIZE_PERCENT = 50
        const val MAX_SIZE_PERCENT = 200
    }
}

/** The header keys the overrides are written under. */
internal const val SONG_FONT_KEY = "font"
internal const val SONG_SIZE_KEY = "size"
internal const val SONG_COLOR_KEY = "color"
internal const val SONG_VOICE2_KEY = "voice2"

/** Every header key [songTextOverridesFrom] reads. */
internal val SONG_TEXT_OVERRIDE_KEYS = setOf(SONG_FONT_KEY, SONG_SIZE_KEY, SONG_COLOR_KEY, SONG_VOICE2_KEY)

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

/**
 * The overrides a `.song` header's [fields] carry. A size is read with or without its `%`; a
 * colour that is not `#RRGGBB` (or `#AARRGGBB`) and a size that is not a number are ignored, so a
 * hand-typed mistake costs the override rather than the song.
 */
fun songTextOverridesFrom(fields: Map<String, String>): SongTextOverrides = SongTextOverrides(
    font = fields[SONG_FONT_KEY].orEmpty().trim(),
    sizePercent = fields[SONG_SIZE_KEY]?.trim()?.removeSuffix("%")?.trim()?.toIntOrNull()
        ?.coerceIn(SongTextOverrides.MIN_SIZE_PERCENT, SongTextOverrides.MAX_SIZE_PERCENT)
        ?: SongTextOverrides.FULL_SIZE_PERCENT,
    color = fields[SONG_COLOR_KEY].orEmpty().trim().takeIf { HEX_COLOR.matches(it) }.orEmpty(),
    secondVoiceColor = fields[SONG_VOICE2_KEY].orEmpty().trim().takeIf { HEX_COLOR.matches(it) }.orEmpty(),
)

/** The header lines [overrides] is written as, in a fixed order; none for what the profile decides. */
fun songTextOverrideFields(overrides: SongTextOverrides): List<Pair<String, String>> = buildList {
    if (overrides.font.isNotBlank()) add(SONG_FONT_KEY to overrides.font)
    if (overrides.sizePercent != SongTextOverrides.FULL_SIZE_PERCENT) add(SONG_SIZE_KEY to "${overrides.sizePercent}%")
    if (overrides.color.isNotBlank()) add(SONG_COLOR_KEY to overrides.color)
    if (overrides.secondVoiceColor.isNotBlank()) add(SONG_VOICE2_KEY to overrides.secondVoiceColor)
}

package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * The home for a new song setting, whatever it is about -- layout, as the name says, and by now
 * styling too.
 *
 * Several unrelated pieces folded into one field rather than one each.
 *
 * [SongSettings] was already within a few slots of the JVM's 255-constructor-parameter ceiling --
 * see [SongOutlines] -- so a single field here costs it one slot no matter how many of these are
 * added later, where four flat fields would have cost it four and risked the same
 * `ClassFormatError: Too many arguments in method signature` [SongOutlines] describes. Group new
 * settings here rather than adding another top-level field to [SongSettings].
 */
@Serializable
data class SongLayoutExtras(
    /** Shrinks/repositions the whole lyrics block -- see [ContentRegion]. */
    val contentRegion: ContentRegion = ContentRegion(),
    /** The current section's own label, drawn above the lyrics -- see [SongSectionLabel]. */
    val sectionLabel: SongSectionLabel = SongSectionLabel(),
    /** Fine X/Y nudge on top of [SongSettings.songNumberCorner], for the full-screen output. */
    val numberOffset: SongNumberOffset = SongNumberOffset(),
    /** [numberOffset] for the lower third. */
    val numberLowerThirdOffset: SongNumberOffset = SongNumberOffset(),
    /**
     * Where the lyrics block sits, when it is positioned rather than aligned -- see [ElementOffset].
     *
     * Null, the default, leaves `SongSettings.lyricsAlignment` placing it exactly as it always has.
     * Full screen only, like [contentRegion] beside it, so there is no lower-third twin.
     */
    val lyricsOffset: ElementOffset? = null,
    /**
     * The title slide's own song number -- its look, its corner and its offset.
     *
     * Separate from the lyric slides' number, which the title slide used to share; see
     * [SongTitleSlideNumber].
     */
    val titleSlideNumber: SongTitleSlideNumber = SongTitleSlideNumber(),
    /**
     * Where the title slide's other five elements sit -- see [SongTitleSlideOffsets].
     *
     * The number is not among them: [titleSlideNumber] above already carries its own corner and
     * nudge, which is a richer placement than an offset and the one that slide's number wants.
     */
    val titleSlideOffsets: SongTitleSlideOffsets = SongTitleSlideOffsets(),
)

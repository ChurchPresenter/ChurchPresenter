package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * Four unrelated pieces of layout added at once, folded into one field rather than four.
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
)

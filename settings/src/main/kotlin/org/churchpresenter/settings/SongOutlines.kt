package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextOutline

/**
 * The stroke drawn around each song profile's glyphs, one [TextOutline] per profile.
 *
 * **One record because [SongSettings] is at the JVM's parameter ceiling.** A class's constructor may
 * take 255 slots and no more, and kotlinx.serialization generates a synthetic constructor taking
 * every property plus one `seen` mask per 32 of them plus a marker — so at 240 properties
 * `SongSettings` was already using 250 of the 255. Adding these ten as flat fields compiled
 * cleanly and then failed at class-load with `ClassFormatError: Too many arguments in method
 * signature`, which is to say the app did not start at all. Nested, they cost one.
 *
 * So: **a new song setting goes in a record, not in [SongSettings] directly**, and if it belongs
 * with something already nested it goes in that record. There are three slots left.
 *
 * The names are the profiles' own, dropping the `Outline` suffix a field of this type does not need
 * — `textEffects.lyricsLowerThird` is the lower third's lyrics outline.
 */
@Serializable
data class SongOutlines(
    val songNumber: TextOutline = TextOutline(),
    val songNumberLowerThird: TextOutline = TextOutline(),
    val title: TextOutline = TextOutline(),
    val titleLowerThird: TextOutline = TextOutline(),
    val lyrics: TextOutline = TextOutline(),
    val lyricsLowerThird: TextOutline = TextOutline(),
    val lookAhead: TextOutline = TextOutline(),
    val lookAheadLowerThird: TextOutline = TextOutline(),
    val nextSection: TextOutline = TextOutline(),
    val nextSectionLowerThird: TextOutline = TextOutline(),
)

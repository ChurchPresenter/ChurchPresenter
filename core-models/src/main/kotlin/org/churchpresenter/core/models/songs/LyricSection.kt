package org.churchpresenter.core.models.songs

data class LyricSection(
    val header: String? = null,
    val labelName: String = "",
    val title: String = "",
    val secondaryTitle: String = "",
    val songNumber: Int = 0,
    val type: String = "", // "verse", "chorus"
    val lines: List<String> = emptyList(),
    val secondaryLines: List<String> = emptyList(),
    val isLastSection: Boolean = false,
    val bpm: Int = 0, // metronome tempo for this song (0 = off)
    val capo: Int = 0, // capo the chart is read with (0 = none)
    /**
     * The section's lines as written, chord markers still in, for the stage monitor to draw a chart
     * from. Held apart from [lines] rather than inside it so no presenter can show a chord by
     * accident: [lines] is what the audience reads and is always stripped.
     *
     * Empty when the song has no chords. Unlike [lines] it keeps chord-only lines — an intro has no
     * words to present but is exactly what the band needs.
     */
    val chordLines: List<String> = emptyList(),
    /**
     * The song's own full-screen background, carried here for the same reason [bpm] and [capo] are:
     * the presenter is handed a section, never the [SongItem] it came from. Inheriting by default,
     * in which case the global Background settings decide.
     */
    val background: SongBackground = SongBackground(),
    /** The same for the lower-third band. */
    val lowerThirdBackground: SongBackground = SongBackground(),
)

/** [section] with [song]'s own backgrounds stamped on, so the presenter can draw them. */
fun LyricSection.withBackgroundsOf(song: SongItem): LyricSection =
    copy(background = song.background, lowerThirdBackground = song.lowerThirdBackground)

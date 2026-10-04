package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection

/**
 * One crossfade layer's page: the section it draws, the line within it, the fit it is drawn at and
 * where it sits in its song -- which the look-ahead and the first-page rules read.
 */
internal data class SongCrossfadePage(
    val section: LyricSection,
    val lineIndex: Int,
    val fit: SongFit? = null,
    val allSections: List<LyricSection> = emptyList(),
    val sectionIndex: Int = -1,
)

/**
 * Whether [next] is this section drawn again rather than another slide: the same section, or the
 * same one stamped with a different tuning. The section that goes out carries the song's tempo and
 * capo, so changing either re-sends a slide that looks exactly as it did -- and crossfading a slide
 * into itself dims it by a quarter at the midpoint.
 */
fun LyricSection.isRestatedAs(next: LyricSection): Boolean =
    this == next || copy(bpm = next.bpm, capo = next.capo) == next

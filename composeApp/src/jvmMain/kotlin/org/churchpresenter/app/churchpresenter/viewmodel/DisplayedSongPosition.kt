package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.songs.LyricSection

/**
 * Where in the song the outputs are: the song's sections, the section on screen and the line within
 * it -- published together with `PresenterManager.displayedLyricSection`.
 *
 * The operator's position moves the moment a slide is clicked, while the displayed section follows
 * through the transition collector a frame or more later. An output that paired the displayed
 * section with the live position drew the old section against the new one's place in the song for
 * that frame: the look-ahead showed the verse two ahead, and a slide fitted on its own was drawn at
 * the size of the slide replacing it.
 */
data class DisplayedSongPosition(
    val allSections: List<LyricSection> = emptyList(),
    val sectionIndex: Int = -1,
    val lineIndex: Int = -1,
)

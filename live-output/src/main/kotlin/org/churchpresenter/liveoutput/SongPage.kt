package org.churchpresenter.liveoutput

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.presenter.isRestatedAs
import org.churchpresenter.sharedui.models.Presenting

/**
 * One page of a song on air: the section, and where it sits in the song -- its section and line.
 * A section re-sent with only its tempo or capo changed is the same page ([isRestatedAs]).
 */
internal data class SongPage(val section: LyricSection, val sectionIndex: Int, val lineIndex: Int) {
    fun isSamePageAs(next: SongPage): Boolean =
        section.isRestatedAs(next.section) && sectionIndex == next.sectionIndex && lineIndex == next.lineIndex
}

/**
 * The slide on air moved on to its next page -- a section, a line, verses -- while [mode] is the
 * slide: the overlays over it and a message come down, as they do when slide content goes live
 * ([PresenterManager.setPresentingMode]). A hymn number put up as an announcement comes down with
 * the hymn's first line, however that line was sent. An overlay put up over a page stays until the
 * next one; a page of other content than the slide's changes nothing on air.
 */
internal fun PresenterManager.slidePageTurned(mode: Presenting) {
    if (slideContent.value != mode) return
    overlayLayers.clearAllOverlays()
    liveShow.clear(Layer.MESSAGES)
}

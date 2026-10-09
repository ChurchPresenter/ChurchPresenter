package org.churchpresenter.app.churchpresenter

import org.churchpresenter.presentationengine.model.Deck
import java.io.File

/**
 * The selected presentation as the clicker and the live-slide push read and move it: where it stands,
 * its rendered slides, notes and parsed deck, and a step either way (which also tells any Instance Link
 * follower). The wiring adapts the Presentation tab's view model to it.
 */
internal interface PresentationSlideCursor {
    val selectedSlideIndex: Int
    val slideFiles: List<File>
    val presentationName: String?
    val slideNotes: List<String>
    val deck: Deck?
    fun nextShownSlideIndex(index: Int): Int?
    fun nextSlide()
    fun previousSlide()
}

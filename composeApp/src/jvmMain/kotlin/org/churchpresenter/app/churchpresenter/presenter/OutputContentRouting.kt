package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.settings.ScreenAssignment

/**
 * Whether an output shows a given kind of content -- the per-content-type visibility gate every
 * real output obeys.
 *
 * The single definition of that mapping. It was written out three times (here, the live preview
 * panel, and the Browser Source renderer), which is three places to forget when a content type is
 * added: a preview that answers differently from the output it previews shows the operator a
 * picture the screen is not displaying.
 */
fun showsContentFor(mode: Presenting, assignment: ScreenAssignment): Boolean = when (mode) {
    Presenting.BIBLE -> assignment.showBible
    Presenting.LYRICS -> assignment.showSongs
    Presenting.PICTURES, Presenting.PRESENTATION -> assignment.showPictures
    Presenting.ANNOUNCEMENTS -> assignment.showAnnouncements
    Presenting.LOWER_THIRD -> assignment.showStreaming
    Presenting.MEDIA -> assignment.showMedia
    Presenting.WEBSITE -> assignment.showWebsite
    Presenting.CANVAS -> assignment.showCanvas
    Presenting.QA -> assignment.showQA
    Presenting.STT -> assignment.showSTT
    Presenting.DICTIONARY -> assignment.showDictionary
    Presenting.NONE -> false
}

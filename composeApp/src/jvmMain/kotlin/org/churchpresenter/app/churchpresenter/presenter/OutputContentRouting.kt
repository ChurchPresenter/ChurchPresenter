package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.settings.OutputProfile

/**
 * Whether an output shows a given kind of content -- the per-content-type visibility gate every
 * real output obeys, via the [OutputProfile] it is assigned to.
 *
 * The single definition of that mapping. It was written out three times (here, the live preview
 * panel, and the Browser Source renderer), which is three places to forget when a content type is
 * added: a preview that answers differently from the output it previews shows the operator a
 * picture the screen is not displaying.
 */
fun showsContentFor(mode: Presenting, profile: OutputProfile): Boolean = when (mode) {
    Presenting.BIBLE -> profile.showBible
    Presenting.LYRICS -> profile.showSongs
    Presenting.PICTURES, Presenting.PRESENTATION -> profile.showPictures
    Presenting.ANNOUNCEMENTS -> profile.showAnnouncements
    Presenting.LOWER_THIRD -> profile.showStreaming
    Presenting.MEDIA -> profile.showMedia
    Presenting.WEBSITE -> profile.showWebsite
    Presenting.CANVAS -> profile.showCanvas
    Presenting.QA -> profile.showQA
    Presenting.STT -> profile.showSTT
    Presenting.DICTIONARY -> profile.showDictionary
    Presenting.NONE -> false
}

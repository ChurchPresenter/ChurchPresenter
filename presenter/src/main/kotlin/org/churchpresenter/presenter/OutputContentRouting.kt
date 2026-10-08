package org.churchpresenter.presenter

import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.sharedui.models.Presenting

/**
 * Whether an output shows a given kind of content -- the per-content-type visibility gate every
 * real output obeys: its [OutputProfile]'s look, and for scripture and songs its language modes.
 *
 * The single definition of that mapping. It was written out three times (here, the live preview
 * panel, and the Browser Source renderer), which is three places to forget when a content type is
 * added: a preview that answers differently from the output it previews shows the operator a
 * picture the screen is not displaying.
 */
fun showsContentFor(mode: Presenting, profile: OutputProfile): Boolean = when (mode) {
    Presenting.BIBLE -> profile.showBible
    Presenting.LYRICS -> profile.showSongs
    Presenting.PICTURES, Presenting.PRESENTATION -> profile.look.media.pictures
    Presenting.ANNOUNCEMENTS -> profile.look.announcements
    Presenting.LOWER_THIRD -> profile.look.graphics
    Presenting.MEDIA -> profile.look.media.video
    Presenting.WEBSITE -> profile.look.slide.web
    Presenting.CANVAS -> profile.look.slide.canvas
    Presenting.QA -> profile.look.slide.qa
    Presenting.STT -> profile.look.captions
    Presenting.DICTIONARY -> profile.look.slide.dictionary
    Presenting.MESSAGE -> profile.look.messages
    Presenting.PROPS -> profile.look.props
    Presenting.NONE -> false
}

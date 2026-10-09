package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants

/**
 * Whether an output on this profile offers the musicians' transpose: a Stage Monitor drawing
 * chords, with the profile's own switch on. The one rule behind the page's buttons, the routes
 * that accept a press, and the desktop tile's control.
 */
fun OutputProfile.offersTranspose(): Boolean =
    displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR && showChords && showTransposeControls

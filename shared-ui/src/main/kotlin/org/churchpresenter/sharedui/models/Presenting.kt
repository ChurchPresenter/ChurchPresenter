package org.churchpresenter.sharedui.models

/**
 * The live-content enum. [DRAWING] is a page drawn on a phone or tablet's canvas and sent live
 * through the companion server -- not to be confused with [CANVAS], the scene editor.
 */
enum class Presenting {
    NONE, BIBLE, LYRICS, PICTURES, DRAWING, PRESENTATION, MEDIA, LOWER_THIRD, ANNOUNCEMENTS, WEBSITE, CANVAS, QA,
    STT, DICTIONARY, MESSAGE, PROPS
}

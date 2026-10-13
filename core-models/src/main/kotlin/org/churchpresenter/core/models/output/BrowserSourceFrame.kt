package org.churchpresenter.core.models.output

/**
 * One emitted delta: a PNG-encoded sub-rectangle of the full [fullWidth]x[fullHeight] frame,
 * positioned at ([x], [y]). A full-frame delta has x=0, y=0, rectWidth=fullWidth,
 * rectHeight=fullHeight — sent for the very first tick and whenever a new HTTP subscriber
 * attaches, since a brand-new client's compositing canvas has nothing to apply a partial rect
 * onto yet.
 *
 * A plain class, not a data class: a frame is passed through, never compared, and equality is
 * identity -- comparing [png] by content would cost a frame's worth of bytes every time.
 */
class BrowserSourceFrame(
    val x: Int,
    val y: Int,
    val rectWidth: Int,
    val rectHeight: Int,
    val fullWidth: Int,
    val fullHeight: Int,
    val png: ByteArray,
)

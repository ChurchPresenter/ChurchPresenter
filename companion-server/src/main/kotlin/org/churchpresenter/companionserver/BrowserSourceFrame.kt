package org.churchpresenter.companionserver

/**
 * One emitted delta: a PNG-encoded sub-rectangle of the full [fullWidth]x[fullHeight] frame,
 * positioned at ([x],[y]). A full-frame delta has x=0, y=0, rectWidth=fullWidth,
 * rectHeight=fullHeight — sent for the very first tick and whenever a new HTTP subscriber
 * attaches, since a brand-new client's compositing canvas has nothing to apply a partial rect
 * onto yet. Note: default `equals()`/`hashCode()` on [png] is reference-based, not content-based
 * — harmless since nothing ever compares instances, only passes them through.
 *
 * It lives here rather than beside the renderer that produces it because the wire format is this
 * module's: [BrowserSourceHub.encodeBrowserSourceFrameMessage] is what turns one into bytes.
 */
data class BrowserSourceFrame(
    val x: Int,
    val y: Int,
    val rectWidth: Int,
    val rectHeight: Int,
    val fullWidth: Int,
    val fullHeight: Int,
    val png: ByteArray,
)

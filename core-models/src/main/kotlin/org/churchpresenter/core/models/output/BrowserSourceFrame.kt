package org.churchpresenter.core.models.output

/** Where a delta sits in the full frame: its top-left corner and its size, in pixels. */
data class FrameRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * One emitted delta: a PNG-encoded sub-rectangle of the full [fullWidth]x[fullHeight] frame, at
 * [rect]. A full-frame delta's rect is the whole frame -- sent for the very first tick and whenever
 * a new HTTP subscriber attaches, since a brand-new client's compositing canvas has nothing to apply
 * a partial rect onto yet.
 *
 * A plain class, not a data class: a frame is passed through, never compared, and equality is
 * identity -- comparing [png] by content would cost a frame's worth of bytes every time.
 */
class BrowserSourceFrame(
    val rect: FrameRect,
    val fullWidth: Int,
    val fullHeight: Int,
    val png: ByteArray,
)

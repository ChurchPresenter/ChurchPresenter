package org.churchpresenter.web.offscreen

import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

/** One published picture of the page: the view, smaller copies for small windows, and the open popup over it. */
class OffscreenFrame(
    val view: Image,
    /** [view] halved, quartered and so on, largest first; empty when [view] is already small. */
    val levels: List<Image>,
    /** The open popup (a `<select>` list), drawn over [view]; null while none is open. */
    val popup: OffscreenPopup?,
    /** `System.nanoTime()` when Chromium handed the paint over, for paint-to-draw latency. */
    val paintedAt: Long,
    /** Counts of new [view]s and new [levels] published so far, for each window's skipped-frame count. */
    val viewSeq: Long,
    val levelSeq: Long,
)

/** An open popup's picture, and where it sits on the page, in the view's pixels. */
class OffscreenPopup(val image: Image, val rect: Rect)

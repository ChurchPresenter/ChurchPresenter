package org.churchpresenter.presenter

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/** A presenter's background on one output, resolved once per composition. */
internal class PresenterBackdrop(val resolvedBg: ResolvedBackground, val backgroundImageBitmap: ImageBitmap?) {
    val bgDimPercent = resolvedBg.dimPercent
    val bgBlurReferencePx = resolvedBg.blurReferencePx
    val effectiveOpacity = resolvedBg.opacity
    val bgModifier: Modifier = backgroundModifier(resolvedBg, backgroundImageBitmap)

    // A blurred background has to be its own layer — blurring the box the text sits in would blur
    // the text with it, so an unblurred one keeps the original single-box shape and its behaviour.
    val blurred = bgBlurReferencePx > 0
}

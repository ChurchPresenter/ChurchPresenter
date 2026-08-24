package org.churchpresenter.lowerthird

import org.churchpresenter.companionserver.LottieFrameRenderer

/**
 * The real [LottieFrameRenderer]: composes the lottie offscreen with Skia.
 *
 * It is the whole reason the interface exists. [LowerThirdOffscreenRenderer] needs Compose and a
 * Skia surface, and `:companion-server` — which owns the render cache the frames are written into —
 * deliberately has neither. Everything that asks the cache to render passes this; a test passes
 * frames of its own.
 */
object SkiaLottieFrameRenderer : LottieFrameRenderer {
    override suspend fun withSession(
        width: Int,
        height: Int,
        lottieJson: String,
        initialProgress: Float,
        block: suspend (renderFrame: suspend (Float) -> IntArray) -> Unit,
    ) {
        LowerThirdOffscreenRenderer(width, height).withSession(lottieJson, initialProgress, block)
    }
}

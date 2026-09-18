package org.churchpresenter.app.churchpresenter.presenter

/**
 * The box slot [name] should draw into this frame: its own slot, widened to the union of both
 * halves when its paired slot (Text2 for Text1, Reference2 for Reference1, and back) has nothing
 * to show — a per-screen override to one language, or a title/section with content for only one —
 * so the remaining text fills the space rather than leaving the other half blank.
 *
 * Its own small file rather than a fourth (or twelfth) function on [BibleLottieTemplate] or
 * `BibleLottieBand.kt` — both already sit at the `TooManyFunctions` ceiling.
 */
internal fun BibleLottieTemplate.effectiveBox(name: String, texts: Map<String, String?>): LottieSlotBox {
    val paired = when (name) {
        BibleLottieTemplate.LAYER_TEXT_1 -> BibleLottieTemplate.LAYER_TEXT_2
        BibleLottieTemplate.LAYER_TEXT_2 -> BibleLottieTemplate.LAYER_TEXT_1
        BibleLottieTemplate.LAYER_REFERENCE_1 -> BibleLottieTemplate.LAYER_REFERENCE_2
        BibleLottieTemplate.LAYER_REFERENCE_2 -> BibleLottieTemplate.LAYER_REFERENCE_1
        else -> null
    }
    val own = slots[name] ?: LottieSlotBox(0f, 0f, width, height)
    if (paired == null || !texts[paired].isNullOrBlank()) return own
    val other = slots[paired] ?: return own
    val minX = minOf(own.x, other.x)
    val minY = minOf(own.y, other.y)
    val maxX = maxOf(own.x + own.w, other.x + other.w)
    val maxY = maxOf(own.y + own.h, other.y + other.h)
    return LottieSlotBox(minX, minY, maxX - minX, maxY - minY)
}

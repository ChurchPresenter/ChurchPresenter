package org.churchpresenter.app.churchpresenter.utils

import java.awt.Rectangle
import java.util.Locale

private const val MAX_SIMPLE_RATIO_SIDE = 64

/** Returns the aspect ratio of the presenter screen. */
fun presenterAspectRatio(): Float = aspectRatioOf(presenterScreenBounds())

internal fun aspectRatioOf(bounds: Rectangle): Float =
    bounds.width.toFloat() / bounds.height.toFloat()

/** Formats an aspect ratio as a common name (e.g. "16:9") or decimal fallback (e.g. "1.78:1"). */
fun formatAspectRatio(width: Int, height: Int): String {
    val gcd = gcd(width, height)
    val w = width / gcd
    val h = height / gcd
    // Accept simplified ratios where both sides are reasonable (≤64)
    return if (w <= MAX_SIMPLE_RATIO_SIDE && h <= MAX_SIMPLE_RATIO_SIDE) "$w:$h"
    else String.format(Locale.US, "%.2f:1", width.toFloat() / height.toFloat())
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

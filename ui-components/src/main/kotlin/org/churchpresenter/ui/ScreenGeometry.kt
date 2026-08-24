package org.churchpresenter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle
import java.util.Locale

/**
 * Which display the audience sees, and how to describe its shape.
 *
 * Five tabs and the live preview all ask these questions, which is why they live beside the widgets
 * rather than inside any one feature. Everything here is `java.awt` and Compose runtime only — the
 * assignment-aware variants stay in `:composeApp`, because those take a `ScreenAssignment` and
 * `:ui-components` must not gain a production dependency on `:settings`.
 *
 * **Every entry point survives a headless JVM.** The test suite runs with no display at all, so a
 * throw here would fail tests for a value they never look at; each of these answers 1080p instead.
 */

private const val SCREEN_POLL_INTERVAL_MS = 2000L
private const val MAX_SIMPLE_RATIO_SIDE = 64

/** The 1080p bounds assumed when there is no real display to ask. */
val HEADLESS_PRESENTER_BOUNDS: Rectangle = Rectangle(0, 0, 1920, 1080)

/** Empty on a headless JVM (CI, or a genuinely displayless deployment) instead of throwing. */
fun safeScreenDevices(): Array<GraphicsDevice> = try {
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
} catch (_: HeadlessException) {
    emptyArray()
}

/** Polls for screen devices every 2 seconds so hot-plugged displays trigger recomposition. */
@Composable
fun rememberScreenDevices(): Array<GraphicsDevice> {
    var devices by remember { mutableStateOf(safeScreenDevices()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SCREEN_POLL_INTERVAL_MS)
            val current = safeScreenDevices()
            if (current.size != devices.size) {
                devices = current
            }
        }
    }
    return devices
}

/** Returns the presenter screen bounds (first non-primary screen if available, else primary). */
fun presenterScreenBounds(): Rectangle {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
    return try {
        presenterBoundsOf(ge.screenDevices, ge.defaultScreenDevice)
    } catch (_: HeadlessException) {
        HEADLESS_PRESENTER_BOUNDS
    }
}

internal fun presenterBoundsOf(screens: Array<GraphicsDevice>, primary: GraphicsDevice): Rectangle =
    (screens.firstOrNull { it != primary } ?: primary).defaultConfiguration.bounds

/** Find a screen index by stored bounds. Returns null if no match. */
fun findScreenIndexByBounds(screens: Array<GraphicsDevice>, x: Int, y: Int, w: Int, h: Int): Int? {
    if (x == Int.MIN_VALUE) return null // bounds not set
    return screens.indexOfFirst { device ->
        val b = device.defaultConfiguration.bounds
        b.x == x && b.y == y && b.width == w && b.height == h
    }.takeIf { it >= 0 }
}

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

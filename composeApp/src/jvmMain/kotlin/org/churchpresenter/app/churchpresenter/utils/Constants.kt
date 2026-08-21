package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.songchords.ChordTransposer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.churchpresenter.settings.ScreenAssignment
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle
import java.util.Locale

private const val SCREEN_POLL_INTERVAL_MS = 2000L
private const val MAX_SIMPLE_RATIO_SIDE = 64

/** Empty on a headless JVM (CI, or a genuinely displayless deployment) instead of throwing. */
private fun safeScreenDevices(): Array<GraphicsDevice> = try {
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

/** The 1080p bounds [ScaledPresenterContent][org.churchpresenter.app.churchpresenter.composables.ScaledPresenterContent] assumes when no real display exists to ask (headless). */
private val HEADLESS_PRESENTER_BOUNDS = Rectangle(0, 0, 1920, 1080)

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

/**
 * Bounds of the display [assignment] targets, or 1080p when there is no display to ask (headless).
 *
 * Used for aspect-ratio comparisons against content that will be shown there — a scene built 16:9
 * on a 4:3 output is worth warning about before it goes live, and that check has to keep working on
 * a machine with no second screen attached.
 */
fun assignedDisplayBounds(assignment: ScreenAssignment): Rectangle {
    val screens = safeScreenDevices()
    if (screens.isEmpty()) return HEADLESS_PRESENTER_BOUNDS
    return try {
        assignedBoundsOf(screens, GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice, assignment)
    } catch (_: HeadlessException) {
        HEADLESS_PRESENTER_BOUNDS
    }
}

/**
 * Which of [screens] an assignment resolves to, most specific first: the screen whose top-left
 * corner matches the stored bounds, else the stored index, else any screen that is not [primary],
 * else [primary] itself.
 *
 * Stored bounds win over the index because a display's index shifts when another is plugged in or
 * removed, while its position on the desktop usually does not.
 */
internal fun assignedBoundsOf(
    screens: Array<GraphicsDevice>,
    primary: GraphicsDevice,
    assignment: ScreenAssignment,
): Rectangle {
    val matched = if (assignment.targetBoundsX != Int.MIN_VALUE) {
        screens.firstOrNull { device ->
            val bounds = device.defaultConfiguration.bounds
            bounds.x == assignment.targetBoundsX && bounds.y == assignment.targetBoundsY
        }
    } else null
    val device = matched
        ?: screens.getOrNull(assignment.targetDisplay)
        ?: screens.firstOrNull { it != primary }
        ?: primary
    return device.defaultConfiguration.bounds
}

/** Find a screen index by stored bounds. Returns null if no match. */
fun findScreenIndexByBounds(screens: Array<GraphicsDevice>, x: Int, y: Int, w: Int, h: Int): Int? {
    if (x == Int.MIN_VALUE) return null  // bounds not set
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

/**
 * Any line wrapped in [] or {} is a section header — except one holding nothing but a chord, which
 * an instrumental break writes as its own line. See [ChordTransposer.isSectionHeader].
 */
fun isHeaderLine(line: String): Boolean = ChordTransposer.isSectionHeader(line)

/** {} = chorus, [] = verse/other */
fun isChorusHeader(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("{") && t.endsWith("}")
}


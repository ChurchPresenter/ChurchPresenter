package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.PlayerMessage

/** What a screen's web page test says about it, in the words its card uses. */
enum class WebTestVerdict {
    /** Pages that move look smooth even with Smooth motion on. */
    SMOOTH,

    /** Fine at the normal 15 frames a second; Smooth motion is too much for it. */
    NORMAL_ONLY,

    /** Even 15 frames a second does not keep up: pages that move will stutter. */
    TOO_SLOW,
}

/**
 * The verdict on [this] test: too slow when under 80% of 15 frames a second reached the screen;
 * smooth when 90% of 30 did with the processor under 85% busy; normal motion only in between.
 * Null when the test did not run at 15. Decided here rather than on the screen, so the thresholds
 * can change without a player update.
 */
fun PlayerMessage.WebTestResult.verdict(): WebTestVerdict? {
    val normal = runs.firstOrNull { it.fps == NORMAL_FPS } ?: return null
    val smooth = runs.firstOrNull { it.fps == SMOOTH_FPS }
    return when {
        normal.shownFps < NORMAL_FPS * KEEPS_UP -> WebTestVerdict.TOO_SLOW
        smooth != null && smooth.shownFps >= SMOOTH_FPS * KEEPS_UP_SMOOTHLY &&
            (smooth.cpuPercent ?: 0.0) < BUSY_PERCENT -> WebTestVerdict.SMOOTH
        else -> WebTestVerdict.NORMAL_ONLY
    }
}

/** The hottest the screen's processor got in the test, when it is hot enough to slow itself down. */
fun PlayerMessage.WebTestResult.hotDegrees(): Int? =
    runs.mapNotNull { it.maxTempC }.maxOrNull()?.takeIf { it >= HOT_C }?.toInt()

private const val NORMAL_FPS = 15
private const val SMOOTH_FPS = LinkPlaylist.SMOOTH_FPS
private const val KEEPS_UP = 0.8
private const val KEEPS_UP_SMOOTHLY = 0.9
private const val BUSY_PERCENT = 85.0

/** Where small computers start slowing themselves down: a Raspberry Pi at 80 °C, others near it. */
private const val HOT_C = 80.0

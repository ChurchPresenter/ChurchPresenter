package org.churchpresenter.sharedui.utils

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The seconds an announcement's Speed sets (`AnnouncementsSettings.animationDuration`, in ms): how
 * long a sliding one takes to cross the screen from one edge to the other, and how long a still or
 * fading one stays up each loop. The same number means the same time for every animation.
 */
const val ANNOUNCEMENT_MIN_SECONDS_MS = 1_000
const val ANNOUNCEMENT_MAX_SECONDS_MS = 30_000

/** The Speed's steps: half a second. */
const val ANNOUNCEMENT_SECONDS_STEP_MS = 500

/** How long an announcement fades in and out, on the output and in the tab's preview alike. */
const val ANNOUNCEMENT_FADE_MS = 500

/** A stored Speed, in the range the slider offers -- a value saved before it had one included. */
fun announcementSecondsMs(stored: Int): Int = stored.coerceIn(ANNOUNCEMENT_MIN_SECONDS_MS, ANNOUNCEMENT_MAX_SECONDS_MS)

/**
 * How far either side of its resting place a sliding announcement starts and ends: just off one
 * edge to just off the other, for content [contentPx] long centred in a container [containerPx] long.
 */
fun slideTravelPx(containerPx: Float, contentPx: Float): Float = (containerPx + contentPx.coerceAtLeast(0f)) / 2f

/**
 * How long one pass takes, so that the content's leading edge crosses the container in [crossingMs]:
 * the whole trip is the container and the content's own length, at the speed the crossing sets.
 */
fun slidePassMs(crossingMs: Int, containerPx: Float, contentPx: Float): Int =
    if (containerPx <= 0f) {
        crossingMs
    } else {
        (crossingMs * (containerPx + contentPx.coerceAtLeast(0f)) / containerPx).roundToInt().coerceAtLeast(1)
    }

/**
 * The Speed slider's position, 0..1, for [secondsMs]. Spaced by ratio rather than by seconds, so each
 * bit of travel changes how fast the text moves by about the same amount -- going from 1 s to 2 s
 * halves the speed, going from 29 s to 30 s barely changes it.
 */
fun announcementSliderPosition(secondsMs: Int): Float {
    val clamped = announcementSecondsMs(secondsMs).toDouble()
    return (ln(clamped / ANNOUNCEMENT_MIN_SECONDS_MS) / ln(RATIO)).toFloat().coerceIn(0f, 1f)
}

/** The seconds, in ms and to the half second, at the Speed slider's [position] -- see [announcementSliderPosition]. */
fun announcementSecondsAt(position: Float): Int {
    val raw = ANNOUNCEMENT_MIN_SECONDS_MS * RATIO.pow(position.coerceIn(0f, 1f).toDouble())
    val snapped = (raw / ANNOUNCEMENT_SECONDS_STEP_MS).roundToInt() * ANNOUNCEMENT_SECONDS_STEP_MS
    return announcementSecondsMs(snapped)
}

private const val RATIO = ANNOUNCEMENT_MAX_SECONDS_MS.toDouble() / ANNOUNCEMENT_MIN_SECONDS_MS

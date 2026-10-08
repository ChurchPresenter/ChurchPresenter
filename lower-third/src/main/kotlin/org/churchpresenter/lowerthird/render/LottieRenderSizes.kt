package org.churchpresenter.lowerthird.render

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.settings.AtemSettings
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MILLIS_PER_SECOND = 1000.0
private const val DEFAULT_CANVAS_WIDTH = 1920
private const val DEFAULT_CANVAS_HEIGHT = 1080
private const val MAX_CANVAS_DIMENSION = 1920

/**
 * The longest side an output-sized variant is rendered at. An output larger than this (8K) draws the
 * 4K frames scaled up rather than holding frames four times their size.
 */
private const val MAX_OUTPUT_DIMENSION = 3840

/** Same-aspect tolerance for sharing one cache entry between desktop and ATEM sizes. */
private const val ASPECT_TOLERANCE = 0.01

/**
 * What a lottie's JSON says about it — its canvas and its length — and the size and frame count
 * each consumer's cache entry is rendered at. [LottieRenderCache] answers these by delegation.
 */
interface LottieRenderPolicy {
    /** Canvas size (w × h) straight from the lottie JSON. */
    fun lottieCanvasSize(lottieJson: String): Pair<Int, Int>?

    /** Clip duration straight from the lottie JSON: (op - ip) / fr seconds. */
    fun lottieDurationMs(lottieJson: String): Long?

    /** Frame count for a clip of this lottie at the given fps, or null if the JSON has no timing. */
    fun clipFrameCount(lottieJson: String, fps: Double): Int?

    /** (w, h) scaled down proportionally so neither side exceeds 1920. */
    fun clampCanvasSize(w: Int, h: Int): Pair<Int, Int>

    /** Size an ATEM upload variant is stored at. */
    fun atemRenderSize(lottieJson: String, atem: AtemSettings): Pair<Int, Int>

    /** Size a desktop playback variant is stored at. */
    fun desktopRenderSize(lottieJson: String, atem: AtemSettings?): Pair<Int, Int>

    /** The cache variant an ATEM still or clip upload should prepare and read. */
    fun atemVariant(
        lottieJson: String,
        atem: AtemSettings,
        clip: Boolean,
        fps: Double = atem.clipFps,
        fallbackFrameCount: Int = 1,
    ): LottieRenderCache.Variant

    /** The cache variant desktop playback streams from, or null if the JSON has no timing. */
    fun desktopVariant(lottieJson: String, atem: AtemSettings?): LottieRenderCache.Variant?

    /**
     * The cache variant an output [outputWidth]×[outputHeight] pixels big streams from: [desktop] at
     * the size `ContentScale.Fit` draws it on that output, so the output draws its frames one to one.
     * Null when [desktop] is already at least that size, so the output scales [desktop] down as it
     * always has. Capped at 3840 on the longest side.
     */
    fun outputVariant(
        desktop: LottieRenderCache.Variant,
        outputWidth: Int,
        outputHeight: Int,
    ): LottieRenderCache.Variant?
}

internal object LottieRenderSizes : LottieRenderPolicy {
    /** Canvas size (w × h) straight from the lottie JSON. */
    override fun lottieCanvasSize(lottieJson: String): Pair<Int, Int>? = try {
        val obj = Json.parseToJsonElement(lottieJson).jsonObject
        val w = obj["w"]?.jsonPrimitive?.double?.toInt() ?: return null
        val h = obj["h"]?.jsonPrimitive?.double?.toInt() ?: return null
        if (w > 0 && h > 0) w to h else null
    } catch (_: Exception) {
        null
    }

    /** Clip duration straight from the lottie JSON: (op - ip) / fr seconds. */
    override fun lottieDurationMs(lottieJson: String): Long? = try {
        val obj = Json.parseToJsonElement(lottieJson).jsonObject
        val fr = obj["fr"]?.jsonPrimitive?.double ?: return null
        val ip = obj["ip"]?.jsonPrimitive?.double ?: 0.0
        val op = obj["op"]?.jsonPrimitive?.double ?: return null
        if (fr <= 0.0 || op <= ip) null
        else (((op - ip) / fr) * MILLIS_PER_SECOND).toLong().coerceAtLeast(1L)
    } catch (_: Exception) {
        null
    }

    /** Frame count for a clip of this lottie at the given fps, or null if the JSON has no timing. */
    override fun clipFrameCount(lottieJson: String, fps: Double): Int? =
        lottieDurationMs(lottieJson)?.let { ((it / MILLIS_PER_SECOND) * fps).toInt().coerceAtLeast(1) }

    /**
     * Scales (w, h) down proportionally so neither side exceeds 1920 — the desktop variant every
     * output can draw stays small on disk and quick to decode; an output larger than it gets a
     * variant of its own size ([outputVariant]).
     */
    override fun clampCanvasSize(w: Int, h: Int): Pair<Int, Int> = clampTo(w, h, MAX_CANVAS_DIMENSION)

    private fun clampTo(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
        val longestSide = maxOf(w, h)
        if (longestSide <= maxSide) return w to h
        val scale = maxSide.toDouble() / longestSide
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    private fun clampedCanvas(lottieJson: String): Pair<Int, Int> {
        val (w, h) = lottieCanvasSize(lottieJson) ?: (DEFAULT_CANVAS_WIDTH to DEFAULT_CANVAS_HEIGHT)
        return clampCanvasSize(w, h)
    }

    private fun aspectsMatch(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean {
        val ra = a.first.toDouble() / a.second
        val rb = b.first.toDouble() / b.second
        return abs(ra - rb) <= rb * ASPECT_TOLERANCE
    }

    /**
     * Size an ATEM upload variant is stored at. Same aspect as the switcher raster → the
     * per-axis max of lottie canvas and raster, so one entry serves both ATEM and desktop
     * playback (the renderer composites with ContentScale.Fit, and a same-aspect downscale
     * to the raster at upload time is geometry-preserving). Different aspect → exactly the
     * raster, because a non-uniform scale would distort; the lottie letterboxes into the
     * raster at render time instead. ATEM media must match the switcher's video mode —
     * uploading any other size produces a stride/chroma-shifted (purplish, half) image.
     */
    override fun atemRenderSize(lottieJson: String, atem: AtemSettings): Pair<Int, Int> {
        val raster = atem.renderWidth to atem.renderHeight
        val canvas = clampedCanvas(lottieJson)
        return if (aspectsMatch(canvas, raster))
            maxOf(canvas.first, raster.first) to maxOf(canvas.second, raster.second)
        else raster
    }

    /**
     * Size a desktop playback variant is stored at: the (clamped) lottie canvas, upgraded to
     * the shared ATEM size when an ATEM is configured with a same-aspect raster — that makes
     * the desktop and ATEM variants one cache entry rendered once.
     */
    override fun desktopRenderSize(lottieJson: String, atem: AtemSettings?): Pair<Int, Int> {
        val canvas = clampedCanvas(lottieJson)
        if (atem == null || atem.host.isBlank()) return canvas
        val raster = atem.renderWidth to atem.renderHeight
        return if (aspectsMatch(canvas, raster))
            maxOf(canvas.first, raster.first) to maxOf(canvas.second, raster.second)
        else canvas
    }

    /** The cache variant an ATEM still or clip upload should prepare and read. */
    override fun atemVariant(
        lottieJson: String,
        atem: AtemSettings,
        clip: Boolean,
        fps: Double,
        fallbackFrameCount: Int,
    ): LottieRenderCache.Variant {
        val (w, h) = atemRenderSize(lottieJson, atem)
        if (!clip) return LottieRenderCache.Variant(clip = false, width = w, height = h)
        val frames = clipFrameCount(lottieJson, fps) ?: fallbackFrameCount
        return LottieRenderCache.Variant(true, w, h, fps, frames)
    }

    /** The cache variant desktop playback streams from, or null if the JSON has no timing. */
    override fun desktopVariant(lottieJson: String, atem: AtemSettings?): LottieRenderCache.Variant? {
        val (w, h) = desktopRenderSize(lottieJson, atem)
        val frames = clipFrameCount(lottieJson, LottieRenderCache.PLAYBACK_FPS.toDouble()) ?: return null
        return LottieRenderCache.Variant(true, w, h, LottieRenderCache.PLAYBACK_FPS.toDouble(), frames)
    }

    /**
     * The limiting side comes out at exactly the output's size and the other at most the output's,
     * so `ContentScale.Fit` draws the frame at a scale of exactly one.
     */
    override fun outputVariant(
        desktop: LottieRenderCache.Variant,
        outputWidth: Int,
        outputHeight: Int,
    ): LottieRenderCache.Variant? {
        if (outputWidth <= 0 || outputHeight <= 0) return null
        val scale = minOf(outputWidth.toDouble() / desktop.width, outputHeight.toDouble() / desktop.height)
        if (scale <= 1.0) return null
        val (w, h) = clampTo(
            (desktop.width * scale).roundToInt().coerceAtMost(outputWidth),
            (desktop.height * scale).roundToInt().coerceAtMost(outputHeight),
            MAX_OUTPUT_DIMENSION,
        )
        if (w <= desktop.width && h <= desktop.height) return null
        return desktop.copy(width = w, height = h)
    }
}

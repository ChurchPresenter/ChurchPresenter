package org.churchpresenter.lottiegen.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.model.StyleCatalog
import org.churchpresenter.lottiegen.render.StillFrame
import java.awt.image.BufferedImage

/** How long the config has to stay still before every style is drawn again. */
internal const val THUMBNAIL_DEBOUNCE_MS = 400L

/** The thumbnails are drawn at this fraction of the canvas: 1920x1080 becomes 480x270. */
private const val THUMBNAIL_SCALE = 4

/** Transparent margin kept around what was drawn, in thumbnail pixels. */
private const val THUMBNAIL_PADDING_PX = 6

/**
 * The Style menu's picture of every style, drawn with the lower third as it is being edited -- its
 * name, title, colours, fonts and logo -- so what is picked from the menu is what comes out.
 *
 * The same approach as the Bible band's `ensureStyleThumbnails`: keyed on the config, built on
 * [Dispatchers.Default], rebuilt only when the key changes. Unlike the band it keeps the words, and
 * it is a still cropped to the drawing rather than a live painter, because a lower third sits in a
 * corner of the canvas and a whole-canvas picture of it would be mostly empty.
 *
 * [render] turns one style's Lottie into a picture; it is the one step that needs a real Compose
 * scene, so it is a parameter and a test passes a stand-in. Pictures arrive one style at a time,
 * so the menu fills in while it is being built rather than all at once.
 *
 * [diagnostics] records what each build did -- see [ThumbnailDiagnostics]; it is read, never acted on.
 */
class StyleThumbnails(
    private val scope: CoroutineScope,
    private val diagnostics: ThumbnailDiagnostics = ThumbnailDiagnostics(),
    private val render: suspend (lottieJson: String, config: LottieGenConfig) -> ImageBitmap? = { lottie, config ->
        renderThumbnail(lottie, config, diagnostics)
    },
    private val styleIds: () -> List<String> = { StyleCatalog.entries.map { it.id } },
    private val debounceMs: Long = THUMBNAIL_DEBOUNCE_MS,
) {
    /** The picture of each style by its id, for those drawn so far under the current config. */
    var thumbnails by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
        private set

    private var key: LottieGenConfig? = null
    private var job: Job? = null
    private val json = Json

    /** What the builds have done so far, as text -- for a picture that should be there and is not. */
    fun diagnostics(): String = diagnostics.describe()

    /** Draws every style for [config], unless that is what is already drawn or being drawn. */
    fun request(config: LottieGenConfig) {
        val next = thumbnailKey(config)
        if (next == key) {
            diagnostics.requested(config.style, job = null)
            return
        }
        key = next
        job?.cancel()
        val build = scope.launch {
            delay(debounceMs)
            diagnostics.buildStarted()
            val built = LinkedHashMap<String, ImageBitmap>()
            for (id in styleIds()) {
                val picture = drawOne(next.copy(style = id)) ?: continue
                if (key != next) {
                    diagnostics.buildSuperseded()
                    return@launch
                }
                built[id] = picture
                publish(thumbnails + (id to picture))
            }
            // A style that failed to draw this time must not keep the last config's picture.
            if (key == next) {
                publish(built)
                diagnostics.buildFinished()
            }
        }
        job = build
        diagnostics.requested(config.style, build)
    }

    private fun publish(pictures: Map<String, ImageBitmap>) {
        thumbnails = pictures
        diagnostics.published(pictures.keys)
    }

    // A style that cannot be drawn -- a composition that never loads -- simply has no picture: the
    // menu still lists it by name, and picking it shows the real error in the preview.
    @Suppress("SwallowedException")
    private suspend fun drawOne(config: LottieGenConfig): ImageBitmap? {
        val picture = try {
            val lottie = withContext(Dispatchers.Default) {
                json.encodeToString(JsonObject.serializer(), LottieGenerator.generate(config))
            }
            renderWithRetry(lottie, config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            diagnostics.recordIfAbsent(config.style, ThumbnailDiagnostics.Outcome.ILLEGAL_STATE, e.message)
            null
        } catch (e: IllegalArgumentException) {
            diagnostics.record(config.style, ThumbnailDiagnostics.Outcome.ILLEGAL_ARGUMENT, e.message)
            null
        }
        if (picture == null) diagnostics.recordIfAbsent(config.style, ThumbnailDiagnostics.Outcome.RENDER_NULL)
        return picture
    }

    // The first still of a session parses its Lottie cold, and on a slow or busy machine that can
    // outlast the renderer's load deadline; the second attempt is warm. Without this the style
    // being edited could stay blank until the config next changed.
    @Suppress("SwallowedException")
    private suspend fun renderWithRetry(lottie: String, config: LottieGenConfig): ImageBitmap? = try {
        render(lottie, config)?.also { diagnostics.record(config.style, ThumbnailDiagnostics.Outcome.DRAWN) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        retry(lottie, config)
    }

    private suspend fun retry(lottie: String, config: LottieGenConfig): ImageBitmap? = try {
        render(lottie, config)?.also { diagnostics.record(config.style, ThumbnailDiagnostics.Outcome.DRAWN_ON_RETRY) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        diagnostics.record(config.style, ThumbnailDiagnostics.Outcome.ILLEGAL_STATE_TWICE, e.message)
        throw e
    }
}

/**
 * What the pictures depend on: everything but the style, which each one sets for itself, and the
 * durations, which move when the hold is reached but not what it looks like -- see
 * [thumbnailProgress], which draws every style at the middle of a normalised hold.
 */
internal fun thumbnailKey(config: LottieGenConfig): LottieGenConfig =
    config.copy(style = "", animDuration = KEY_ANIM_SECONDS, holdDuration = KEY_HOLD_SECONDS)

private val KEY_DEFAULTS = LottieGenConfig()
private val KEY_ANIM_SECONDS = KEY_DEFAULTS.animDuration
private val KEY_HOLD_SECONDS = KEY_DEFAULTS.holdDuration

/**
 * The middle of the hold, as a fraction of the whole: in, hold, then an exit as long as the
 * entrance -- the timeline `LottieGenerator` builds.
 */
internal fun thumbnailProgress(config: LottieGenConfig): Float {
    val total = 2 * config.animDuration + config.holdDuration
    return if (total <= 0f) 0f else (config.animDuration + config.holdDuration / 2) / total
}

/** The drawn part of [pixels] -- [width] x [height] -- with a small transparent margin, or null when blank. */
internal fun croppedThumbnail(pixels: IntArray, width: Int, height: Int): Pair<IntArray, StillFrame.CropRegion>? {
    val bounds = StillFrame.frameBounds(pixels, width, height) ?: return null
    val x0 = (bounds.minX - THUMBNAIL_PADDING_PX).coerceAtLeast(0)
    val y0 = (bounds.minY - THUMBNAIL_PADDING_PX).coerceAtLeast(0)
    val x1 = (bounds.maxX + THUMBNAIL_PADDING_PX).coerceAtMost(width - 1)
    val y1 = (bounds.maxY + THUMBNAIL_PADDING_PX).coerceAtMost(height - 1)
    val region = StillFrame.CropRegion(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    return StillFrame.cropRegion(pixels, width, height, region, fill = 0) to region
}

/** One style's still at quarter scale, cropped to what it draws; a blank still is noted in [diagnostics]. */
private suspend fun renderThumbnail(
    lottieJson: String,
    config: LottieGenConfig,
    diagnostics: ThumbnailDiagnostics,
): ImageBitmap? {
    val width = (config.canvasW / THUMBNAIL_SCALE).coerceAtLeast(1)
    val height = (config.canvasH / THUMBNAIL_SCALE).coerceAtLeast(1)
    val pixels = StillFrame.render(lottieJson, width, height, thumbnailProgress(config))
    val (cropped, region) = croppedThumbnail(pixels, width, height) ?: run {
        diagnostics.record(config.style, ThumbnailDiagnostics.Outcome.BLANK)
        return null
    }
    val image = BufferedImage(region.width, region.height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, region.width, region.height, cropped, 0, region.width)
    return image.toComposeImageBitmap()
}

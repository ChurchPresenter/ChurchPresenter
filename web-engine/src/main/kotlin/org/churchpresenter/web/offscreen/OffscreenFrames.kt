package org.churchpresenter.web.offscreen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.cef.browser.HeadlessSink
import org.churchpresenter.diagnostics.Log
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import java.awt.Rectangle
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Turns Chromium's paints into [frames].
 *
 * The view is written into a [FrameRing] that Skia wraps without copying, so a frame costs one copy
 * out of Chromium's buffer (which is only valid inside the callback) and no more. Every window draws
 * the same image, so no two can disagree about what is on the page. Chromium waits for each paint
 * callback to return before it goes on, so the callback does only the copy, split across
 * [COPY_THREADS] threads when the whole frame changed.
 *
 * A worker thread also keeps a [FramePyramid] of the view. A small window draws the smallest level
 * at least its own size, so it never shrinks a picture by more than 2:1 (past that a bilinear sample
 * skips source pixels and small text breaks up) and never uploads a 4K texture to draw it at 640 px.
 * A frame is published only with its levels, so every window changes at the same moment: on Linux
 * skiko draws the windows that changed together and waits for one vertical blank, and a preview a few
 * milliseconds behind the output cost a second wait every frame. The worker takes the newest frame
 * whenever it is free; a paint that arrives while it is busy replaces the one waiting, and is not
 * drawn.
 *
 * An image is closed [RETIRED_KEPT] frames after it stops being current. The ring has room for
 * those, the current frame, the one with the worker, the one waiting and the one being written.
 *
 * [rgba] keeps the frames as RGBA rather than Chromium's BGRA, swapped during the copy: on Linux,
 * NVIDIA's driver converts BGRA on the CPU while uploading it. [onPaintFailed] hears what a paint
 * threw, on the paint thread.
 */
internal class OffscreenFrames(
    private val dipWidth: () -> Int,
    private val rgba: Boolean,
    private val stats: OffscreenStats,
    private val onPaintFailed: (Throwable) -> Unit,
) : HeadlessSink {
    private val _frames = MutableStateFlow<OffscreenFrame?>(null)
    val frames: StateFlow<OffscreenFrame?> = _frames

    private val _cursor = MutableStateFlow(0)
    val cursor: StateFlow<Int> = _cursor

    private val colorType = if (rgba) ColorType.RGBA_8888 else ColorType.BGRA_8888
    private var ring: FrameRing? = null
    private val oldRings = ArrayDeque<Pair<Long, FrameRing>>()
    private var popupMirror: PopupMirror? = null
    private var popupVisible = false
    private var popupRect: Rectangle? = null
    private var popupImage: Image? = null

    /** The level worker's pyramid, and replaced ones kept until no image over them is left. */
    private var pyramid: FramePyramid? = null
    private val oldPyramids = ArrayDeque<Pair<Long, FramePyramid>>()
    private var seq = 0L
    private var viewSeq = 0L
    private var levelSeq = 0L
    private val retired = ArrayDeque<List<Image>>()

    /** The newest levels, which every published frame carries. */
    private var latestLevels: List<Image> = emptyList()
    private val retiredLevels = ArrayDeque<List<Image>>()

    /** Whether the level worker is busy with a frame. */
    private var scaling = false

    /** A paint written but not yet published, waiting for the worker, and when it arrived. */
    private var pending: FrameRing.Written? = null
    private var pendingAt = 0L

    /** The bounding box, in view pixels, of everything painted since the level worker last started. */
    private var levelDirty: Rectangle? = null
    private var closed = false
    private val levelWorker = Executors.newSingleThreadExecutor(daemon("web-offscreen-levels"))
    private val copyPool = Executors.newFixedThreadPool(COPY_THREADS - 1, daemon("web-offscreen-copy"))

    /** The level worker's own helpers, so its work never queues ahead of a paint's copy. */
    private val levelPool = Executors.newFixedThreadPool(COPY_THREADS - 1, daemon("web-offscreen-level-band"))

    @Synchronized
    override fun onPaint(popup: Boolean, dirtyRects: Array<Rectangle>, buffer: ByteBuffer, width: Int, height: Int) {
        if (closed) return
        val start = System.nanoTime()
        if (popup) {
            val mirror = popupMirror?.takeIf { it.width == width && it.height == height }
                ?: PopupMirror(width, height).also { popupMirror = it }
            popupImage?.let { retire(listOf(it)) }
            popupImage = mirror.snapshot(buffer)
            emit(_frames.value?.view ?: return, start, newView = false)
            return
        }
        val target = ring?.takeIf { it.width == width && it.height == height }
            ?: FrameRing(width, height, swizzle = rgba, colorType = colorType).also { fresh ->
                Log.info(LOG_TAG, "painting ${width}x$height for a ${dipWidth()} DIP wide page")
                // Images over the old ring's buffers are still being retired; keep it alive until they are.
                ring?.let { old -> oldRings.addLast(seq to old) }
                ring = fresh
            }
        val written = target.write(dirtyRects, buffer, copyPool)
        stats.paint(System.nanoTime() - start)
        if (width / 2 < SMALLEST_LEVEL) {
            emit(written.image, start, newView = true)
            return
        }
        dirtyRects.forEach { r -> levelDirty = levelDirty?.union(r) ?: Rectangle(r) }
        pending?.image?.close()
        pending = written
        pendingAt = start
        if (!scaling) startScaling()
    }

    /** Hands the waiting paint, if any, to the level worker. Called holding the lock. */
    private fun startScaling() {
        val next = pending ?: return
        val paintedAt = pendingAt
        val dirty = levelDirty
        pending = null
        levelDirty = null
        scaling = true
        levelWorker.execute { scaleLevels(next, paintedAt, dirty) }
    }

    /** On the level worker: brings the levels up to date over [dirty] of [view], then publishes the two together. */
    private fun scaleLevels(view: FrameRing.Written, paintedAt: Long, dirty: Rectangle?) {
        val width = view.image.width
        val height = view.image.height
        val fresh = pyramid?.takeIf { it.viewWidth == width && it.viewHeight == height }
        val target = fresh ?: FramePyramid(width, height, colorType).also { made ->
            // Images over the old levels' buffers are still being retired; keep them alive until they are.
            pyramid?.let { old -> oldPyramids.addLast(levelSeq to old) }
            pyramid = made
        }
        while (oldPyramids.isNotEmpty() && levelSeq - oldPyramids.first().first > RING_SIZE) oldPyramids.removeFirst()
        val levels = target.update(view.buffer, if (fresh == null) null else dirty, levelPool)
        synchronized(this) {
            scaling = false
            if (closed) {
                (levels + view.image).forEach(Image::close)
                return
            }
            retiredLevels.addLast(latestLevels)
            while (retiredLevels.size > RETIRED_KEPT) retiredLevels.removeFirst().forEach(Image::close)
            latestLevels = levels
            levelSeq++
            emit(view.image, paintedAt, newView = true)
            startScaling()
        }
    }

    @Synchronized
    override fun onPopupShow(show: Boolean) {
        popupVisible = show
        if (!show) {
            popupImage?.let { retire(listOf(it)) }
            popupImage = null
            popupRect = null
        }
        emit(_frames.value?.view ?: return, System.nanoTime(), newView = false)
    }

    @Synchronized
    override fun onPopupSize(rect: Rectangle) {
        popupRect = Rectangle(rect)
    }

    override fun onCursorChange(cursorType: Int) {
        _cursor.value = cursorType
    }

    override fun onPaintFailed(failure: Throwable) = onPaintFailed.invoke(failure)

    /**
     * Publishes [view] with the newest levels and the popup. With [newView], the frame it replaces
     * retires its view; without, it is the same view again under a changed popup.
     */
    private fun emit(view: Image, paintedAt: Long, newView: Boolean) {
        if (newView) _frames.value?.let { old -> retire(listOf(old.view)) }
        seq++
        _frames.value = OffscreenFrame(
            view = view,
            levels = latestLevels,
            popup = popupImage?.let { image -> popupRect?.let { rect -> OffscreenPopup(image, inPixels(rect, view)) } }
                ?.takeIf { popupVisible },
            paintedAt = paintedAt,
            viewSeq = if (newView) ++viewSeq else viewSeq,
            levelSeq = levelSeq,
        )
    }

    /** [rect], in DIPs as Chromium places a popup, in [view]'s pixels. */
    private fun inPixels(rect: Rectangle, view: Image): Rect {
        val scale = view.width.toFloat() / dipWidth()
        return Rect.makeXYWH(rect.x * scale, rect.y * scale, rect.width * scale, rect.height * scale)
    }

    private fun retire(images: List<Image>) {
        retired.addLast(images)
        while (retired.size > RETIRED_KEPT) retired.removeFirst().forEach(Image::close)
        while (oldRings.isNotEmpty() && seq - oldRings.first().first > RING_SIZE) oldRings.removeFirst()
    }

    /**
     * Stops taking paints and stops the threads; returns what frees the images, for the caller to run
     * once no window can still be drawing the last frame.
     */
    @Synchronized
    fun close(): () -> Unit {
        closed = true
        val images = buildList {
            _frames.value?.view?.let(::add)
            retired.forEach(::addAll)
            retiredLevels.forEach(::addAll)
            addAll(latestLevels)
            pending?.image?.let(::add)
            popupImage?.let(::add)
        }.distinct()
        _frames.value = null
        levelWorker.shutdown()
        copyPool.shutdown()
        levelPool.shutdown()
        retired.clear()
        retiredLevels.clear()
        latestLevels = emptyList()
        pending = null
        popupImage = null
        return { images.forEach(Image::close) }
    }
}

/** The popup (a `<select>` list) is small and repainted whole: a plain copy each time. */
private class PopupMirror(val width: Int, val height: Int) {
    private val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
    private val pixels = ByteArray(width * height * BYTES_PER_PIXEL)

    fun snapshot(source: ByteBuffer): Image {
        source.get(0, pixels, 0, pixels.size)
        return Image.makeRaster(info, pixels, width * BYTES_PER_PIXEL)
    }
}

private const val LOG_TAG = "WebOffscreen"

private fun daemon(name: String) = ThreadFactory { r -> Thread(r, name).apply { isDaemon = true } }

package org.churchpresenter.web.offscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.onSizeChanged
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * One output's picture of [page]: the latest frame, letterboxed, with the open popup over it. Every
 * surface that shows the page is one of these over the same frames, so they all change together.
 *
 * With [drivesSize] (an output window) the page is told this surface's size in pixels, and renders
 * at the largest such size, so a 4K projector gets a 4K page. [small] picks the frame's scaled-down
 * copies for a small surface; an output is meant to be drawn at its own size and passes false.
 */
@Composable
fun OffscreenWebView(
    page: OffscreenPage,
    modifier: Modifier = Modifier,
    drivesSize: Boolean = false,
    small: Boolean = false,
) {
    val surface = remember { Any() }
    DisposableEffect(page, surface) {
        onDispose {
            page.releaseSize(surface)
            page.stats.forget(surface)
        }
    }
    val sized = if (drivesSize) modifier.onSizeChanged { page.requestSize(surface, it) } else modifier
    PageCanvas(page, sized, small) { latencyNs, gap -> if (!small) page.stats.draw(surface, latencyNs, gap) }
}

/**
 * [page] as the operator controls it: [OffscreenWebView]'s small picture, with the mouse, wheel and
 * touchpad sent to the page at the matching point, the pointer shaped as the page asks, and the keys
 * sent to the page while this has focus. A press here gives it focus; focus anywhere else takes the
 * keys back. With [drivesSize] the page renders at this box's size, as for a page of its own.
 */
@Composable
fun OffscreenWebControl(page: OffscreenPage, modifier: Modifier = Modifier, drivesSize: Boolean = false) {
    val surface = remember { Any() }
    DisposableEffect(page, surface) { onDispose { page.releaseSize(surface) } }
    val sized = if (drivesSize) modifier.onSizeChanged { page.requestSize(surface, it) } else modifier
    val focus = remember { FocusRequester() }
    val focused = remember { AtomicBoolean(false) }
    val window = remember { AtomicReference<Window?>(null) }
    DisposableEffect(page) {
        val keys = PageKeys(BrowserKeys(page.browser), { e -> inWindow(e, window.get()) }, focused::get)
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        manager.addKeyEventDispatcher(keys)
        onDispose {
            manager.removeKeyEventDispatcher(keys)
            if (focused.get()) page.browser.setFocus(false)
        }
    }
    val cursor by page.cursor.collectAsState()
    PageCanvas(
        page,
        sized
            .focusRequester(focus)
            .onFocusChanged {
                if (it.isFocused) window.set(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow)
                focused.set(it.isFocused)
                page.browser.setFocus(it.isFocused)
            }
            .focusable()
            .forwardPointer(page.browser, cursor, onPress = { focus.requestFocus() }),
        small = true,
    ) { _, _ -> }
}

/** Draws [page]'s frames into [modifier]'s box; [onDrawn] hears each draw's latency and frames skipped. */
@Composable
private fun PageCanvas(page: OffscreenPage, modifier: Modifier, small: Boolean, onDrawn: (Long, Long) -> Unit) {
    val frames = page.frames.collectAsState()
    val shown by remember(small) { derivedStateOf(Shown.Policy) { frames.value?.let { Shown.of(it, small) } } }
    val lastSeq = remember { longArrayOf(-1) }
    val paint = remember { Paint() }
    Canvas(modifier) {
        val current = shown ?: return@Canvas
        drawFrame(current, small, paint)
        val gap = if (lastSeq[0] < 0) 0 else (current.pictureSeq - lastSeq[0] - 1).coerceAtLeast(0)
        lastSeq[0] = current.pictureSeq
        onDrawn(System.nanoTime() - current.frame.paintedAt, gap)
    }
}

private fun DrawScope.drawFrame(current: Shown, small: Boolean, paint: Paint) {
    val f = current.frame
    val dst = fitRect(size, f.view.width, f.view.height)
    val picture = pick(current.pictures, dst.width)
    // Mipmaps are rebuilt on the CPU for every new picture, so only a small surface that is below
    // even the smallest level pays for them.
    val shrinking = small && dst.width < picture.width * MIPMAP_BELOW
    val sampling = FilterMipmap(FilterMode.LINEAR, if (shrinking) MipmapMode.LINEAR else MipmapMode.NONE)
    drawIntoCanvas { c ->
        val canvas = c.skiaCanvas
        // Not strict: Skia turns mipmapping off for a strict source rect, and the source is the whole
        // picture anyway, so there is nothing outside it to bleed in.
        canvas.drawImageRect(picture, picture.bounds(), dst, sampling, paint, false)
        val popup = f.popup
        if (popup != null) {
            // The popup's place is in the view's pixels; the picture may be a smaller level.
            val viewToDst = dst.width / f.view.width
            val rect = popup.rect
            val target = Rect.makeXYWH(
                dst.left + rect.left * viewToDst,
                dst.top + rect.top * viewToDst,
                rect.width * viewToDst,
                rect.height * viewToDst,
            )
            canvas.drawImageRect(popup.image, popup.image.bounds(), target, sampling, paint, false)
        }
    }
}

/**
 * What one surface draws from a frame: the full view, or for a small one also the frame's levels,
 * picked by its size at draw time, and the popup. Two are equivalent when they would draw the same
 * pixels, so a surface is not redrawn when only another surface's pictures changed.
 */
internal class Shown(val frame: OffscreenFrame, val pictures: List<Image>, val pictureSeq: Long) {
    companion object {
        fun of(frame: OffscreenFrame, small: Boolean): Shown =
            if (small && frame.levels.isNotEmpty()) Shown(frame, listOf(frame.view) + frame.levels, frame.levelSeq)
            else Shown(frame, listOf(frame.view), frame.viewSeq)

        val Policy = object : SnapshotMutationPolicy<Shown?> {
            override fun equivalent(a: Shown?, b: Shown?): Boolean =
                if (a == null || b == null) a === b else drawsTheSame(a, b)
        }

        /** Whether [a] and [b] draw the same picture and the same popup in the same place. */
        fun drawsTheSame(a: Shown, b: Shown): Boolean {
            val popupA = a.frame.popup
            val popupB = b.frame.popup
            val samePopup = if (popupA == null || popupB == null) popupA === popupB
            else popupA.image === popupB.image && popupA.rect == popupB.rect
            return a.pictures.last() === b.pictures.last() && samePopup
        }
    }
}

/** The whole of this image, as a source rect. */
private fun Image.bounds(): Rect = Rect.makeWH(width.toFloat(), height.toFloat())

/** Where the page's picture sits inside a box of [box]: centred, letterboxed to the frame's aspect. */
internal fun fitRect(box: Size, frameW: Int, frameH: Int): Rect {
    val scale = minOf(box.width / frameW, box.height / frameH)
    val w = frameW * scale
    val h = frameH * scale
    return Rect.makeXYWH((box.width - w) / 2, (box.height - h) / 2, w, h)
}

/** The smallest of [pictures] (largest first) at least [width] wide; the largest when none is. */
internal fun pick(pictures: List<Image>, width: Float): Image =
    pictures.lastOrNull { it.width >= width } ?: pictures.first()

/**
 * A small surface drawn smaller than this share of even the smallest level samples it from mipmaps:
 * past 2:1 a bilinear sample skips source pixels.
 */
private const val MIPMAP_BELOW = 0.5f

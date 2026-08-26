package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineScope
import org.churchpresenter.companionserver.BrowserSourceFrame
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.stt.STTManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private const val ALPHA_SHIFT = 24
private const val OPAQUE_ALPHA = 0xFF
private const val MIN_CROSSFADE_MS = 100

/**
 * Renders a Browser Source output's live content off-screen (no window, no JCEF — same
 * [ImageComposeScene] technique as [LowerThirdOffscreenRenderer]) and encodes changed frames
 * as PNG, so a remote OBS/vMix Browser Source gets a pixel-identical stream of the same
 * presenter composables used everywhere else in the app — no reimplementation drift.
 *
 * Supported content types: Bible, Songs/Lyrics, Announcements, Pictures, Presentation slides,
 * Lower Third (Lottie graphics), Canvas (scene compositor), Q&A, STT captions, Dictionary,
 * Media (muted — video frames come from the single master player via SharedVideoOutput;
 * audio stays on the main output; note real video never settles into an "unchanged" frame,
 * so MEDIA encodes continuously at the tick rate rather than only-on-change) and Website
 * (mirrors [PresenterManager.webSnapshot], which is only pushed while the Web tab or a real
 * output window hosts the live JCEF browser — a Browser Source alone cannot drive a site) —
 * plus Stage Monitor as a whole separate display-mode layout.
 *
 * The scene provides [LocalTransparentBlanking] = true, so "no background"/Transparent
 * renders genuinely transparent pixels (real alpha survives PNG encoding) for OBS keying,
 * instead of the black a projector window paints.
 *
 * Only encodes and emits a frame when the rendered pixels actually changed since the last
 * tick, so a static slide costs one encode, not continuous encoding — this is what keeps a
 * per-output stream far cheaper than NDI (which encodes continuously regardless of change
 * or of whether anyone is even watching). The "whether anyone is watching" half of that is
 * [shouldRenderTick]'s job: the loop parks entirely while the output is switched off or has
 * no subscriber, because until it did, the render and pixel readback were still paid in full
 * on every tick just to discover the frame was redundant. The one exception is a periodic full-frame
 * recapture (see [FULL_FRAME_RESEED_MS]) that fires on its own schedule regardless of
 * whether content changed, to bound how long a client can stay desynced from a dropped
 * dirty-rect delta (see [frames]'s KDoc for why that can happen).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class BrowserSourceVideoRenderer(
    private val presenterManager: PresenterManager,
    private val appSettingsState: State<AppSettings>,
    private val screenAssignmentState: State<ScreenAssignment>,
    private val effectiveModeState: State<Presenting>,
    private val outputIndex: Int = 0,
    private val sttManager: STTManager? = null,
    private val mediaViewModel: MediaViewModel? = null,
    private val qaDisplayUrlState: State<String>? = null,
    private val serverUrlState: State<String>? = null,
    private val width: Int = 1920,
    private val height: Int = 1080,
    fps: Int = 30,
) {
    /**
     * The off-screen render loop itself. Everything generic about it — the scene, the virtual
     * clock, the pixel readback and the idle parking — lives in [ComposeScenePump]; what stays
     * here is the Browser-Source-specific stage that runs on each frame it produces.
     */
    private val pump = ComposeScenePump(
        width = width,
        height = height,
        fps = fps,
        shouldRender = {
            shouldRenderTick(screenAssignmentState.value.browserSourceEnabled, frames.subscriptionCount.value)
        },
        onPark = ::onPark,
    ) {
        OffscreenOutputContent(
            OffscreenOutputContext(
                presenterManager = presenterManager,
                appSettingsState = appSettingsState,
                screenAssignmentState = screenAssignmentState,
                effectiveModeState = effectiveModeState,
                outputIndex = outputIndex,
                sttManager = sttManager,
                mediaViewModel = mediaViewModel,
                qaDisplayUrlState = qaDisplayUrlState,
                serverUrlState = serverUrlState,
            )
        )
    }

    // Sampling cadence from the per-output fps setting; only changed frames are actually
    // encoded/emitted, so this is a ceiling, not a constant cost.
    internal val tickDelayMs: Long get() = pump.tickDelayMs

    internal companion object {
        // DROP_OLDEST (below) is only safe for dirty-rect deltas because of this: each delta is
        // only valid applied on top of the exact state the previous delta produced, so a silently
        // dropped intermediate delta leaves a connected client permanently wrong in that region
        // with no way to detect it. Forcing a fresh full-frame recapture on this schedule (not
        // just on new-subscriber) bounds how long that drift can persist.
        private const val FULL_FRAME_RESEED_MS = 5_000L

        /**
         * How often to re-check for work while parked — [ComposeScenePump]'s, re-exported because
         * it is this class's own invariant that it stay well above [tickDelayMs]. The reseed path
         * gives a connecting client a full frame the moment the loop wakes, so a quarter second of
         * latency on connect buys back a permanently idle output.
         */
        internal const val IDLE_POLL_MS = ComposeScenePump.IDLE_POLL_MS

        /**
         * Whether this tick is worth rendering at all.
         *
         * Rendering a Browser Source frame is the single most expensive thing this app does per
         * unit time — a full off-screen [ImageComposeScene] render plus a full-resolution pixel
         * readback, ~16 MB of allocation per frame at 1920x1080 — and until this gate existed the
         * loop paid it unconditionally, from app launch, for every configured output, whether or
         * not an OBS/vMix client was connected and whether or not the output was even switched on.
         * Measured on an idle app with two 1080p30 outputs configured and nothing connected: 57.5%
         * of a core and 98% of all allocation, versus 24.6% and near-zero with the outputs removed.
         *
         * [enabled] is the per-output `browserSourceEnabled` switch. It gated *serving* frames in
         * BrowserSourceRoutes but never gated *producing* them, so switching an output off in
         * settings left this loop running at full rate — which is exactly the state a user who
         * turned it off would assume costs nothing.
         *
         * Note the emit path was always change-gated ([decideTick] returns null for an unchanged
         * frame), so a static slide never re-encoded. The cost this removes is the render and
         * readback done *before* that decision — the work of discovering the frame was redundant.
         */
        internal fun shouldRenderTick(enabled: Boolean, subscriberCount: Int): Boolean =
            enabled && subscriberCount > 0

        /**
         * Pure per-tick decision of whether this frame is worth sending and, if so, which
         * rectangle: a dirty-rect delta on plain content change, or the full canvas when a
         * client needs reseeding (first frame, a newly-attached subscriber, or the periodic
         * reseed schedule). Returns null when nothing changed and no reseed is due. Split out
         * from [start] so the decision is testable without an [ImageComposeScene].
         */
        internal fun decideTick(
            intBuf: IntArray,
            previous: IntArray?,
            width: Int,
            height: Int,
            newSubscriberJoined: Boolean,
            elapsedMs: Long,
            lastFullFrameAtMs: Long,
        ): TickDecision? {
            val contentChanged = previous == null || !intBuf.contentEquals(previous)
            val periodicReseedDue = elapsedMs - lastFullFrameAtMs >= FULL_FRAME_RESEED_MS
            if (!contentChanged && !newSubscriberJoined && !periodicReseedDue) return null
            val forceFullFrame = previous == null || newSubscriberJoined || periodicReseedDue
            val rect = if (forceFullFrame) DirtyRect(0, 0, width, height) else computeDirtyRect(intBuf, previous, width, height)
            return TickDecision(rect, forceFullFrame, contentChanged)
        }

        /** First and last column of one row that differ; (width, -1) when the row is unchanged. */
        private fun changedColumns(
            current: IntArray,
            previous: IntArray,
            rowStart: Int,
            width: Int,
        ): Pair<Int, Int> {
            var first = width
            var last = -1
            for (col in 0 until width) {
                val idx = rowStart + col
                if (current[idx] == previous[idx]) continue
                if (col < first) first = col
                last = col
            }
            return first to last
        }

        /**
         * Tight bounding box of pixels that differ between [current] and [previous] (both row-major
         * width*height IntArrays). Only called when the buffers are already known to differ, so a
         * diff always exists. Two-phase for cheapness: first shrink top/bottom via whole-row
         * comparisons to skip unchanged rows cheaply (the common case — a small moving region, e.g.
         * a blinking cursor or a Lottie lower-third, against an otherwise static frame), then scan
         * columns only within the surviving row band. A full-screen crossfade (nearly every pixel
         * changes) degrades to close to the full frame — expected, not a regression.
         */
        internal fun computeDirtyRect(current: IntArray, previous: IntArray, width: Int, height: Int): DirtyRect {
            var minRow = 0
            while (minRow < height && rowsEqual(current, previous, minRow, width)) minRow++
            var maxRow = height - 1
            while (maxRow > minRow && rowsEqual(current, previous, maxRow, width)) maxRow--

            var minCol = width
            var maxCol = -1
            for (row in minRow..maxRow) {
                val (first, last) = changedColumns(current, previous, row * width, width)
                if (first < minCol) minCol = first
                if (last > maxCol) maxCol = last
            }
            // Defensive fallback — the caller guarantees a diff exists, so this shouldn't trigger,
            // but never emit an inverted rect.
            if (minCol > maxCol) {
                minCol = 0
                maxCol = width - 1
            }

            return DirtyRect(x = minCol, y = minRow, w = maxCol - minCol + 1, h = maxRow - minRow + 1)
        }

        private fun rowsEqual(a: IntArray, b: IntArray, row: Int, width: Int): Boolean {
            val start = row * width
            return java.util.Arrays.equals(a, start, start + width, b, start, start + width)
        }

        internal fun cropPixels(src: IntArray, srcWidth: Int, x: Int, y: Int, w: Int, h: Int): IntArray {
            val out = IntArray(w * h)
            for (row in 0 until h) {
                System.arraycopy(src, (y + row) * srcWidth + x, out, row * w, w)
            }
            return out
        }

        /**
         * Encodes a frame rectangle: JPEG when every pixel is fully opaque (several times faster
         * to encode and far smaller — what keeps continuously-changing MEDIA video sustainable at
         * the tick rate), PNG whenever any transparency is present (JPEG has no alpha channel and
         * transparency is the whole point of an overlay). The client sniffs the payload's first
         * byte (0x89 = PNG, 0xFF = JPEG) — see browserSourceOverlayPageHtml in CompanionServer.
         */
        internal fun encodeFrame(argb: IntArray, w: Int, h: Int): ByteArray {
            // Early exit on the first non-opaque pixel — overlay-style frames bail almost
            // immediately; a fully opaque frame (pictures, slides, video) costs one linear scan.
            var fullyOpaque = true
            for (px in argb) {
                if (px ushr ALPHA_SHIFT != OPAQUE_ALPHA) {
                    fullyOpaque = false
                    break
                }
            }
            val out = ByteArrayOutputStream()
            if (fullyOpaque) {
                val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                image.setRGB(0, 0, w, h, argb, 0, w)
                ImageIO.write(image, "jpg", out)
            } else {
                val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                image.setRGB(0, 0, w, h, argb, 0, w)
                ImageIO.write(image, "png", out)
            }
            return out.toByteArray()
        }

        /** Same formula as the real output windows (main.kt): fades only when a crossfade is enabled. */
        internal fun crossfadeDurationMs(
            bibleCrossfadeEnabled: Boolean,
            bibleTransitionDurationMs: Int,
            songCrossfadeEnabled: Boolean,
            songTransitionDurationMs: Int,
        ): Int = maxOf(
            if (bibleCrossfadeEnabled) bibleTransitionDurationMs else 0,
            if (songCrossfadeEnabled) songTransitionDurationMs else 0
        ).coerceAtLeast(MIN_CROSSFADE_MS)

        /** Fades only when a crossfade is enabled AND neither the outgoing nor incoming mode is NONE. */
        internal fun isScreenCrossfadeActive(
            bibleCrossfadeEnabled: Boolean,
            songCrossfadeEnabled: Boolean,
            currentMode: Presenting,
            previousMode: Presenting,
        ): Boolean = (bibleCrossfadeEnabled || songCrossfadeEnabled) &&
            currentMode != Presenting.NONE && previousMode != Presenting.NONE

        /** Per-content-type visibility gate — the same mapping [main.kt]'s own output windows use. */
        internal fun showsContentFor(mode: Presenting, screenAssignment: ScreenAssignment): Boolean = when (mode) {
            Presenting.BIBLE -> screenAssignment.showBible
            Presenting.LYRICS -> screenAssignment.showSongs
            Presenting.PICTURES, Presenting.PRESENTATION -> screenAssignment.showPictures
            Presenting.ANNOUNCEMENTS -> screenAssignment.showAnnouncements
            Presenting.LOWER_THIRD -> screenAssignment.showStreaming
            Presenting.MEDIA -> screenAssignment.showMedia
            Presenting.WEBSITE -> screenAssignment.showWebsite
            Presenting.CANVAS -> screenAssignment.showCanvas
            Presenting.QA -> screenAssignment.showQA
            Presenting.STT -> screenAssignment.showSTT
            Presenting.DICTIONARY -> screenAssignment.showDictionary
            else -> false
        }
    }

    internal data class DirtyRect(val x: Int, val y: Int, val w: Int, val h: Int)

    /** What [decideTick] decided for one tick: send [rect] (the full canvas when [forceFullFrame]), or nothing. */
    internal data class TickDecision(
        val rect: DirtyRect,
        val forceFullFrame: Boolean,
        val contentChanged: Boolean,
    )

    /**
     * Latest frame delta, replayed to any newly-subscribed HTTP client. Uses [BufferOverflow.DROP_OLDEST]
     * (the established pattern elsewhere in this codebase) rather than the default SUSPEND — a
     * slow network consumer must never stall the render loop itself, only skip stale frames.
     *
     * Dropping a *dirty-rect* frame this way is only safe because of [FULL_FRAME_RESEED_MS] —
     * each delta is only valid applied on top of the state the previous one produced, so on its
     * own a dropped delta would leave a client permanently wrong in that region.
     *
     * That the replayed frame is usually a delta is also why the render loop clears this cache
     * when it parks (see [shouldRenderTick]): with no subscribers left there is no canvas for the
     * next client to apply it to, so replaying it would paint a fragment of the old content until
     * the wake-up full frame arrives.
     */
    val frames = MutableSharedFlow<BrowserSourceFrame>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * The previous frame's pixels, reused rather than reallocated. This used to be
     * `lastBuf = intBuf.copyOf()` per changed frame — a fresh width*height IntArray (8.3 MB at
     * 1080p) every time, and 34% of the app's total allocation. The contents are only ever read by
     * [decideTick]/[computeDirtyRect] before being overwritten, so one buffer for the lifetime of
     * the renderer is enough. Lazy, because a configured-but-never-started output should not pay
     * for it.
     */
    private val previousBuf by lazy { IntArray(width * height) }
    private var hasPrevious = false
    private var lastSeenSubscriberCount = 0
    private var lastFullFrameAtMs = 0L

    fun start(scope: CoroutineScope) = pump.start(scope, ::onFrame)

    fun stop() = pump.stop()

    /**
     * The Browser-Source stage of one frame: decide whether these pixels are worth sending and, if
     * so, encode and emit either a dirty-rect delta or the whole canvas.
     *
     * Runs on the pump's coroutine, once per rendered tick. [intBuf] is the pump's own buffer and
     * is overwritten by the next tick, which is why a frame that is kept as the diff baseline is
     * copied into [previousBuf] rather than aliased.
     */
    private suspend fun onFrame(intBuf: IntArray, w: Int, h: Int, elapsedMs: Long) {
        // A newly-attached HTTP client (OBS/vMix reconnect, or a debug tab opened mid-service)
        // must be seeded with a full frame before any dirty-rect delta means anything to it, so
        // force one whenever the subscriber count rises — even on a tick where content didn't
        // otherwise change.
        val subscriberCount = frames.subscriptionCount.value
        val newSubscriberJoined = subscriberCount > lastSeenSubscriberCount
        lastSeenSubscriberCount = subscriberCount

        val lastBuf = if (hasPrevious) previousBuf else null
        val decision = decideTick(intBuf, lastBuf, w, h, newSubscriberJoined, elapsedMs, lastFullFrameAtMs)
            ?: return

        val rect = decision.rect
        val frame = if (decision.forceFullFrame) {
            BrowserSourceFrame(rect.x, rect.y, rect.w, rect.h, w, h, encodeFrame(intBuf, w, h))
        } else {
            val cropped = cropPixels(intBuf, w, rect.x, rect.y, rect.w, rect.h)
            BrowserSourceFrame(rect.x, rect.y, rect.w, rect.h, w, h, encodeFrame(cropped, rect.w, rect.h))
        }
        frames.emit(frame)
        if (decision.forceFullFrame) lastFullFrameAtMs = elapsedMs
        if (decision.contentChanged) {
            System.arraycopy(intBuf, 0, previousBuf, 0, intBuf.size)
            hasPrevious = true
        }
    }

    /**
     * Called once each time the pump parks, i.e. when [shouldRenderTick] turns false.
     *
     * Forgets the diff baseline: whoever connects next has an empty canvas, so the next rendered
     * frame has to be a full one regardless. Dropping it here means that happens via [decideTick]'s
     * existing first-frame path rather than by diffing against pixels no client ever received.
     *
     * And drops what [frames] would replay. The last thing emitted before parking is usually a
     * dirty-rect delta, which only means anything applied on top of the canvas the client before it
     * had built up — so replaying it to the *next* client paints a fragment of old content at that
     * rectangle until the wake-up full frame lands a poll later.
     */
    private fun onPark() {
        hasPrevious = false
        lastSeenSubscriberCount = frames.subscriptionCount.value
        frames.resetReplayCache()
    }
}

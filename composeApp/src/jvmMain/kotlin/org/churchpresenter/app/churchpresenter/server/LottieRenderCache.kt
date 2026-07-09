package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import org.churchpresenter.app.churchpresenter.presenter.LowerThirdOffscreenRenderer
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.viewmodel.isLottieFile
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Disk cache of lower-third lottie animations pre-rendered to raw ARGB frames — the single
 * render pass shared by every consumer. Desktop playback streams frames straight out of the
 * cache, and ATEM uploads convert cached frames to the switcher's YUVA format at upload time
 * via [Reader.nextAtemFrame] (scaling to the raster only when it differs from the cached size).
 *
 * Entries are generated in the background as soon as a lottie file appears in the lower-third
 * folder (created by the generator, dropped in manually, or edited), so playback and
 * "Send to ATEM" can both stream a ready file instead of rendering on the spot.
 *
 * Keys are content-addressed — md5 of the lottie JSON plus the render parameters — so editing
 * a file naturally produces a fresh entry and stale ones age out via LRU.
 *
 * File format (.lrcc — "Lottie Render Cache Clip"):
 *   magic "LRCC" (4) | version u8 | flags u8 | width u32 | height u32 |
 *   fps×100 u32 | frameCount u32 |
 *   per frame: encodedLen u32 + RLE-compressed ARGB |
 *   footer: frameOffset u64 × frameCount | footerStart u64
 *
 * The trailing footer gives random access to any frame (playback seeks, pause-at-frame holds).
 * RLE runs over whole 32-bit ARGB pixels: a record is [count i32][...] — count > 0 is a run of
 * one repeated pixel value, count < 0 is |count| literal pixel values. (The YUVA sentinel trick
 * AtemFrameEncoder uses is not safe here: any 8-byte value can occur in ARGB data.) Lower
 * thirds are mostly transparent, so frames typically shrink >90%.
 */
object LottieRenderCache {

    private const val MAGIC = "LRCC"
    private const val VERSION = 1
    private const val MAX_ENTRIES = 60
    private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

    /** Frame rate desktop playback variants are rendered at. */
    const val PLAYBACK_FPS = 30

    /** Same-aspect tolerance for sharing one cache entry between desktop and ATEM sizes. */
    private const val ASPECT_TOLERANCE = 0.01

    /** Header byte length: magic(4) + version(1) + flags(1) + w(4) + h(4) + fps(4) + frames(4). */
    private const val HEADER_LEN = 22L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cacheDir = File(System.getProperty("user.home"), ".churchpresenter/lottie_render_cache")

    /** One render at a time — each opens an off-screen Compose scene. */
    private val renderMutex = Mutex()

    private val jobs = ConcurrentHashMap<String, Deferred<File>>()
    private val progressFlows = ConcurrentHashMap<String, MutableStateFlow<Float>>()

    init {
        // One-time migration: the pre-unification ATEM-only cache (YUVA .acpc files) is
        // superseded by this shared ARGB cache — delete it so it doesn't linger on disk.
        scope.launch(Dispatchers.IO) {
            File(System.getProperty("user.home"), ".churchpresenter/atem_render_cache")
                .takeIf { it.isDirectory }
                ?.deleteRecursively()
        }
    }

    /** Render parameters that, together with the lottie content, identify a cache entry. */
    data class Variant(
        val clip: Boolean,
        val width: Int,
        val height: Int,
        val fps: Double = 0.0,
        val frameCount: Int = 1
    )

    // ── Lottie JSON helpers ────────────────────────────────────────────────────

    /** Canvas size (w × h) straight from the lottie JSON. */
    fun lottieCanvasSize(lottieJson: String): Pair<Int, Int>? = try {
        val obj = Json.parseToJsonElement(lottieJson).jsonObject
        val w = obj["w"]?.jsonPrimitive?.double?.toInt() ?: return null
        val h = obj["h"]?.jsonPrimitive?.double?.toInt() ?: return null
        if (w > 0 && h > 0) w to h else null
    } catch (_: Exception) {
        null
    }

    /** Clip duration straight from the lottie JSON: (op - ip) / fr seconds. */
    fun lottieDurationMs(lottieJson: String): Long? = try {
        val obj = Json.parseToJsonElement(lottieJson).jsonObject
        val fr = obj["fr"]?.jsonPrimitive?.double ?: return null
        val ip = obj["ip"]?.jsonPrimitive?.double ?: 0.0
        val op = obj["op"]?.jsonPrimitive?.double ?: return null
        if (fr <= 0.0 || op <= ip) null
        else (((op - ip) / fr) * 1000.0).toLong().coerceAtLeast(1L)
    } catch (_: Exception) {
        null
    }

    /** Frame count for a clip of this lottie at the given fps, or null if the JSON has no timing. */
    fun clipFrameCount(lottieJson: String, fps: Double): Int? =
        lottieDurationMs(lottieJson)?.let { ((it / 1000.0) * fps).toInt().coerceAtLeast(1) }

    // ── Variant / size policy ──────────────────────────────────────────────────

    /**
     * Scales (w, h) down proportionally so neither side exceeds 1920 — output windows draw
     * pre-rendered frames with ContentScale.Fit, so a larger canvas wastes disk and decode
     * time with no visual benefit.
     */
    fun clampCanvasSize(w: Int, h: Int): Pair<Int, Int> {
        val longestSide = maxOf(w, h)
        if (longestSide <= MAX_CANVAS_DIMENSION) return w to h
        val scale = MAX_CANVAS_DIMENSION.toDouble() / longestSide
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    private const val MAX_CANVAS_DIMENSION = 1920

    private fun clampedCanvas(lottieJson: String): Pair<Int, Int> {
        val (w, h) = lottieCanvasSize(lottieJson) ?: (1920 to 1080)
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
    fun atemRenderSize(lottieJson: String, atem: AtemSettings): Pair<Int, Int> {
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
    fun desktopRenderSize(lottieJson: String, atem: AtemSettings?): Pair<Int, Int> {
        val canvas = clampedCanvas(lottieJson)
        if (atem == null || atem.host.isBlank()) return canvas
        val raster = atem.renderWidth to atem.renderHeight
        return if (aspectsMatch(canvas, raster))
            maxOf(canvas.first, raster.first) to maxOf(canvas.second, raster.second)
        else canvas
    }

    /** The cache variant an ATEM still or clip upload should prepare and read. */
    fun atemVariant(
        lottieJson: String,
        atem: AtemSettings,
        clip: Boolean,
        fps: Double = atem.clipFps,
        fallbackFrameCount: Int = 1
    ): Variant {
        val (w, h) = atemRenderSize(lottieJson, atem)
        if (!clip) return Variant(clip = false, width = w, height = h)
        val frames = clipFrameCount(lottieJson, fps) ?: fallbackFrameCount
        return Variant(true, w, h, fps, frames)
    }

    /** The cache variant desktop playback streams from, or null if the JSON has no timing. */
    fun desktopVariant(lottieJson: String, atem: AtemSettings?): Variant? {
        val (w, h) = desktopRenderSize(lottieJson, atem)
        val frames = clipFrameCount(lottieJson, PLAYBACK_FPS.toDouble()) ?: return null
        return Variant(true, w, h, PLAYBACK_FPS.toDouble(), frames)
    }

    // ── Preparation ────────────────────────────────────────────────────────────

    fun isReady(lottieJson: String, variant: Variant): Boolean =
        cacheFile(keyFor(lottieJson, variant)).exists()

    /** Render progress (0..1) of an entry's in-flight job; 1f when already cached. */
    fun progressFlow(lottieJson: String, variant: Variant): StateFlow<Float> {
        val key = keyFor(lottieJson, variant)
        return progressFlows.getOrPut(key) {
            MutableStateFlow(if (cacheFile(key).exists()) 1f else 0f)
        }
    }

    /**
     * Returns the cache file for this content+variant, rendering it first if needed.
     * Concurrent calls for the same key share one job; renders are serialized. The job
     * keeps running if the caller goes away, so the cache still gets warm.
     */
    fun prepare(lottieJson: String, variant: Variant): Deferred<File> {
        val key = keyFor(lottieJson, variant)
        // computeIfAbsent must not race a completing job removing itself — loop once
        while (true) {
            var created = false
            val job = jobs.computeIfAbsent(key) {
                created = true
                scope.async {
                    val dest = cacheFile(key)
                    if (!dest.exists()) {
                        renderMutex.withLock {
                            if (!dest.exists()) renderToFile(lottieJson, variant, dest, key)
                        }
                    }
                    progressFlows.getOrPut(key) { MutableStateFlow(0f) }.value = 1f
                    dest
                }
            }
            // Registered outside computeIfAbsent: an already-completed Deferred invokes this
            // handler synchronously, and doing that from within the map's own update callback
            // is what causes ConcurrentHashMap's "Recursive update" IllegalStateException.
            if (created) job.invokeOnCompletion { jobs.remove(key, job) }
            if (!job.isCancelled) return job
        }
    }

    /**
     * Backfill cache entries for every lottie file in the lower-third folder.
     * Called at app startup so playback and uploads are ready without ever opening the tab.
     */
    fun ensureForFolder(folderPath: String, atem: AtemSettings?) {
        if (folderPath.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            File(folderPath).takeIf { it.isDirectory }
                ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
                ?.forEach { ensureForFile(it, atem) }
        }
    }

    /**
     * Make sure the variants this lottie file will be consumed at exist, generating them in
     * the background if missing: the desktop playback clip always, plus the ATEM still and
     * clip variants when an ATEM is configured (usually the same entry as the desktop clip).
     */
    fun ensureForFile(file: File, atem: AtemSettings?) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = file.readText()
                desktopVariant(json, atem)?.let { prepare(json, it) }
                if (atem != null && atem.host.isNotBlank()) {
                    prepare(json, atemVariant(json, atem, clip = false))
                    if (clipFrameCount(json, atem.clipFps) != null) {
                        prepare(json, atemVariant(json, atem, clip = true))
                    }
                }
            } catch (e: Exception) {
                System.err.println("[LottieRenderCache] Failed to prepare ${file.name}: ${e.message}")
                CrashReporter.reportWarning(
                    "Failed to prepare lottie render cache for ${file.name}",
                    throwable = e,
                    tags = mapOf("subsystem" to "lower_third")
                )
            }
        }
    }

    // ── Reading ────────────────────────────────────────────────────────────────

    /**
     * Random-access frame reader for an .lrcc cache file. Frames decode to ARGB IntArrays;
     * [nextAtemFrame] additionally converts to the ATEM's RLE-YUVA upload format. Not
     * thread-safe — use one Reader per consumer.
     */
    class Reader(file: File) : Closeable {
        private val raf = RandomAccessFile(file, "r")
        val width: Int
        val height: Int
        /** fps × 100 as stored; 0 for stills. */
        val fpsX100: Int
        val frameCount: Int
        private val frameOffsets: LongArray
        private var nextIndex = 0

        init {
            try {
                val magic = ByteArray(4).also { raf.readFully(it) }
                if (String(magic, Charsets.US_ASCII) != MAGIC) {
                    throw IOException("Not a lottie render cache file: ${file.name}")
                }
                val version = raf.readUnsignedByte()
                if (version != VERSION) {
                    throw IOException("Unsupported cache version $version: ${file.name}")
                }
                raf.readUnsignedByte() // flags — reserved
                width = raf.readInt()
                height = raf.readInt()
                fpsX100 = raf.readInt()
                frameCount = raf.readInt()
                raf.seek(raf.length() - 8)
                val footerStart = raf.readLong()
                raf.seek(footerStart)
                frameOffsets = LongArray(frameCount) { raf.readLong() }
            } catch (e: Exception) {
                raf.close()
                throw e
            }
        }

        /** Decode the frame at [index] to ARGB pixels (width × height). */
        fun frameArgb(index: Int): IntArray {
            val i = index.coerceIn(0, frameCount - 1)
            raf.seek(frameOffsets[i])
            val len = raf.readInt()
            val payload = ByteArray(len).also { raf.readFully(it) }
            nextIndex = i + 1
            return decodeArgbRle(payload, width * height)
        }

        /** Sequential [frameArgb], starting at frame 0. */
        fun nextFrameArgb(): IntArray = frameArgb(nextIndex)

        /**
         * Next frame converted for an ATEM upload: decode ARGB → bilinear-scale to the
         * switcher raster if the cached size differs (same-aspect by variant policy) →
         * 10-bit YUVA 4:2:2 + RLE.
         */
        fun nextAtemFrame(targetWidth: Int, targetHeight: Int): EncodedFrame {
            var argb = nextFrameArgb()
            if (targetWidth != width || targetHeight != height) {
                argb = scaleArgb(argb, width, height, targetWidth, targetHeight)
            }
            return AtemFrameEncoder.encodeFrame(targetWidth, targetHeight, argb)
        }

        override fun close() = raf.close()
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun keyFor(lottieJson: String, v: Variant): String {
        val md5 = MessageDigest.getInstance("MD5").digest(lottieJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return if (!v.clip) "${md5}_${v.width}x${v.height}_still"
        else "${md5}_${v.width}x${v.height}_${"%.2f".format(Locale.US, v.fps)}x${v.frameCount}_clip"
    }

    // Version in the filename so a format/behavior change invalidates old entries
    // (leftovers age out through LRU eviction)
    private fun cacheFile(key: String) = File(cacheDir, "${key}_v$VERSION.lrcc")

    private suspend fun renderToFile(lottieJson: String, v: Variant, dest: File, key: String) {
        cacheDir.mkdirs()
        val progress = progressFlows.getOrPut(key) { MutableStateFlow(0f) }
        progress.value = 0f
        val renderer = LowerThirdOffscreenRenderer(v.width, v.height)
        val tmp = File(cacheDir, "$key.tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                out.writeBytes(MAGIC)
                out.writeByte(VERSION)
                out.writeByte(0) // flags — reserved
                out.writeInt(v.width)
                out.writeInt(v.height)
                out.writeInt(if (v.clip) (v.fps * 100).toInt() else 0)
                val frames = if (v.clip) v.frameCount else 1
                out.writeInt(frames)
                var pos = HEADER_LEN
                val offsets = LongArray(frames)
                var maxEncodedSize = 0
                // Stills capture the animation midpoint (lower thirds are empty at frame 0)
                renderer.withSession(lottieJson, initialProgress = if (v.clip) 0f else 0.5f) { renderFrame ->
                    for (i in 0 until frames) {
                        val p = if (v.clip) i.toFloat() / frames else 0.5f
                        val enc = encodeArgbRle(renderFrame(p))
                        offsets[i] = pos
                        out.writeInt(enc.size)
                        out.write(enc)
                        pos += 4 + enc.size
                        maxEncodedSize = maxOf(maxEncodedSize, enc.size)
                        progress.value = (i + 1).toFloat() / frames
                    }
                }
                offsets.forEach { out.writeLong(it) }
                out.writeLong(pos)
                // A fully uniform frame RLE-encodes to a single 8-byte record — if every
                // frame did, the off-screen capture almost certainly produced blanks
                if (maxEncodedSize <= 16) {
                    System.err.println("[LottieRenderCache] WARNING: all frames of $key are uniform — captures may be blank")
                }
            }
            dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("Could not move cache file into place: ${dest.name}")
            evictOldEntries()
        } finally {
            tmp.delete()
        }
    }

    private fun evictOldEntries() {
        val entries = cacheDir.listFiles { f -> f.extension == "lrcc" } ?: return
        val byAge = entries.sortedBy { it.lastModified() }
        var totalBytes = entries.sumOf { it.length() }
        var excessCount = entries.size - MAX_ENTRIES
        for (f in byAge) {
            if (excessCount <= 0 && totalBytes <= MAX_TOTAL_BYTES) break
            totalBytes -= f.length()
            excessCount--
            f.delete()
        }
    }

    // ── ARGB RLE codec ────────────────────────────────────────────────────────

    /** Runs shorter than this stay literal — a run record costs 8 bytes. */
    private const val MIN_RUN = 3

    private fun encodeArgbRle(pixels: IntArray): ByteArray {
        // Worst case is alternating length-1 literal records and minimum runs, which stays
        // at parity with raw size; headroom covers record headers.
        val buf = ByteBuffer.allocate(pixels.size * 4 + pixels.size / MIN_RUN * 4 + 16)
        var i = 0
        var literalStart = -1
        while (i < pixels.size) {
            var runEnd = i + 1
            val value = pixels[i]
            while (runEnd < pixels.size && pixels[runEnd] == value) runEnd++
            val runLen = runEnd - i
            if (runLen >= MIN_RUN) {
                if (literalStart >= 0) {
                    buf.putInt(-(i - literalStart))
                    for (j in literalStart until i) buf.putInt(pixels[j])
                    literalStart = -1
                }
                buf.putInt(runLen)
                buf.putInt(value)
            } else if (literalStart < 0) {
                literalStart = i
            }
            i = if (runLen >= MIN_RUN) runEnd else i + runLen
        }
        if (literalStart >= 0) {
            buf.putInt(-(pixels.size - literalStart))
            for (j in literalStart until pixels.size) buf.putInt(pixels[j])
        }
        return buf.array().copyOf(buf.position())
    }

    private fun decodeArgbRle(payload: ByteArray, pixelCount: Int): IntArray {
        val buf = ByteBuffer.wrap(payload)
        val out = IntArray(pixelCount)
        var o = 0
        while (buf.remaining() >= 4 && o < pixelCount) {
            val count = buf.int
            if (count > 0) {
                val value = buf.int
                out.fill(value, o, minOf(o + count, pixelCount))
                o += count
            } else {
                var n = -count
                while (n-- > 0 && o < pixelCount) out[o++] = buf.int
            }
        }
        if (o < pixelCount) throw IOException("Truncated RLE frame: got $o of $pixelCount pixels")
        return out
    }

    /** Bilinear ARGB scale (used only for same-aspect raster mismatches at ATEM upload time). */
    private fun scaleArgb(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        val srcImg = BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
        srcImg.setRGB(0, 0, sw, sh, src, 0, sw)
        val dstImg = BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB)
        val g = dstImg.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(srcImg, 0, 0, dw, dh, null)
        } finally {
            g.dispose()
        }
        return dstImg.getRGB(0, 0, dw, dh, null, 0, dw)
    }
}

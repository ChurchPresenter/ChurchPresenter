package org.churchpresenter.lowerthird.render

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.atem.AtemFrameEncoder
import org.churchpresenter.atem.EncodedFrame
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

private const val FOOTER_POINTER_BYTES = 8
private const val FPS_SCALE = 100
private const val INT_BYTES = 4
private const val UNIFORM_FRAME_MAX_BYTES = 16

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
 * a file naturally produces a fresh entry and stale ones age out through [LottieCacheFiles.evictOldEntries].
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
object LottieRenderCache : LottieRenderPolicy by LottieRenderSizes {

    private const val MAGIC = "LRCC"
    // 2: text in scripts like Tamil is shaped as whole lines (the file's Text shaping), so frames
    // rendered letter by letter before it must not be reused for an unchanged file
    const val VERSION = 2
    internal const val MAX_ENTRIES = 60


    /** Frame rate desktop playback variants are rendered at. */
    const val PLAYBACK_FPS = 30


    /** Header byte length: magic(4) + version(1) + flags(1) + w(4) + h(4) + fps(4) + frames(4). */
    private const val HEADER_LEN = 22L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    internal val cacheDir: File get() = LottieCacheFiles.cacheDir

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

    // ── Preparation ────────────────────────────────────────────────────────────

    fun isReady(lottieJson: String, variant: Variant): Boolean =
        LottieCacheFiles.cacheFile(LottieCacheFiles.keyFor(lottieJson, variant)).exists()

    /**
     * Render progress (0..1) of an entry's in-flight job; 1f when already cached. The first call for
     * an entry asks the disk, so call it off the UI thread.
     */
    fun progressFlow(lottieJson: String, variant: Variant): StateFlow<Float> {
        val key = LottieCacheFiles.keyFor(lottieJson, variant)
        return progressFlows.getOrPut(key) {
            MutableStateFlow(if (LottieCacheFiles.cacheFile(key).exists()) 1f else 0f)
        }
    }

    /**
     * Returns the cache file for this content+variant, rendering it first if needed.
     * Concurrent calls for the same key share one job; renders are serialized. The job
     * keeps running if the caller goes away, so the cache still gets warm.
     */
    fun prepare(lottieJson: String, variant: Variant): Deferred<File> {
        val key = LottieCacheFiles.keyFor(lottieJson, variant)
        // computeIfAbsent must not race a completing job removing itself — loop once
        while (true) {
            var created = false
            val job = jobs.computeIfAbsent(key) {
                created = true
                scope.async {
                    val dest = LottieCacheFiles.cacheFile(key)
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
        scope.launch(Dispatchers.IO) {
            // The only eviction pass that does not depend on a render succeeding. Without it a
            // directory left over cap — or holding a scratch file from a crashed render — is
            // never trimmed again until something new happens to render.
            runCatching { LottieCacheFiles.evictOldEntries() }
        }
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
     * Returns the background job, which nothing in the app waits on; a test does.
     */
    fun ensureForFile(file: File, atem: AtemSettings?): Job =
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
            } catch (e: IOException) {
                prepareFailed(file, e)
            } catch (e: IllegalArgumentException) {
                // A file in the folder that is not valid Lottie JSON.
                prepareFailed(file, e)
            }
        }

    private fun prepareFailed(file: File, e: Exception) {
        Log.warn("LottieRenderCache", "Failed to prepare ${file.name}: ${e.message}")
        CrashReporter.reportWarning(
            "Failed to prepare lottie render cache for ${file.name}",
            throwable = e,
            tags = mapOf("subsystem" to "lower_third")
        )
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
            // Closed here only when the header cannot be read; on success the reader owns it.
            var opened = false
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
                raf.seek(raf.length() - FOOTER_POINTER_BYTES)
                val footerStart = raf.readLong()
                raf.seek(footerStart)
                frameOffsets = LongArray(frameCount) { raf.readLong() }
                opened = true
            } finally {
                if (!opened) raf.close()
            }
        }

        /** Decode the frame at [index] to ARGB pixels (width × height). */
        fun frameArgb(index: Int): IntArray {
            val i = index.coerceIn(0, frameCount - 1)
            raf.seek(frameOffsets[i])
            val len = raf.readInt()
            val payload = ByteArray(len).also { raf.readFully(it) }
            nextIndex = i + 1
            return ArgbRle.decodeArgbRle(payload, width * height)
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
                argb = ArgbRle.scaleArgb(argb, width, height, targetWidth, targetHeight)
            }
            return AtemFrameEncoder.encodeFrame(targetWidth, targetHeight, argb)
        }

        override fun close() = raf.close()
    }

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
                out.writeInt(if (v.clip) (v.fps * FPS_SCALE).toInt() else 0)
                val frames = if (v.clip) v.frameCount else 1
                out.writeInt(frames)
                var pos = HEADER_LEN
                val offsets = LongArray(frames)
                var maxEncodedSize = 0
                // Stills capture the animation midpoint (lower thirds are empty at frame 0)
                renderer.withSession(lottieJson, initialProgress = if (v.clip) 0f else 0.5f) { renderFrame ->
                    for (i in 0 until frames) {
                        val p = if (v.clip) i.toFloat() / frames else 0.5f
                        val enc = ArgbRle.encodeArgbRle(renderFrame(p))
                        offsets[i] = pos
                        out.writeInt(enc.size)
                        out.write(enc)
                        pos += INT_BYTES + enc.size
                        maxEncodedSize = maxOf(maxEncodedSize, enc.size)
                        progress.value = (i + 1).toFloat() / frames
                    }
                }
                offsets.forEach { out.writeLong(it) }
                out.writeLong(pos)
                // A fully uniform frame RLE-encodes to a single 8-byte record — if every
                // frame did, the off-screen capture almost certainly produced blanks
                if (maxEncodedSize <= UNIFORM_FRAME_MAX_BYTES) {
                    Log.warn("LottieRenderCache", "all frames of $key are uniform — captures may be blank")
                }
            }
            dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("Could not move cache file into place: ${dest.name}")
            LottieCacheFiles.evictOldEntries()
        } finally {
            tmp.delete()
        }
    }

}

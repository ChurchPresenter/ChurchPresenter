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
import org.churchpresenter.app.churchpresenter.viewmodel.isLottieFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk cache of lower thirds pre-rendered and pre-encoded for ATEM upload.
 *
 * Entries are generated in the background as soon as a lottie file appears in the
 * lower-third folder (created by the generator, dropped in manually, or edited), so
 * "Send to ATEM" can stream a ready file instead of rendering on the spot.
 *
 * Keys are content-addressed — md5 of the lottie JSON plus the render parameters —
 * so editing a file naturally produces a fresh entry and stale ones age out via LRU.
 *
 * File format (.acpc — "ATEM Cache Pre-encoded Clip"):
 *   magic "ACPC" (4) | version u8 | frameCount u32 | rawFrameLen u32 |
 *   per frame: encodedLen u32 + RLE-encoded YUVA bytes
 */
object AtemRenderCache {

    private const val MAGIC = "ACPC"
    // v4: windowless ImageComposeScene rendering (v1-v3 hidden-window captures were blank)
    private const val VERSION = 4
    private const val MAX_ENTRIES = 100

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cacheDir = File(System.getProperty("user.home"), ".churchpresenter/atem_render_cache")

    /** One render at a time — each opens an off-screen Compose window. */
    private val renderMutex = Mutex()

    private val jobs = ConcurrentHashMap<String, Deferred<File>>()
    private val progressFlows = ConcurrentHashMap<String, MutableStateFlow<Float>>()

    /** Render parameters that, together with the lottie content, identify a cache entry. */
    data class Variant(
        val clip: Boolean,
        val width: Int,
        val height: Int,
        val fps: Double = 0.0,
        val frameCount: Int = 1
    )

    /** Canvas size (w × h) straight from the lottie JSON. */
    fun lottieCanvasSize(lottieJson: String): Pair<Int, Int>? = try {
        val obj = Json.parseToJsonElement(lottieJson).jsonObject
        val w = obj["w"]?.jsonPrimitive?.double?.toInt() ?: return null
        val h = obj["h"]?.jsonPrimitive?.double?.toInt() ?: return null
        if (w > 0 && h > 0) w to h else null
    } catch (_: Exception) {
        null
    }

    /** ATEM render size for a lottie: its own canvas, falling back to the configured frame. */
    fun renderSize(lottieJson: String, atem: AtemSettings): Pair<Int, Int> =
        lottieCanvasSize(lottieJson) ?: (atem.renderWidth to atem.renderHeight)

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
     * Returns the cache file for this content+variant, rendering and encoding it first
     * if needed. Concurrent calls for the same key share one job; renders are serialized.
     * The job keeps running if the caller goes away, so the cache still gets warm.
     */
    fun prepare(lottieJson: String, variant: Variant): Deferred<File> {
        val key = keyFor(lottieJson, variant)
        // computeIfAbsent must not race a completing job removing itself — loop once
        while (true) {
            val job = jobs.computeIfAbsent(key) {
                scope.async {
                    val dest = cacheFile(key)
                    if (!dest.exists()) {
                        renderMutex.withLock {
                            if (!dest.exists()) renderToFile(lottieJson, variant, dest, key)
                        }
                    }
                    progressFlows.getOrPut(key) { MutableStateFlow(0f) }.value = 1f
                    dest
                }.also { d -> d.invokeOnCompletion { jobs.remove(key, d) } }
            }
            if (!job.isCancelled) return job
        }
    }

    /**
     * Backfill cache entries for every lottie file in the lower-third folder.
     * Called at app startup so uploads are ready without ever opening the tab.
     */
    fun ensureForFolder(folderPath: String, atem: AtemSettings) {
        if (folderPath.isEmpty() || atem.host.isBlank()) return
        scope.launch(Dispatchers.IO) {
            File(folderPath).takeIf { it.isDirectory }
                ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
                ?.forEach { ensureForFile(it, atem) }
        }
    }

    /**
     * Make sure still + clip entries exist for this lottie file, generating them in the
     * background if missing. No-op when the ATEM is not configured.
     */
    fun ensureForFile(file: File, atem: AtemSettings) {
        if (atem.host.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val json = file.readText()
                val (w, h) = renderSize(json, atem)
                prepare(json, Variant(clip = false, width = w, height = h))
                val frames = clipFrameCount(json, atem.clipFps) ?: return@launch
                prepare(json, Variant(true, w, h, atem.clipFps, frames))
            } catch (e: Exception) {
                System.err.println("[AtemRenderCache] Failed to prepare ${file.name}: ${e.message}")
            }
        }
    }

    /** Sequential frame reader for an .acpc cache file. */
    class Reader(file: File) : Closeable {
        private val input = DataInputStream(BufferedInputStream(FileInputStream(file)))
        val frameCount: Int
        val rawFrameLen: Int

        init {
            val magic = ByteArray(4).also { input.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != MAGIC) {
                input.close(); throw IOException("Not an ATEM cache file: ${file.name}")
            }
            val version = input.readUnsignedByte()
            if (version != VERSION) {
                input.close(); throw IOException("Unsupported cache version $version: ${file.name}")
            }
            frameCount = input.readInt()
            rawFrameLen = input.readInt()
        }

        fun nextFrame(): EncodedFrame {
            val len = input.readInt()
            val bytes = ByteArray(len).also { input.readFully(it) }
            return EncodedFrame(bytes, rawFrameLen)
        }

        override fun close() = input.close()
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun keyFor(lottieJson: String, v: Variant): String {
        val md5 = MessageDigest.getInstance("MD5").digest(lottieJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return if (!v.clip) "${md5}_${v.width}x${v.height}_still"
        else "${md5}_${v.width}x${v.height}_${"%.2f".format(java.util.Locale.US, v.fps)}x${v.frameCount}_clip"
    }

    // Version in the filename so a format/behavior change invalidates old entries
    // (leftovers age out through LRU eviction)
    private fun cacheFile(key: String) = File(cacheDir, "${key}_v$VERSION.acpc")

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
                val frames = if (v.clip) v.frameCount else 1
                out.writeInt(frames)
                out.writeInt(v.width * v.height * 4)
                // Stills capture the animation midpoint (lower thirds are empty at frame 0)
                var maxEncodedSize = 0
                renderer.withSession(lottieJson, initialProgress = if (v.clip) 0f else 0.5f) { renderFrame ->
                    for (i in 0 until frames) {
                        val p = if (v.clip) i.toFloat() / frames else 0.5f
                        val argb = renderFrame(p)
                        val enc = AtemFrameEncoder.encodeFrame(v.width, v.height, argb)
                        maxEncodedSize = maxOf(maxEncodedSize, enc.data.size)
                        out.writeInt(enc.data.size)
                        out.write(enc.data)
                        progress.value = (i + 1).toFloat() / frames
                    }
                }
                // A fully uniform frame RLE-encodes to a single 24-byte run — if every
                // frame did, the off-screen capture almost certainly produced blanks
                if (maxEncodedSize <= 32) {
                    System.err.println("[AtemRenderCache] WARNING: all frames of $key are uniform — captures may be blank")
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
        val entries = cacheDir.listFiles { f -> f.extension == "acpc" } ?: return
        if (entries.size <= MAX_ENTRIES) return
        entries.sortedBy { it.lastModified() }
            .take(entries.size - MAX_ENTRIES)
            .forEach { it.delete() }
    }
}

package org.churchpresenter.app.churchpresenter.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.random.Random

/**
 * The mechanics every Bible source shares: fetch an archive, prove it arrived intact, unpack it,
 * and put the finished module in place without ever endangering the one already installed.
 *
 * All of it runs in-JVM — the archives are zips and the converters are on our own classpath, so
 * nothing shells out to an external tool.
 */
internal object BibleInstallSupport {

    const val COPY_BUFFER_BYTES = 64 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 64
    private const val MAX_EXTRACTED_BYTES = 256L * 1024 * 1024

    // Phase boundaries, chosen so the bar's movement roughly matches where the time actually goes.
    const val DOWNLOAD_END = 0.55f
    const val EXTRACT_END = 0.60f
    const val CONVERT_END = 0.97f

    /**
     * Where reading the source file ends, for a source with nothing to unzip.
     *
     * The Holy Bible XML archive publishes bare XML up to 21 MB, so the parse owns the slice the
     * other two sources spend extracting — without this the bar would sit at [DOWNLOAD_END] through
     * the longest part of the install.
     */
    const val PARSE_END = 0.80f

    /** Attempts in a row that move the file forward not one byte, before the download is given up on. */
    private const val MAX_STALLED_ATTEMPTS = 3

    /** A ceiling however much progress each attempt makes — a runaway guard, not the normal exit. */
    private const val MAX_DOWNLOAD_ATTEMPTS = 12

    /**
     * How long a download may sit without a single byte arriving before the attempt is abandoned and
     * resumed. Half of what a download used to be allowed, and only safe because it is now
     * recoverable: before the resume below existed, tripping this threw the whole download away.
     */
    private const val DOWNLOAD_SOCKET_TIMEOUT_MS = 30_000L

    internal const val DEFAULT_DOWNLOAD_RETRY_FLOOR_MS = 2_000L
    private const val MAX_DOWNLOAD_RETRY_DELAY_MS = 30_000L

    /**
     * This client also fetches eBible's catalogue (`EBibleSource.catalog`), which is one small file
     * and wants a whole-request cap. [downloadTo] overrides both timeouts per request, because a
     * module on a throttled line needs the opposite: no request cap at all, and a shorter stall
     * threshold that starts a resume sooner.
     */
    val defaultHttp: HttpClient by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 300_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    /** The body ended before the bytes the response promised had arrived. */
    private class TruncatedBodyException(promised: Long, received: Long) :
        IOException("Body ended after $received of $promised bytes")

    /** Every attempt stopped part-way: the link is alive, it just stops moving. */
    class DownloadStalledException(
        val attempts: Int,
        val bytesWritten: Long,
        cause: Throwable,
    ) : IOException("Download stalled after $attempts attempts ($bytesWritten bytes)", cause)

    /**
     * Doubling backoff with a cap and ±20% jitter, so a church whose whole network drops does not
     * bring every machine back in lockstep.
     *
     * Deliberately a second copy of the shape in
     * [org.churchpresenter.app.churchpresenter.viewmodel.BibleEngineClient]'s `retryDelayMs` rather
     * than a shared helper: `data/` must not depend on `viewmodel/`. Change one, look at the other.
     *
     * [floorMs] is also the lower bound, so passing 0 gives exactly 0 — which is how the tests wait
     * for nothing at all.
     */
    internal fun downloadRetryDelayMs(attempt: Int, floorMs: Long = DEFAULT_DOWNLOAD_RETRY_FLOOR_MS): Long {
        val base = (floorMs shl attempt.coerceIn(0, 4)).coerceAtMost(MAX_DOWNLOAD_RETRY_DELAY_MS)
        return (base * Random.nextDouble(0.8, 1.2)).toLong().coerceAtLeast(floorMs)
    }

    /**
     * A scratch directory inside the Bible folder — deliberately not the system temp dir, because
     * the finishing move must be an atomic rename, which only works within one filesystem, and
     * Bible folders are routinely on network shares or external drives.
     */
    fun scratchIn(targetDir: File): File = File(targetDir, ".cp-install")

    fun usableDirectory(targetDir: File): Boolean =
        (targetDir.exists() || targetDir.mkdirs()) && targetDir.isDirectory

    /**
     * Streams [url] to [destination], reporting 0..[DOWNLOAD_END]. Returns the HTTP status.
     *
     * A stalled read costs a resume, not the download: the bytes already on disk are kept and the
     * tail is asked for with a `Range` header. That is what the failing case needs — a link
     * throttled hard enough to stall every minute could never finish a module while one timeout
     * discarded everything. An attempt that moves the file forward buys back the whole retry
     * budget, so only a link that has genuinely stopped delivering gives up.
     *
     * A non-2xx answer is never retried: a 404 or a 403 will say the same thing next time.
     *
     * @param retryFloorMs the first backoff delay; 0 in tests, so they wait for nothing.
     * @throws DownloadStalledException every attempt timed out mid-body.
     */
    suspend fun downloadTo(
        url: String,
        destination: File,
        http: HttpClient,
        expectedBytes: Long,
        retryFloorMs: Long = DEFAULT_DOWNLOAD_RETRY_FLOOR_MS,
        onProgress: (Float) -> Unit,
    ): DownloadResult {
        // Deliberately outside the loop: this is the state a resumed attempt picks back up from.
        var written = 0L
        var total = expectedBytes.takeIf { it > 0 } ?: -1L
        var highest = 0f
        var stalledAttempts = 0
        var attempts = 0
        var lastFailure: IOException? = null

        // Announced before the first byte so the row names the phase even when the size is unknown
        // — eBible's catalogue carries no size, so a server that omits Content-Length would
        // otherwise leave the longest phase of the install completely silent.
        onProgress(0f)

        while (attempts < MAX_DOWNLOAD_ATTEMPTS) {
            attempts++
            val resumeFrom = written
            try {
                val status = http.prepareGet(url) {
                    if (resumeFrom > 0) header(HttpHeaders.Range, "bytes=$resumeFrom-")
                    timeout {
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = DOWNLOAD_SOCKET_TIMEOUT_MS
                    }
                }.execute { response ->
                    val code = response.status.value
                    // Asked for a tail that isn't there: everything wanted is already on disk.
                    if (code == 416 && resumeFrom > 0) return@execute 200
                    if (code !in 200..299) return@execute code

                    // Only a 206 whose Content-Range starts exactly where we left off is a tail. A
                    // server that ignores Range answers 200 with the whole body, and appending that
                    // would splice the file into nonsense — so start the file over instead.
                    val append = code == 206 && resumeFrom > 0 && rangeStartsAt(response, resumeFrom)
                    if (!append) written = 0
                    // What this answer promises, as opposed to what the catalogue said the whole
                    // module weighs — only the former is a promise this response can break.
                    val promised = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        ?.let { it + if (append) resumeFrom else 0 }
                    if (promised != null) total = promised

                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    FileOutputStream(destination, append).use { out ->
                        while (true) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read <= 0) {
                                if (read == -1) break else continue
                            }
                            out.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val fraction = (written.toFloat() / total).coerceIn(0f, 1f) * DOWNLOAD_END
                                // Only ever forward: a restart from zero must not rewind the bar.
                                if (fraction > highest) {
                                    highest = fraction
                                    onProgress(fraction)
                                }
                            }
                        }
                    }
                    // A body that stops early does not always throw at the read: depending on how the
                    // connection died the channel is simply closed, with the cause attached or with
                    // nothing at all. Both are the same event — fewer bytes than the server promised
                    // — and both must resume rather than hand a half file on to be converted.
                    requireCompleteBody(channel.closedCause, promised, written)
                    code
                }
                if (status !in 200..299) return DownloadResult(status, written)
                // 206 is normalised away — callers only ever ask whether the bytes arrived.
                return DownloadResult(200, written)
            } catch (e: IOException) {
                lastFailure = e
                stalledAttempts = if (written > resumeFrom) 0 else stalledAttempts + 1
                if (stalledAttempts >= MAX_STALLED_ATTEMPTS) break
                delay(downloadRetryDelayMs(stalledAttempts - 1, retryFloorMs))
            }
        }

        val failure = lastFailure ?: IOException("Download failed without a cause")
        throw if (failure.isStall()) DownloadStalledException(attempts, written, failure) else failure
    }

    /** Rejects a body that ended early, whether the channel reported a cause or simply closed. */
    private fun requireCompleteBody(closedCause: Throwable?, promised: Long?, written: Long) {
        if (closedCause != null) throw closedCause
        if (promised != null && written < promised) {
            throw TruncatedBodyException(promised = promised, received = written)
        }
    }

    /** `Content-Range: bytes 1234-5678/9999` — the tail is only ours if it starts at [expectedStart]. */
    private fun rangeStartsAt(response: HttpResponse, expectedStart: Long): Boolean {
        val header = response.headers[HttpHeaders.ContentRange] ?: return false
        val start = header.substringAfter("bytes ", "").substringBefore('-').trim().toLongOrNull()
        return start == expectedStart
    }

    /**
     * A download that started and then stopped, as opposed to a connection that never worked — the
     * two are told apart so the user hears "your connection keeps stopping" rather than "you are
     * offline". Ktor wraps, so the cause chain is walked, but only far enough to stay cheap.
     */
    private fun Throwable.isStall(depth: Int = 0): Boolean =
        this is SocketTimeoutException ||
            this is HttpRequestTimeoutException ||
            this is TruncatedBodyException ||
            (depth < 4 && cause?.isStall(depth + 1) == true)

    data class DownloadResult(val status: Int, val bytesWritten: Long)

    /**
     * Unpacks the entries [wanted] accepts into [into], keyed by entry name.
     *
     * Entry names are never used as paths — each file is written under a name derived from its
     * position — so a crafted archive cannot write outside [into]. Entry count and total
     * decompressed size are capped so a zip bomb fails instead of filling the disk.
     */
    fun extractEntries(zip: File, into: File, wanted: (String) -> Boolean): Map<String, File> = try {
        ZipFile(zip).use { archive ->
            val entries = archive.entries().asSequence().toList()
            if (entries.size > MAX_ARCHIVE_ENTRIES) return emptyMap()
            val result = mutableMapOf<String, File>()
            var total = 0L
            entries.filter { !it.isDirectory && wanted(it.name.substringAfterLast('/')) }
                .forEachIndexed { index, entry ->
                    val destination = File(into, "entry$index")
                    archive.getInputStream(entry).use { input ->
                        FileOutputStream(destination).use { out ->
                            val buffer = ByteArray(COPY_BUFFER_BYTES)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                total += read
                                if (total > MAX_EXTRACTED_BYTES) return emptyMap()
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    result[entry.name.substringAfterLast('/')] = destination
                }
            result
        }
    } catch (_: Exception) {
        emptyMap()
    }

    /**
     * Git's own object hash: `SHA1("blob <size>\0<content>")`, as the tree listing publishes it.
     *
     * The NUL is written as the escape `\u0000` below, never as a raw byte. A literal 0x00 in the
     * source makes git treat this whole file as binary: no diff in review, no blame, no line-level
     * merge, and `grep` skips it silently.
     */
    fun gitBlobSha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.UTF_8))
        file.inputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Every `.spb` opens with a `##` header line. */
    fun looksLikeModule(file: File): Boolean = runCatching {
        file.bufferedReader(Charsets.UTF_8).use { it.readLine() }?.startsWith("##") == true
    }.getOrDefault(false)

    /**
     * Records when a cached catalogue was fetched, and its ETag, in a `<cache>.meta` sidecar.
     *
     * Kept beside the cache rather than read from the file's own timestamp: a copied user profile, a
     * restored backup or a sync tool all rewrite mtimes, and any of those would silently make a
     * years-old listing look current.
     */
    internal object BibleIndexCache {

        private fun metaFile(cacheFile: File) = File(cacheFile.parentFile, cacheFile.name + ".meta")

        /** `fetchedAt` millis and ETag; zero and blank when there is no sidecar to read. */
        fun readMeta(cacheFile: File): Pair<Long, String> {
            val text = runCatching { metaFile(cacheFile).readText() }.getOrNull() ?: return 0L to ""
            val fetchedAt = text.substringBefore('\n').trim().toLongOrNull() ?: 0L
            return fetchedAt to text.substringAfter('\n', "").trim()
        }

        fun writeMeta(cacheFile: File, fetchedAt: Long, etag: String) {
            runCatching {
                cacheFile.parentFile?.mkdirs()
                metaFile(cacheFile).writeText("$fetchedAt\n$etag")
            }
        }
    }

    fun moveIntoPlace(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            // Atomic moves aren't supported on every filesystem, notably some network shares.
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

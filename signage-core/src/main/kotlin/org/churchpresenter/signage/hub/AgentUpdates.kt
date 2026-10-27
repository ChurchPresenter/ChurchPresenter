package org.churchpresenter.signage.hub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.ReleaseSigning
import org.churchpresenter.signage.core.SignageRelease
import org.churchpresenter.signage.core.compareVersions
import org.churchpresenter.signage.core.supportsProtocol
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream

/** The newest player release, believed because its index carries the release key's signature. */
class LatestRelease(
    val release: SignageRelease,
    /** The index exactly as published: the player checks the signature over these bytes itself. */
    val text: String,
    val signature: String,
)

/**
 * The player builds ChurchPresenter can send its screens: the latest release's index, read from
 * GitHub and checked against [ReleaseSigning], and each platform's bundle, downloaded into [cacheDir]
 * only when a screen is to be updated and kept only if it hashes to the index's entry.
 *
 * @param fetch reads a URL; the default follows GitHub's redirects to its download host.
 */
class AgentUpdates(
    private val cacheDir: Path,
    private val baseUrl: String = RELEASE_URL,
    private val publicKey: String = ReleaseSigning.PUBLIC_KEY,
    private val fetch: suspend (url: String, consume: (InputStream) -> Unit) -> Unit = ::httpGet,
) {
    private val _latest = MutableStateFlow<LatestRelease?>(null)

    /** Null until [check] has found a signed release. */
    val latest: StateFlow<LatestRelease?> = _latest.asStateFlow()

    private val downloading = Mutex()

    /** Reads the latest release's index; keeps the last good one when this fails. */
    suspend fun check(): LatestRelease? = withContext(Dispatchers.IO) {
        try {
            val text = read("$baseUrl/${SignageRelease.FILE}")
            val signature = read("$baseUrl/${SignageRelease.SIGNATURE_FILE}").trim()
            val release = ReleaseSigning.verify(text, signature, publicKey)
            if (release == null) {
                Log.warn(TAG, "The latest player release is not signed with the release key; ignoring it")
            } else {
                _latest.value = LatestRelease(release, text, signature)
            }
        } catch (e: IOException) {
            Log.info(TAG, "Could not check for player updates: ${e.message}")
        }
        _latest.value
    }

    /**
     * The release [screen] can be updated to, or null: there must be a newer one with a build for its
     * platform that still speaks this ChurchPresenter's protocol, and the player must install updates.
     */
    fun offerFor(screen: PairedScreen, features: List<String>): LatestRelease? = _latest.value?.takeIf { latest ->
        val release = latest.release
        Features.UPDATE in features &&
            screen.platform in release.assets &&
            supportsProtocol(release.protocolMin, release.protocolMax) &&
            compareVersions(release.version, screen.agentVersion) > 0
    }

    /** The latest release's bundle for [platform], downloaded and checked; throws [IOException] if it cannot be. */
    suspend fun build(platform: Platform): Path = downloading.withLock {
        withContext(Dispatchers.IO) {
            val latest = _latest.value ?: throw IOException("no release has been checked yet")
            val asset = latest.release.assets[platform] ?: throw IOException("the release has no build for $platform")
            val file = cacheDir.resolve(latest.release.version).resolve(asset.name)
            if (file.exists()) return@withContext file
            file.parent.createDirectories()
            val partial = file.resolveSibling("${asset.name}.part")
            val digest = MessageDigest.getInstance("SHA-256")
            fetch("$baseUrl/${asset.name}") { input ->
                partial.outputStream().use { out -> copyHashing(input, out, digest, asset.size) }
            }
            if (digest.digest().toHex() != asset.sha256) {
                partial.deleteIfExists()
                throw IOException("the downloaded build does not match the signed release")
            }
            Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE)
            file
        }
    }

    /** The checked bundle for [platform] if it has been downloaded, for the players to fetch. */
    fun cached(platform: Platform): Path? {
        val release = _latest.value?.release ?: return null
        val asset = release.assets[platform] ?: return null
        return cacheDir.resolve(release.version).resolve(asset.name).takeIf { it.exists() }
    }

    private suspend fun read(url: String): String {
        var text = ""
        fetch(url) { input -> text = input.readNBytes(MAX_INDEX_BYTES).toString(Charsets.UTF_8) }
        return text
    }

    companion object {
        const val RELEASE_URL = "https://github.com/ChurchPresenter/ChurchPresenter-Signage/releases/latest/download"

        /**
         * Reads releases from another place instead (`-Dchurchpresenter.signageReleaseUrl=…`), a
         * `file:` folder included: for trying an update before it is published. The index must still
         * carry the release key's signature.
         */
        const val RELEASE_URL_PROPERTY = "churchpresenter.signageReleaseUrl"
        private const val TAG = "Signage"
        private const val MAX_INDEX_BYTES = 64 * 1024
        private const val BUFFER = 64 * 1024
        private val TIMEOUT: Duration = Duration.ofSeconds(30)

        private val client: HttpClient by lazy {
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(TIMEOUT).build()
        }

        private fun httpGet(url: String, consume: (InputStream) -> Unit) {
            if (url.startsWith("file:")) return Files.newInputStream(Path.of(URI.create(url))).use(consume)
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build()
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("interrupted", e)
            }
            response.body().use { body ->
                if (response.statusCode() != HTTP_OK) throw IOException("HTTP ${response.statusCode()} for $url")
                consume(body)
            }
        }

        private const val HTTP_OK = 200

        private fun copyHashing(input: InputStream, out: OutputStream, digest: MessageDigest, size: Long) {
            val buffer = ByteArray(BUFFER)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > size) throw IOException("the download is larger than the signed release says")
                digest.update(buffer, 0, read)
                out.write(buffer, 0, read)
            }
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}

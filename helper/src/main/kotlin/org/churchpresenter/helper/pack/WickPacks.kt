package org.churchpresenter.helper.pack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.diagnostics.Log
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Where `main`'s pack is read from: the project's own repository, the host the update check already calls. */
const val WICK_PACK_URL = "https://raw.githubusercontent.com/ChurchPresenter/ChurchPresenter/main/wick-pack/pack.json"

/** A dev build reads its pack from here instead when set — a URL, or a path to a local file. */
const val WICK_PACK_URL_PROPERTY = "churchpresenter.wickPackUrl"

private const val HTTP_OK = 200
private const val DAY_MS = 24L * 60L * 60L * 1000L
private val TIMEOUT: Duration = Duration.ofSeconds(10)

/**
 * The Wick pack in use, and how it is kept fresh: fetched at most once a day, cached on disk, and used
 * from the cache while offline. With neither, Wick runs on what the app was built with — a pack only
 * ever adds to that, so a missing or bad one never breaks anything.
 *
 * @param cacheFile resolved on each call, so it follows the current `user.home`
 * @param fetch reads the pack's text from a URL, or null when it cannot
 */
class WickPackStore(
    private val cacheFile: () -> File,
    private val fetch: (String) -> String? = ::httpGet,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val pack = MutableStateFlow<WickPack?>(null)

    /** The pack in use, or null for the bundled data alone. */
    val current: StateFlow<WickPack?> = pack.asStateFlow()

    /** "bundled", or the pack's version — what a sent chat says Wick was running. */
    val versionLabel: String get() = pack.value?.version?.toString() ?: "bundled"

    /**
     * Loads the cached pack, then fetches a fresh one when the cache is a day old (or a dev URL is set).
     * Off the calling thread; never throws.
     */
    suspend fun refresh(appVersion: String, url: String = System.getProperty(WICK_PACK_URL_PROPERTY) ?: WICK_PACK_URL) =
        withContext(Dispatchers.IO) {
            runCatching { refreshNow(appVersion, url) }
                .onFailure { Log.warn("Wick", "Could not refresh the Wick pack: $it") }
        }

    internal fun refreshNow(appVersion: String, url: String) {
        val cache = cacheFile()
        val cached = cache.takeIf { it.isFile && it.length() <= MAX_PACK_BYTES }?.readText()
        cached?.let { parseWickPack(it, appVersion) }?.let { pack.value = it }
        val overridden = url != WICK_PACK_URL
        val fresh = cache.isFile && nowMillis() - cache.lastModified() < DAY_MS
        if (fresh && !overridden) return
        val text = fetch(url) ?: return
        val parsed = parseWickPack(text, appVersion) ?: return
        pack.value = parsed
        cache.parentFile?.mkdirs()
        cache.writeTextAtomically(text)
    }
}

/** The app-wide pack, cached in `~/.churchpresenter/` beside the app's other state. */
val WickPacks = WickPackStore({ File(System.getProperty("user.home"), ".churchpresenter/wick-pack.json") })

/** [url]'s body over HTTPS, or a local file's text for a dev URL; null on any failure or past the size cap. */
internal fun httpGet(url: String): String? {
    if (!url.startsWith("https://")) {
        val file = if (url.startsWith("file:")) File(URI(url)) else File(url)
        return file.takeIf { it.isFile && it.length() <= MAX_PACK_BYTES }?.readText()
    }
    val client = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build()
    val request = HttpRequest.newBuilder(URI(url)).timeout(TIMEOUT).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    return response.body().takeIf { response.statusCode() == HTTP_OK && it.length <= MAX_PACK_BYTES }
}

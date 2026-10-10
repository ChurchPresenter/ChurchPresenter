package org.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants

/**
 * The REST half of Instance Link: the primary's files and catalogues, fetched on demand while the
 * WebSocket carries what is live. Shares the follower's HTTP client; [target] is told where the
 * primary is on every connect, and until then every fetch answers null with `not_connected`.
 */
internal class InstanceLinkHttpFetches(
    private val httpClient: HttpClient,
    private val json: Json,
) : InstanceLinkFetches {
    // Set on connect() so a fetch made on demand, outside the connect loop, reaches the same primary.
    @Volatile private var host: String = ""

    @Volatile private var port: Int = 0

    @Volatile private var apiKey: String = ""

    /** The primary every fetch from now on goes to. */
    fun target(host: String, port: Int, apiKey: String) {
        this.host = host
        this.port = port
        this.apiKey = apiKey
    }

    /**
     * Builds the streaming URL for one of the primary's local media files (PartialContent, so
     * the player can seek) — used to mirror MEDIA live state. Null while not connected.
     */
    override fun mediaStreamUrl(mediaId: String): String? {
        if (host.isEmpty()) return null
        val keyParam = if (apiKey.isNotEmpty()) "?${Constants.QUERY_PARAM_API_KEY}=$apiKey" else ""
        return "http://$host:$port${Constants.ENDPOINT_MEDIA_STREAM}/$mediaId$keyParam"
    }

    /** Logs the outcome of one `fetch*` call below — [kind] identifies which one (e.g. "bible_file",
     *  "picture_bytes"), symmetric with the primary's `rest_request` log line for the same hit. */
    private fun logFetch(kind: String, success: Boolean, status: Int? = null, reason: String? = null) {
        InstanceLinkLogger.log(
            InstanceLinkLogSide.FOLLOWER, "fetch_result",
            mapOf("kind" to kind, "success" to success, "status" to status, "reason" to reason)
        )
    }

    /**
     * Fetches full song detail (sections/lyrics) from the primary on demand — the catalog broadcast
     * only carries metadata (title/number/tune/author), so this is called lazily when a song is
     * actually selected rather than for the whole library upfront (which could mean thousands of
     * requests for a large library).
     */
    override suspend fun fetchSongDetail(number: String, songbook: String): SongDetailDto? {
        if (host.isEmpty()) {
            logFetch("song_detail", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$host:$port${Constants.ENDPOINT_SONGS}/$number") {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
                if (songbook.isNotEmpty()) parameter(Constants.QUERY_PARAM_SONGBOOK, songbook)
            }
            if (!response.status.isSuccess()) {
                logFetch("song_detail", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("song_detail", success = true, status = response.status.value)
            json.decodeFromString(SongDetailDto.serializer(), response.bodyAsText())
        }.onFailure { e -> logFetch("song_detail", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches one picture's raw bytes from the primary — used to mirror a live picture (resolved
     *  via [LiveStateDto.pictureFolderId]/[LiveStateDto.pictureIndex]) without a local copy of it. */
    override suspend fun fetchPictureImageBytes(folderId: String, index: Int): ByteArray? {
        if (host.isEmpty()) {
            logFetch("picture_bytes", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get(
                "http://$host:$port${Constants.ENDPOINT_PICTURES}/$folderId/images/$index"
            ) {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("picture_bytes", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("picture_bytes", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("picture_bytes", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches one presentation slide's raw bytes from the primary — mirrors [RemotePresentationSlide]
     *  (from the existing presentation_slide_changed broadcast) without a local copy of the file. */
    override suspend fun fetchPresentationSlideBytes(id: String, index: Int): ByteArray? {
        if (host.isEmpty()) {
            logFetch("presentation_slide", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get(
                "http://$host:$port${Constants.ENDPOINT_PRESENTATIONS}/$id/slides/$index"
            ) {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("presentation_slide", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("presentation_slide", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("presentation_slide", success = false, reason = e.message) }.getOrNull()
    }

    /**
     * Downloads the primary's raw .spb bible file bytes — the caller loads it through the same
     * Bible.loadFromSpb() used for local files instead of reimplementing that engine against the API.
     */
    override suspend fun fetchBibleFile(): ByteArray? {
        if (host.isEmpty()) {
            logFetch("bible_file", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$host:$port${Constants.ENDPOINT_BIBLE_FILE}") {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("bible_file", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("bible_file", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("bible_file", success = false, reason = e.message) }.getOrNull()
    }

    /** Downloads the primary's raw secondary .spb bible file — only used when the follower opted in
     *  to mirroring the primary's secondary bible instead of keeping its own local one. */
    override suspend fun fetchSecondaryBibleFile(): ByteArray? {
        if (host.isEmpty()) {
            logFetch("secondary_bible_file", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get(
                "http://$host:$port${Constants.ENDPOINT_BIBLE_FILE}/secondary"
            ) {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch(
                    "secondary_bible_file",
                    success = false,
                    status = response.status.value,
                    reason = "http_status"
                )
                return null
            }
            logFetch("secondary_bible_file", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("secondary_bible_file", success = false, reason = e.message) }.getOrNull()
    }

    /** Downloads every Bible module advertised by the primary, preserving manifest order. */
    override suspend fun fetchBibleTranslations(): List<Pair<String, ByteArray>> {
        if (host.isEmpty()) return emptyList()
        return runCatching {
            val manifestResponse = httpClient.get(
                "http://$host:$port${Constants.ENDPOINT_BIBLE_FILE}/translations"
            ) {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
            }
            if (!manifestResponse.status.isSuccess()) return emptyList()
            val names = Json.decodeFromString<List<String>>(manifestResponse.bodyAsText())
            names.mapIndexedNotNull { index, name ->
                val response = httpClient.get(
                    "http://$host:$port${Constants.ENDPOINT_BIBLE_FILE}/translation/$index"
                ) {
                    if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
                }
                if (response.status.isSuccess()) name to response.readRawBytes() else null
            }
        }.onFailure { error ->
            logFetch("bible_translations", success = false, reason = error.message)
        }.getOrDefault(emptyList())
    }

    /** Fetches one lower-third preset's raw Lottie JSON by name — see [Constants.ENDPOINT_LOWER_THIRDS]. */
    override suspend fun fetchLowerThirdJson(name: String): ByteArray? {
        if (host.isEmpty()) {
            logFetch("lower_third_json", success = false, reason = "not_connected")
            return null
        }
        // Path segment, not a query param: URLEncoder turns spaces into "+", which Ktor's route
        // parameter decoding does NOT turn back into a space (that only happens for query/form
        // encoding) — swap it for "%20" so a name with spaces still resolves on the server.
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return runCatching {
            val response = httpClient.get(
                "http://$host:$port${Constants.ENDPOINT_LOWER_THIRDS}/$encodedName/json"
            ) {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("lower_third_json", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("lower_third_json", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("lower_third_json", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches the primary's current background settings — only used when the follower opted in to
     *  mirroring backgrounds (InstanceLinkSettings.mirrorBackgrounds). Image/video fields are still
     *  the primary's own local file paths; use [fetchBackgroundAsset] (keyed by slot) for bytes. */
    override suspend fun fetchBackgroundSettings(): BackgroundSettings? {
        if (host.isEmpty()) {
            logFetch("background_settings", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get("http://$host:$port${Constants.ENDPOINT_BACKGROUNDS}") {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
            }
            if (!response.status.isSuccess()) {
                logFetch("background_settings", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("background_settings", success = true, status = response.status.value)
            json.decodeFromString(BackgroundSettings.serializer(), response.bodyAsText())
        }.onFailure { e -> logFetch("background_settings", success = false, reason = e.message) }.getOrNull()
    }

    /** Fetches one background slot's raw image/video bytes by slot name — see
     *  [Constants.BACKGROUND_SLOT_DEFAULT] and siblings for the shared slot vocabulary. */
    override suspend fun fetchBackgroundAsset(slot: String, isVideo: Boolean): ByteArray? {
        if (host.isEmpty()) {
            logFetch("background_asset", success = false, reason = "not_connected")
            return null
        }
        return runCatching {
            val response = httpClient.get(
                "http://$host:$port${Constants.ENDPOINT_BACKGROUNDS}/asset/$slot"
            ) {
                if (apiKey.isNotEmpty()) header(Constants.HEADER_API_KEY, apiKey)
                parameter("type", if (isVideo) "video" else "image")
            }
            if (!response.status.isSuccess()) {
                logFetch("background_asset", success = false, status = response.status.value, reason = "http_status")
                return null
            }
            logFetch("background_asset", success = true, status = response.status.value)
            response.readRawBytes()
        }.onFailure { e -> logFetch("background_asset", success = false, reason = e.message) }.getOrNull()
    }
}

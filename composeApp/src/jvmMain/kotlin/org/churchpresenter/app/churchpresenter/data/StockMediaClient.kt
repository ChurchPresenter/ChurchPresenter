package org.churchpresenter.app.churchpresenter.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.io.File

/**
 * Search and download free stock photos/videos from Pexels and Pixabay for use as
 * presentation backgrounds. Each user supplies their own free API key (entered in
 * Background settings) — no key ships with the app, so no shared rate limit.
 */
object StockMediaClient {

    enum class StockSource { PEXELS, PIXABAY }
    enum class StockMediaType { PHOTO, VIDEO }

    data class StockMediaItem(
        val id: String,
        val source: StockSource,
        val isVideo: Boolean,
        val thumbnailUrl: String,
        val downloadUrl: String
    )

    sealed interface SearchOutcome {
        data class Success(val items: List<StockMediaItem>, val hasMore: Boolean) : SearchOutcome
        data object InvalidKey : SearchOutcome
        data object RateLimited : SearchOutcome
        data object NetworkError : SearchOutcome
        data object Failure : SearchOutcome
    }

    sealed interface DownloadOutcome {
        data class Success(val file: File) : DownloadOutcome
        data object NetworkError : DownloadOutcome
        data object Failure : DownloadOutcome
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val defaultHttp by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 8_000
            }
        }
    }

    /** Test seam: when set, requests go here instead of the real network client. Null in production. */
    internal var httpOverride: HttpClient? = null

    private val http: HttpClient get() = httpOverride ?: defaultHttp

    private val defaultDownloadDir = File(System.getProperty("user.home"), ".churchpresenter/stock-backgrounds")

    /** Test seam: when set, downloads land here instead of under the user's home. Null in production. */
    internal var downloadDirOverride: File? = null

    private val downloadDir: File get() = downloadDirOverride ?: defaultDownloadDir

    // --- Pexels DTOs ---

    @Serializable
    private data class PexelsPhotoSrc(val original: String, val large2x: String)

    @Serializable
    private data class PexelsPhoto(val id: Long, val src: PexelsPhotoSrc)

    @Serializable
    private data class PexelsPhotoResponse(
        val photos: List<PexelsPhoto> = emptyList(),
        val next_page: String? = null
    )

    @Serializable
    private data class PexelsVideoFile(val link: String, val quality: String? = null, val file_type: String? = null, val width: Int? = null)

    @Serializable
    private data class PexelsVideo(val id: Long, val image: String, val video_files: List<PexelsVideoFile> = emptyList())

    @Serializable
    private data class PexelsVideoResponse(
        val videos: List<PexelsVideo> = emptyList(),
        val next_page: String? = null
    )

    // --- Pixabay DTOs ---

    @Serializable
    private data class PixabayPhoto(val id: Long, val previewURL: String, val largeImageURL: String)

    @Serializable
    private data class PixabayPhotoResponse(
        val hits: List<PixabayPhoto> = emptyList(),
        val totalHits: Int = 0
    )

    @Serializable
    private data class PixabayVideoFile(val url: String, val thumbnail: String? = null)

    @Serializable
    private data class PixabayVideoFiles(
        val large: PixabayVideoFile? = null,
        val medium: PixabayVideoFile? = null,
        val small: PixabayVideoFile? = null,
        val tiny: PixabayVideoFile? = null
    )

    @Serializable
    private data class PixabayVideo(val id: Long, val videos: PixabayVideoFiles, val picture_id: String? = null)

    @Serializable
    private data class PixabayVideoResponse(
        val hits: List<PixabayVideo> = emptyList(),
        val totalHits: Int = 0
    )

    suspend fun search(
        source: StockSource,
        apiKey: String,
        mediaType: StockMediaType,
        query: String,
        page: Int
    ): SearchOutcome = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext SearchOutcome.InvalidKey
        try {
            val response = when (source) {
                StockSource.PEXELS -> when (mediaType) {
                    StockMediaType.PHOTO -> http.get("https://api.pexels.com/v1/search") {
                        header("Authorization", apiKey)
                        parameter("query", query)
                        parameter("per_page", PER_PAGE)
                        parameter("page", page)
                        parameter("orientation", "landscape")
                    }
                    StockMediaType.VIDEO -> http.get("https://api.pexels.com/videos/search") {
                        header("Authorization", apiKey)
                        parameter("query", query)
                        parameter("per_page", PER_PAGE)
                        parameter("page", page)
                        parameter("orientation", "landscape")
                    }
                }
                StockSource.PIXABAY -> when (mediaType) {
                    StockMediaType.PHOTO -> http.get("https://pixabay.com/api/") {
                        parameter("key", apiKey)
                        parameter("q", query)
                        parameter("image_type", "photo")
                        parameter("orientation", "horizontal")
                        parameter("per_page", PER_PAGE)
                        parameter("page", page)
                    }
                    StockMediaType.VIDEO -> http.get("https://pixabay.com/api/videos/") {
                        parameter("key", apiKey)
                        parameter("q", query)
                        parameter("orientation", "horizontal")
                        parameter("per_page", PER_PAGE)
                        parameter("page", page)
                    }
                }
            }

            if (response.status.value == 401 || response.status.value == 403) {
                return@withContext SearchOutcome.InvalidKey
            }
            if (response.status.value == 429) {
                return@withContext SearchOutcome.RateLimited
            }
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Stock media search returned HTTP ${response.status.value} (${source.name.lowercase()})",
                    tags = mapOf(
                        "subsystem" to "stock_media",
                        "source" to source.name.lowercase(),
                        "media_type" to mediaType.name.lowercase()
                    )
                )
                return@withContext SearchOutcome.Failure
            }

            parseSearchResponse(source, mediaType, response)
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Stock media search failed (${source.name.lowercase()})",
                throwable = e,
                tags = mapOf(
                    "subsystem" to "stock_media",
                    "source" to source.name.lowercase(),
                    "media_type" to mediaType.name.lowercase()
                )
            )
            SearchOutcome.NetworkError
        }
    }

    private suspend fun parseSearchResponse(
        source: StockSource,
        mediaType: StockMediaType,
        response: HttpResponse
    ): SearchOutcome {
        val body: String = response.body()
        return when (source) {
            StockSource.PEXELS -> when (mediaType) {
                StockMediaType.PHOTO -> {
                    val parsed = json.decodeFromString(PexelsPhotoResponse.serializer(), body)
                    SearchOutcome.Success(
                        items = parsed.photos.map {
                            StockMediaItem(
                                id = it.id.toString(),
                                source = source,
                                isVideo = false,
                                thumbnailUrl = it.src.large2x,
                                downloadUrl = it.src.original
                            )
                        },
                        hasMore = parsed.next_page != null
                    )
                }
                StockMediaType.VIDEO -> {
                    val parsed = json.decodeFromString(PexelsVideoResponse.serializer(), body)
                    SearchOutcome.Success(
                        items = parsed.videos.mapNotNull { video ->
                            val file = video.video_files.firstOrNull { it.quality == "hd" }
                                ?: video.video_files.firstOrNull { it.file_type == "video/mp4" }
                                ?: video.video_files.firstOrNull()
                            file?.let {
                                StockMediaItem(
                                    id = video.id.toString(),
                                    source = source,
                                    isVideo = true,
                                    thumbnailUrl = video.image,
                                    downloadUrl = it.link
                                )
                            }
                        },
                        hasMore = parsed.next_page != null
                    )
                }
            }
            StockSource.PIXABAY -> when (mediaType) {
                StockMediaType.PHOTO -> {
                    val parsed = json.decodeFromString(PixabayPhotoResponse.serializer(), body)
                    SearchOutcome.Success(
                        items = parsed.hits.map {
                            StockMediaItem(
                                id = it.id.toString(),
                                source = source,
                                isVideo = false,
                                thumbnailUrl = it.previewURL,
                                downloadUrl = it.largeImageURL
                            )
                        },
                        hasMore = parsed.hits.size >= PER_PAGE
                    )
                }
                StockMediaType.VIDEO -> {
                    val parsed = json.decodeFromString(PixabayVideoResponse.serializer(), body)
                    SearchOutcome.Success(
                        items = parsed.hits.mapNotNull { video ->
                            val file = video.videos.medium ?: video.videos.small ?: video.videos.large ?: video.videos.tiny
                            file?.let {
                                StockMediaItem(
                                    id = video.id.toString(),
                                    source = source,
                                    isVideo = true,
                                    thumbnailUrl = it.thumbnail ?: it.url,
                                    downloadUrl = it.url
                                )
                            }
                        },
                        hasMore = parsed.hits.size >= PER_PAGE
                    )
                }
            }
        }
    }

    /** Fetches raw bytes for a thumbnail preview; returns null on any failure. */
    suspend fun fetchThumbnailBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val response = http.get(url)
            if (response.status.value in 200..299) response.body() else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun download(item: StockMediaItem): DownloadOutcome = withContext(Dispatchers.IO) {
        try {
            val response = http.get(item.downloadUrl)
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Stock media download returned HTTP ${response.status.value} (${item.source.name.lowercase()})",
                    tags = mapOf("subsystem" to "stock_media", "source" to item.source.name.lowercase())
                )
                return@withContext DownloadOutcome.Failure
            }
            val bytes: ByteArray = response.body()
            val extension = if (item.isVideo) "mp4" else "jpg"
            downloadDir.mkdirs()
            val destFile = File(downloadDir, "${item.source.name.lowercase()}_${item.id}.$extension")
            destFile.writeBytes(bytes)
            DownloadOutcome.Success(destFile)
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Stock media download failed (${item.source.name.lowercase()})",
                throwable = e,
                tags = mapOf("subsystem" to "stock_media", "source" to item.source.name.lowercase())
            )
            DownloadOutcome.NetworkError
        }
    }

    private const val PER_PAGE = 24
}

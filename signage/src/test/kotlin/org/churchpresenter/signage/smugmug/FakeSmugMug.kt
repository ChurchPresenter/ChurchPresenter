package org.churchpresenter.signage.smugmug

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.writeText

/** A gallery the fake account has: its key, name, photos, and how a visitor sees it. */
data class FakeAlbum(
    val key: String,
    val name: String,
    val photos: List<String>,
    /** What `Privacy` says; null as SmugMug sends a visitor, who sees only what is public. */
    val privacy: String? = null,
    val securityType: String = "None",
    val updated: String = "2026-11-01T17:23:16+00:00",
    /** Photos SmugMug lists but a screen cannot show: videos, hidden ones, ones still processing. */
    val unshown: List<Pair<String, String>> = emptyList(),
)

/**
 * A SmugMug account answering as the real API does (the shapes of its replies, read from it):
 * a custom domain's lookup names the account only in links to it, galleries and photos come in pages
 * with a `NextPage` link, and a photo's sizes are asked for under its first version (`-0`). Counts
 * what it was asked, and fails on demand.
 */
class FakeSmugMug(
    var albums: List<FakeAlbum>,
    private val nickname: String = "Grace",
    private val domain: String = "photos.example.org",
    private val pageSize: Int = 2,
) : SmugMugHttp {
    /** Every URL asked for, in order. */
    val asked = mutableListOf<String>()

    /** Every photo downloaded, by its URL. */
    val downloads = mutableListOf<String>()

    /** When set, every request fails with it. */
    var failure: IOException? = null

    /** Photo keys whose download fails. */
    val brokenPhotos = mutableSetOf<String>()

    /** When set, every download waits for it: a pick held halfway. */
    @Volatile
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun json(url: String): JsonObject {
        asked += url
        failure?.let { throw it }
        val uri = URI(url)
        val query = query(uri)
        val path = uri.rawPath
        return when {
            path.endsWith("!weburilookup") -> lookup(query.getValue("WebUri"))
            path.endsWith("!albums") -> page(albumsReply(), "Album", path, query)
            path.endsWith("!images") -> {
                val key = path.substringAfter("/album/").substringBefore('!')
                val album = albums.firstOrNull { it.key == key } ?: throw SmugMugStatusException(NOT_FOUND)
                page(imagesReply(album), "AlbumImage", path, query)
            }
            path.endsWith("!sizedetails") -> sizes(path.substringAfter("/image/").substringBefore('!'))
            else -> throw SmugMugStatusException(NOT_FOUND)
        }
    }

    override suspend fun download(url: String, to: Path) {
        hold?.await()
        downloads += url
        failure?.let { throw it }
        val key = url.substringAfter("/i-").substringBefore('/')
        if (key in brokenPhotos) throw IOException("broken photo")
        to.writeText("photo $key")
    }

    private fun lookup(address: String): JsonObject {
        val host = URI(address).host
        // SmugMug answers a `www.` custom domain with a redirect the client does not follow.
        if (host == "www.$domain") throw SmugMugStatusException(MOVED)
        if (host != domain) throw SmugMugStatusException(NOT_FOUND)
        return buildJsonObject {
            putJsonObject("Response") {
                put("Locator", "Folder")
                putJsonObject("Folder") {
                    put("Name", "")
                    put("Uri", "/api/v2/folder/user/$nickname")
                    putJsonObject("Uris") {
                        putJsonObject("User") { put("Uri", "/api/v2/user/$nickname") }
                    }
                }
            }
        }
    }

    private fun albumsReply(): List<JsonObject> = albums.map { album ->
        buildJsonObject {
            put("AlbumKey", album.key)
            put("Name", album.name)
            put("Privacy", album.privacy?.let(::JsonPrimitive) ?: JsonNull)
            put("SecurityType", album.securityType)
            put("PasswordHint", "")
            put("ImageCount", album.photos.size)
            put("ImagesLastUpdated", album.updated)
        }
    }

    private fun imagesReply(album: FakeAlbum): List<JsonObject> =
        album.photos.map { image(it) } + album.unshown.map { (key, why) -> image(key, why) }

    private fun image(key: String, flag: String? = null) = buildJsonObject {
        put("ImageKey", key)
        put("IsVideo", flag == "video")
        put("Hidden", flag == "hidden")
        put("Processing", flag == "processing")
        put("Format", "JPG")
    }

    private fun sizes(versioned: String): JsonObject {
        if (!versioned.endsWith("-0")) throw SmugMugStatusException(MOVED)
        val key = versioned.removeSuffix("-0")
        return buildJsonObject {
            putJsonObject("Response") {
                putJsonObject("ImageSizeDetails") {
                    put("ImageUrlTemplate", "https://photos.smugmug.com/i-$key/0/#size#/$key-#size#.jpg")
                    for ((name, width, height) in SIZES) {
                        putJsonObject(name) {
                            put("Url", "https://photos.smugmug.com/i-$key/0/$name/$key.jpg")
                            put("Width", width)
                            put("Height", height)
                            put("Ext", "jpg")
                        }
                    }
                }
            }
        }
    }

    /** [all] in pages of [pageSize], as `start` and `count` ask, with a `NextPage` link while more remain. */
    private fun page(all: List<JsonObject>, name: String, path: String, query: Map<String, String>): JsonObject {
        val start = query["start"]?.toInt() ?: 1
        val shown = all.drop(start - 1).take(pageSize)
        val next = start + pageSize
        return buildJsonObject {
            putJsonObject("Response") {
                putJsonArray(name) { shown.forEach { add(it) } }
                putJsonObject("Pages") {
                    put("Total", all.size)
                    put("Start", start)
                    if (next <= all.size) put("NextPage", "$path?count=$pageSize&start=$next")
                }
            }
        }
    }

    private fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&').filter { '=' in it }
        .associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            name to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }

    private companion object {
        const val NOT_FOUND = 404
        const val MOVED = 301

        /** The sizes SmugMug offers a 3:2 photo, the original among them. */
        val SIZES = listOf(
            Triple("ImageSizeLarge", 800, 533),
            Triple("ImageSizeX3Large", 1600, 1067),
            Triple("ImageSizeX4Large", 2048, 1365),
            Triple("ImageSize4K", 3840, 2560),
            Triple("ImageSize5K", 5120, 3413),
            Triple("ImageSizeOriginal", 5760, 3840),
        )
    }
}

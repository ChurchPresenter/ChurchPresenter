package org.churchpresenter.signage.smugmug

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.random.Random

/** A gallery on a SmugMug account, as much as choosing it needs. */
@Serializable
data class SmugMugAlbum(
    val key: String,
    val name: String,
    /** `Public`, `Unlisted` or `Private`. */
    val privacy: String,
    /** `None`, `Password` or `GrantAccess`. */
    val securityType: String = "None",
    val passwordHint: String = "",
    val imageCount: Int = 0,
    /** When its photos last changed, as SmugMug writes it: a changed value means listing them again. */
    val imagesLastUpdated: String = "",
)

/** A SmugMug account as a slideshow sees it: its [nickname] and every gallery a visitor can list. */
data class SmugMugSite(val nickname: String, val albums: List<SmugMugAlbum>)

/** A size of one photo SmugMug can send: where, and how big. */
data class SmugMugSize(val url: String, val width: Int, val height: Int, val extension: String)

/**
 * Reads a SmugMug account through its API (v2) as a visitor who is not signed in, with [apiKey]: only
 * what anyone can see on the site. Every request carries the key, and nothing here logs a URL.
 */
class SmugMugClient(private val http: SmugMugHttp, private val apiKey: String) {

    /**
     * The account's nickname for [site]: a nickname as typed, a `*.smugmug.com` address, or the
     * account's own domain (looked up on SmugMug).
     */
    suspend fun nickname(site: String): String {
        val typed = site.trim()
        if (typed.isEmpty()) throw IOException("No SmugMug site")
        val host = hostOf(typed) ?: return typed
        val label = host.substringBefore('.')
        if (host.endsWith(".$SMUGMUG_HOST") && label !in NOT_ACCOUNTS) return label
        // The address without `www.` first: SmugMug sends a lookup of a `www.` custom domain elsewhere.
        val addresses = listOf("https://$host", if ("://" in typed) typed else "https://$typed").distinct()
        var failure: IOException? = null
        for (address in addresses) {
            try {
                findNickname(http.json(api("/api/v2!weburilookup", "WebUri" to address)))?.let { return it }
            } catch (e: IOException) {
                failure = e
            }
        }
        // SmugMug's own "not found" (or a redirect it sends elsewhere) means it does not know the site;
        // anything else, such as no internet, is said as it is.
        val unknown = failure == null || (failure is SmugMugStatusException && failure.status in NOT_KNOWN)
        throw if (unknown) IOException("SmugMug does not know $host") else failure
    }

    /** Every gallery on [nickname]'s account that a visitor can list. */
    suspend fun albums(nickname: String): List<SmugMugAlbum> =
        pages(api("/api/v2/user/${encode(nickname)}!albums", "count" to PAGE)) { response ->
            response["Album"].objects().mapNotNull { album ->
                val key = album.text("AlbumKey") ?: return@mapNotNull null
                SmugMugAlbum(
                    key = key,
                    name = album.text("Name") ?: album.text("Title").orEmpty(),
                    privacy = album.text("Privacy") ?: "Public",
                    securityType = album.text("SecurityType") ?: "None",
                    passwordHint = album.text("PasswordHint").orEmpty(),
                    imageCount = album["ImageCount"]?.jsonPrimitive?.intOrNull ?: 0,
                    imagesLastUpdated = album.text("ImagesLastUpdated") ?: album.text("LastUpdated").orEmpty(),
                )
            }
        }

    /** The keys of [album]'s photos a screen can show: no videos, nothing hidden or still processing. */
    suspend fun photoKeys(album: SmugMugAlbum): List<String> =
        pages(api("/api/v2/album/${encode(album.key)}!images", "count" to PAGE)) { response ->
            response["AlbumImage"].objects().mapNotNull { image ->
                val skip = image.flag("IsVideo") || image.flag("Hidden") || image.flag("Processing")
                image.text("ImageKey")?.takeUnless { skip }
            }
        }

    /** The size of photo [imageKey] a screen gets ([SmugMugPick.bestSize]); null when there is none. */
    suspend fun size(imageKey: String): SmugMugSize? {
        // The photo's first version (`-0`): what SmugMug redirects a bare key to.
        val reply = http.json(api("/api/v2/image/${encode(imageKey)}-0!sizedetails"))
        val details = reply.response()["ImageSizeDetails"] as? JsonObject ?: return null
        return SmugMugPick.bestSize(details)
    }

    /** Every page of a list that starts at [first], each read by [read]. */
    private suspend fun <T> pages(first: String, read: (JsonObject) -> List<T>): List<T> {
        val all = mutableListOf<T>()
        var next: String? = first
        var pagesRead = 0
        while (next != null && pagesRead < MAX_PAGES) {
            val response = http.json(next).response()
            all += read(response)
            pagesRead++
            next = (response["Pages"] as? JsonObject)?.text("NextPage")?.let { api(it) }
        }
        return all
    }

    /** [path] (a path, or a whole URL SmugMug gave) on the API, with [query] and the key added. */
    private fun api(path: String, vararg query: Pair<String, String>): String {
        val base = if (path.startsWith("http")) path else "$API_ROOT$path"
        val params = (query.toList() + ("APIKey" to apiKey))
            .filterNot { (name, _) -> "$name=" in base }
            .joinToString("&") { (name, value) -> "$name=${encode(value)}" }
        return when {
            params.isEmpty() -> base
            '?' in base -> "$base&$params"
            else -> "$base?$params"
        }
    }

    private companion object {
        const val API_ROOT = "https://api.smugmug.com"
        const val SMUGMUG_HOST = "smugmug.com"
        val NOT_ACCOUNTS = setOf("www", "api", "secure", "photos")
        const val PAGE = "100"
        const val NOT_FOUND = 404

        /** What SmugMug answers a site it does not know with: not found, or a redirect elsewhere. */
        val NOT_KNOWN = setOf(NOT_FOUND, 301, 302)

        /** The account a lookup found, in a link to it: `/api/v2/user/{nickname}`. */
        val USER_URI = Regex("""/api/v2/user/([^/!?#]+)""")
        const val MAX_PAGES = 200

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

        /** [typed]'s host when it is an address (has a dot or a scheme); null for a plain nickname. */
        fun hostOf(typed: String): String? {
            if ('.' !in typed && "://" !in typed) return null
            val address = if ("://" in typed) typed else "https://$typed"
            return try {
                URI(address).host?.lowercase(Locale.ROOT)?.removePrefix("www.")
            } catch (_: URISyntaxException) {
                null
            }
        }

        /**
         * The account a lookup found: a `NickName` anywhere in [element], or the nickname in a link to
         * the account (`/api/v2/user/BCOS`), which is how a lookup of a custom domain names it.
         */
        fun findNickname(element: JsonElement?): String? =
            findIn(element) { it.text("NickName")?.takeIf(String::isNotBlank) }
                ?: findIn(element) { obj -> obj.text("Uri")?.let { USER_URI.find(it)?.groupValues?.get(1) } }

        fun findIn(element: JsonElement?, pick: (JsonObject) -> String?): String? = when (element) {
            is JsonObject -> pick(element) ?: element.values.firstNotNullOfOrNull { findIn(it, pick) }
            is JsonArray -> element.firstNotNullOfOrNull { findIn(it, pick) }
            else -> null
        }

        fun JsonObject.response(): JsonObject = this["Response"] as? JsonObject ?: JsonObject(emptyMap())

        /** [name]'s text; null when it is missing or JSON's `null`, as a visitor is sent `Privacy`. */
        fun JsonObject.text(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

        fun JsonObject.flag(name: String): Boolean = (this[name] as? JsonPrimitive)?.booleanOrNull == true

        fun JsonElement?.objects(): List<JsonObject> =
            (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }
}

/** The choices the slideshow makes, apart from SmugMug so they can be tested on their own. */
object SmugMugPick {
    /**
     * The galleries a slideshow takes photos from: public ones (and unlisted with [includeUnlisted]),
     * none behind a password, none empty, none in [excluded].
     */
    fun albumsToUse(albums: List<SmugMugAlbum>, includeUnlisted: Boolean, excluded: Set<String>): List<SmugMugAlbum> =
        albums.filter { album ->
            val open = album.privacy == "Public" || (includeUnlisted && album.privacy == "Unlisted")
            open && album.securityType != "Password" && album.passwordHint.isBlank() &&
                album.imageCount > 0 && album.key !in excluded
        }

    /** [count] of [keys] at random, each at most once, every photo as likely as any other. */
    fun pick(keys: List<String>, count: Int, random: Random): List<String> =
        keys.distinct().shuffled(random).take(count.coerceAtLeast(0))

    /**
     * The size a screen gets from [details] (`ImageSizeDetails`): the smallest whose longer side fills
     * a 1080p screen ([TARGET_SIDE]), so a set of hundreds stays small; the largest up to [MAX_SIDE]
     * when none does. Never the original; null when there is no size.
     */
    fun bestSize(details: JsonObject): SmugMugSize? {
        val sizes = details.entries
            .filter { (name, _) -> name.startsWith("ImageSize") && name != "ImageSizeOriginal" }
            .mapNotNull { (_, value) -> (value as? JsonObject)?.let(::sizeOf) }
            .filter { maxOf(it.width, it.height) <= MAX_SIDE }
        return sizes.filter { maxOf(it.width, it.height) >= TARGET_SIDE }.minByOrNull { it.width.toLong() * it.height }
            ?: sizes.maxByOrNull { it.width.toLong() * it.height }
    }

    private fun sizeOf(size: JsonObject): SmugMugSize? {
        val url = (size["Url"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: return null
        val width = size["Width"]?.jsonPrimitive?.intOrNull ?: return null
        val height = size["Height"]?.jsonPrimitive?.intOrNull ?: return null
        val extension = (size["Ext"] as? JsonPrimitive)?.content
            ?: url.substringAfterLast('/').substringAfterLast('.', "jpg").substringBefore('?')
        return SmugMugSize(url, width, height, extension.lowercase(Locale.ROOT))
    }

    /** A 4K screen's longer side: no larger photo is ever fetched. */
    const val MAX_SIDE = 3840

    /** A 1080p screen's longer side: the smallest size that fills it is what is fetched. */
    const val TARGET_SIDE = 1920
}

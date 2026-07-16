package org.churchpresenter.app.churchpresenter.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.io.File

/**
 * OAuth2 + REST client for Planning Center Online's Services/People APIs. Each church registers
 * its own free PCO Developer OAuth application and supplies its own client id/secret (Settings ->
 * Planning Center) — no credentials ship with the app, matching the existing Pexels/Pixabay
 * bring-your-own-key pattern in [StockMediaClient]. One-way pull only; nothing is written back to
 * Planning Center.
 */
object PlanningCenterClient {

    private const val AUTHORIZE_URL = "https://api.planningcenteronline.com/oauth/authorize"
    private const val TOKEN_URL = "https://api.planningcenteronline.com/oauth/token"
    private const val ME_URL = "https://api.planningcenteronline.com/people/v2/me"

    /** "people" is needed for [getCurrentPerson]; "services" for the plan/song import phases. */
    private const val OAUTH_SCOPE = "people services"

    data class TokenSet(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochMs: Long
    )

    data class ConnectedPerson(val displayName: String)

    sealed interface TokenOutcome {
        data class Success(val tokens: TokenSet) : TokenOutcome
        data object InvalidCredentials : TokenOutcome
        data object NetworkError : TokenOutcome
        data object Failure : TokenOutcome
    }

    sealed interface PersonOutcome {
        data class Success(val person: ConnectedPerson) : PersonOutcome
        data object Unauthorized : PersonOutcome
        data object NetworkError : PersonOutcome
        data object Failure : PersonOutcome
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val http by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 8_000
            }
        }
    }

    fun redirectUri(): String = "http://127.0.0.1:${Constants.PLANNING_CENTER_OAUTH_PORT}/callback"

    fun buildAuthorizationUrl(clientId: String): String {
        val redirect = redirectUri().encodeURLParameter()
        val scope = OAUTH_SCOPE.encodeURLParameter()
        return "$AUTHORIZE_URL?client_id=$clientId&redirect_uri=$redirect&response_type=code&scope=$scope"
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String = "",
        val refresh_token: String = "",
        val expires_in: Long = 0L
    )

    suspend fun exchangeCodeForToken(clientId: String, clientSecret: String, code: String): TokenOutcome =
        requestToken(
            clientId = clientId,
            clientSecret = clientSecret,
            formParams = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri()
            )
        )

    suspend fun refreshAccessToken(clientId: String, clientSecret: String, refreshToken: String): TokenOutcome =
        requestToken(
            clientId = clientId,
            clientSecret = clientSecret,
            formParams = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken
            )
        )

    /**
     * Authenticates via HTTP Basic (`Authorization: Basic base64(client_id:client_secret)`) —
     * the RFC 6749 §2.3.1 client-authentication method, and the one most OAuth providers
     * (including Doorkeeper, which PCO's API runs on) expect for confidential clients. Also still
     * includes client_id/secret as body params for servers that only check there — belt and
     * braces, since a mismatch here surfaces as an opaque "unknown client" error either way.
     */
    private suspend fun requestToken(clientId: String, clientSecret: String, formParams: Map<String, String>): TokenOutcome = withContext(Dispatchers.IO) {
        try {
            val bodyParams = formParams + mapOf("client_id" to clientId, "client_secret" to clientSecret)
            val body = bodyParams.entries.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
            val basicAuth = java.util.Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray(Charsets.UTF_8))
            val response = http.post(TOKEN_URL) {
                header("Authorization", "Basic $basicAuth")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body)
            }
            if (response.status.value == 400 || response.status.value == 401) {
                return@withContext TokenOutcome.InvalidCredentials
            }
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Planning Center token request returned HTTP ${response.status.value}",
                    tags = mapOf("subsystem" to "planning_center")
                )
                return@withContext TokenOutcome.Failure
            }
            val parsed = json.decodeFromString(TokenResponse.serializer(), response.body())
            if (parsed.access_token.isBlank()) return@withContext TokenOutcome.InvalidCredentials
            TokenOutcome.Success(
                TokenSet(
                    accessToken = parsed.access_token,
                    refreshToken = parsed.refresh_token,
                    expiresAtEpochMs = System.currentTimeMillis() + parsed.expires_in * 1000
                )
            )
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Planning Center token request failed",
                throwable = e,
                tags = mapOf("subsystem" to "planning_center")
            )
            TokenOutcome.NetworkError
        }
    }

    @Serializable
    private data class PersonAttributes(
        val name: String? = null,
        val first_name: String? = null,
        val last_name: String? = null
    )

    @Serializable
    private data class PersonData(val id: String = "", val attributes: PersonAttributes = PersonAttributes())

    @Serializable
    private data class PersonResponse(val data: PersonData = PersonData())

    suspend fun getCurrentPerson(accessToken: String): PersonOutcome = withContext(Dispatchers.IO) {
        try {
            val response = http.get(ME_URL) {
                header("Authorization", "Bearer $accessToken")
            }
            if (response.status.value == 401 || response.status.value == 403) {
                return@withContext PersonOutcome.Unauthorized
            }
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Planning Center /people/v2/me returned HTTP ${response.status.value}",
                    tags = mapOf("subsystem" to "planning_center")
                )
                return@withContext PersonOutcome.Failure
            }
            val parsed = json.decodeFromString(PersonResponse.serializer(), response.body())
            val attrs = parsed.data.attributes
            val name = attrs.name
                ?: listOfNotNull(attrs.first_name, attrs.last_name).joinToString(" ").ifBlank { "Connected" }
            PersonOutcome.Success(ConnectedPerson(displayName = name))
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Planning Center /people/v2/me failed",
                throwable = e,
                tags = mapOf("subsystem" to "planning_center")
            )
            PersonOutcome.NetworkError
        }
    }

    // ── Services: service types / plans / plan items / arrangement lyrics ──────

    private const val SERVICES_BASE_URL = "https://api.planningcenteronline.com/services/v2"

    data class ServiceType(val id: String, val name: String)
    data class Plan(val id: String, val title: String, val dates: String)

    data class PlanItem(
        val id: String,
        val title: String,
        val description: String = "",
        /** PCO's own item_type string: "song", "header", "media", or "item" (generic). */
        val itemType: String,
        val sequence: Int,
        val songId: String? = null,
        val arrangementId: String? = null,
        val songTitle: String? = null,
        val songAuthor: String? = null,
        val songCcliNumber: String? = null
    )

    data class ArrangementDetail(val chordChart: String, val lyrics: String)

    sealed interface ServiceTypesOutcome {
        data class Success(val serviceTypes: List<ServiceType>) : ServiceTypesOutcome
        data object Unauthorized : ServiceTypesOutcome
        data object NetworkError : ServiceTypesOutcome
        data object Failure : ServiceTypesOutcome
    }

    sealed interface PlansOutcome {
        data class Success(val plans: List<Plan>) : PlansOutcome
        data object Unauthorized : PlansOutcome
        data object NetworkError : PlansOutcome
        data object Failure : PlansOutcome
    }

    sealed interface PlanItemsOutcome {
        data class Success(val items: List<PlanItem>) : PlanItemsOutcome
        data object Unauthorized : PlanItemsOutcome
        data object NetworkError : PlanItemsOutcome
        data object Failure : PlanItemsOutcome
    }

    sealed interface ArrangementOutcome {
        data class Success(val detail: ArrangementDetail) : ArrangementOutcome
        data object Unauthorized : ArrangementOutcome
        data object NetworkError : ArrangementOutcome
        data object Failure : ArrangementOutcome
    }

    suspend fun listServiceTypes(accessToken: String): ServiceTypesOutcome = withContext(Dispatchers.IO) {
        try {
            val response = http.get("$SERVICES_BASE_URL/service_types") {
                header("Authorization", "Bearer $accessToken")
                parameter("per_page", 100)
            }
            if (response.status.value == 401 || response.status.value == 403) {
                return@withContext ServiceTypesOutcome.Unauthorized
            }
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Planning Center service_types returned HTTP ${response.status.value}",
                    tags = mapOf("subsystem" to "planning_center")
                )
                return@withContext ServiceTypesOutcome.Failure
            }
            val root = json.parseToJsonElement(response.body()).jsonObject
            val serviceTypes = (root["data"] as? JsonArray ?: JsonArray(emptyList())).map { el ->
                val obj = el.jsonObject
                ServiceType(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    name = obj["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                )
            }
            ServiceTypesOutcome.Success(serviceTypes)
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Planning Center service_types request failed",
                throwable = e,
                tags = mapOf("subsystem" to "planning_center")
            )
            ServiceTypesOutcome.NetworkError
        }
    }

    suspend fun listUpcomingPlans(accessToken: String, serviceTypeId: String): PlansOutcome = withContext(Dispatchers.IO) {
        try {
            val response = http.get("$SERVICES_BASE_URL/service_types/$serviceTypeId/plans") {
                header("Authorization", "Bearer $accessToken")
                parameter("filter", "future")
                parameter("order", "sort_date")
                parameter("per_page", 25)
            }
            if (response.status.value == 401 || response.status.value == 403) {
                return@withContext PlansOutcome.Unauthorized
            }
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Planning Center plans returned HTTP ${response.status.value}",
                    tags = mapOf("subsystem" to "planning_center")
                )
                return@withContext PlansOutcome.Failure
            }
            val root = json.parseToJsonElement(response.body()).jsonObject
            val plans = (root["data"] as? JsonArray ?: JsonArray(emptyList())).map { el ->
                val obj = el.jsonObject
                val attrs = obj["attributes"]?.jsonObject
                val title = attrs?.get("title")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
                    ?: attrs?.get("series_title")?.jsonPrimitive?.contentOrNull
                    ?: "Untitled Plan"
                Plan(
                    id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                    title = title,
                    dates = attrs?.get("dates")?.jsonPrimitive?.contentOrNull ?: ""
                )
            }
            PlansOutcome.Success(plans)
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Planning Center plans request failed",
                throwable = e,
                tags = mapOf("subsystem" to "planning_center")
            )
            PlansOutcome.NetworkError
        }
    }

    suspend fun getPlanItems(accessToken: String, serviceTypeId: String, planId: String): PlanItemsOutcome =
        withContext(Dispatchers.IO) {
            try {
                val response = http.get("$SERVICES_BASE_URL/service_types/$serviceTypeId/plans/$planId/items") {
                    header("Authorization", "Bearer $accessToken")
                    parameter("include", "song")
                    parameter("per_page", 200)
                }
                if (response.status.value == 401 || response.status.value == 403) {
                    return@withContext PlanItemsOutcome.Unauthorized
                }
                if (response.status.value !in 200..299) {
                    CrashReporter.reportWarning(
                        "Planning Center plan items returned HTTP ${response.status.value}",
                        tags = mapOf("subsystem" to "planning_center")
                    )
                    return@withContext PlanItemsOutcome.Failure
                }
                val root = json.parseToJsonElement(response.body()).jsonObject
                val dataArray = root["data"] as? JsonArray ?: JsonArray(emptyList())
                val includedArray = root["included"] as? JsonArray ?: JsonArray(emptyList())
                val includedByKey = includedArray.associateBy { el ->
                    val obj = el.jsonObject
                    "${obj["type"]?.jsonPrimitive?.contentOrNull}::${obj["id"]?.jsonPrimitive?.contentOrNull}"
                }

                val items = dataArray.map { el ->
                    val obj = el.jsonObject
                    val attrs = obj["attributes"]?.jsonObject
                    val rel = obj["relationships"]?.jsonObject
                    val songRef = rel?.get("song")?.jsonObject?.get("data")
                        ?.let { it as? JsonObject }
                    val arrangementRef = rel?.get("arrangement")?.jsonObject?.get("data")
                        ?.let { it as? JsonObject }
                    val songId = songRef?.get("id")?.jsonPrimitive?.contentOrNull
                    val songAttrs = songId?.let { includedByKey["Song::$it"]?.jsonObject?.get("attributes")?.jsonObject }

                    PlanItem(
                        id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                        title = attrs?.get("title")?.jsonPrimitive?.contentOrNull ?: "",
                        description = attrs?.get("description")?.jsonPrimitive?.contentOrNull ?: "",
                        itemType = attrs?.get("item_type")?.jsonPrimitive?.contentOrNull ?: "item",
                        sequence = attrs?.get("sequence")?.jsonPrimitive?.int ?: 0,
                        songId = songId,
                        arrangementId = arrangementRef?.get("id")?.jsonPrimitive?.contentOrNull,
                        songTitle = songAttrs?.get("title")?.jsonPrimitive?.contentOrNull,
                        songAuthor = songAttrs?.get("author")?.jsonPrimitive?.contentOrNull,
                        songCcliNumber = songAttrs?.get("ccli_number")?.jsonPrimitive?.contentOrNull
                    )
                }.sortedBy { it.sequence }

                PlanItemsOutcome.Success(items)
            } catch (e: Exception) {
                CrashReporter.reportWarning(
                    "Planning Center plan items request failed",
                    throwable = e,
                    tags = mapOf("subsystem" to "planning_center")
                )
                PlanItemsOutcome.NetworkError
            }
        }

    suspend fun getArrangementDetail(accessToken: String, songId: String, arrangementId: String): ArrangementOutcome =
        withContext(Dispatchers.IO) {
            try {
                val response = http.get("$SERVICES_BASE_URL/songs/$songId/arrangements/$arrangementId") {
                    header("Authorization", "Bearer $accessToken")
                }
                if (response.status.value == 401 || response.status.value == 403) {
                    return@withContext ArrangementOutcome.Unauthorized
                }
                if (response.status.value !in 200..299) {
                    CrashReporter.reportWarning(
                        "Planning Center arrangement returned HTTP ${response.status.value}",
                        tags = mapOf("subsystem" to "planning_center")
                    )
                    return@withContext ArrangementOutcome.Failure
                }
                val root = json.parseToJsonElement(response.body()).jsonObject
                val attrs = root["data"]?.jsonObject?.get("attributes")?.jsonObject
                val chordChart = attrs?.get("chord_chart")?.jsonPrimitive?.contentOrNull ?: ""
                // PCO's own "lyrics" attribute is already chord-free (server-side stripped) —
                // prefer it over our own regex stripping; fall back only if it's ever absent.
                val pcoLyrics = attrs?.get("lyrics")?.jsonPrimitive?.contentOrNull
                ArrangementOutcome.Success(
                    ArrangementDetail(
                        chordChart = chordChart,
                        lyrics = pcoLyrics?.takeIf { it.isNotBlank() } ?: PlanningCenterLyricsFormatter.stripChords(chordChart)
                    )
                )
            } catch (e: Exception) {
                CrashReporter.reportWarning(
                    "Planning Center arrangement request failed",
                    throwable = e,
                    tags = mapOf("subsystem" to "planning_center")
                )
                ArrangementOutcome.NetworkError
            }
        }

    // ── Attachments (media backgrounds, slide decks) ────────────────────────────

    /**
     * [thumbnailUrl] (when present) is a public, unauthenticated S3 URL suitable for a small
     * preview — cheap, no token needed. It is NOT a full-resolution download source. The
     * attachment's own `url` attribute is deliberately not kept here: it's a
     * `services.planningcenteronline.com` **web app** link (browser-session auth), not an API
     * download link — hitting it with an OAuth bearer token 302s to the PCO login page, which is
     * silently what got saved as "the image" before this was found. The real file must be
     * fetched via [resolveAttachmentDownloadUrl].
     */
    data class PlanAttachment(val id: String, val filename: String, val thumbnailUrl: String? = null)

    sealed interface AttachmentsOutcome {
        data class Success(val attachments: List<PlanAttachment>) : AttachmentsOutcome
        data object Unauthorized : AttachmentsOutcome
        data object NetworkError : AttachmentsOutcome
        data object Failure : AttachmentsOutcome
    }

    sealed interface FileDownloadOutcome {
        data class Success(val file: File) : FileDownloadOutcome
        data object NetworkError : FileDownloadOutcome
        data object Failure : FileDownloadOutcome
    }

    sealed interface AttachmentUrlOutcome {
        data class Success(val url: String) : AttachmentUrlOutcome
        data object Unauthorized : AttachmentUrlOutcome
        data object Failure : AttachmentUrlOutcome
    }

    suspend fun getItemAttachments(
        accessToken: String,
        serviceTypeId: String,
        planId: String,
        itemId: String
    ): AttachmentsOutcome = withContext(Dispatchers.IO) {
        try {
            val response = http.get(
                "$SERVICES_BASE_URL/service_types/$serviceTypeId/plans/$planId/items/$itemId/attachments"
            ) {
                header("Authorization", "Bearer $accessToken")
                parameter("per_page", 50)
            }
            if (response.status.value == 401 || response.status.value == 403) {
                return@withContext AttachmentsOutcome.Unauthorized
            }
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Planning Center item attachments returned HTTP ${response.status.value}",
                    tags = mapOf("subsystem" to "planning_center")
                )
                return@withContext AttachmentsOutcome.Failure
            }
            val root = json.parseToJsonElement(response.body()).jsonObject
            val attachments = (root["data"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val attrs = obj["attributes"]?.jsonObject
                val filename = attrs?.get("filename")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val thumbnailUrl = attrs.get("thumbnail_url")?.jsonPrimitive?.contentOrNull
                PlanAttachment(id = id, filename = filename, thumbnailUrl = thumbnailUrl)
            }
            AttachmentsOutcome.Success(attachments)
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Planning Center item attachments request failed",
                throwable = e,
                tags = mapOf("subsystem" to "planning_center")
            )
            AttachmentsOutcome.NetworkError
        }
    }

    /**
     * Exchanges an attachment id for a real, temporary download URL (a pre-signed S3 link).
     * PCO's API requires this to be a POST — confirmed by GETting the endpoint first, which
     * returns a description saying so, rather than guessing. This is the same request PCO's own
     * web app makes when a user clicks "download"; it records one `AttachmentActivity` "open"
     * event on the account as an audit-trail entry, same as any legitimate download — not a
     * destructive or content-modifying call.
     */
    suspend fun resolveAttachmentDownloadUrl(accessToken: String, attachmentId: String): AttachmentUrlOutcome =
        withContext(Dispatchers.IO) {
            try {
                val response = http.post("$SERVICES_BASE_URL/attachments/$attachmentId/open") {
                    header("Authorization", "Bearer $accessToken")
                }
                if (response.status.value == 401 || response.status.value == 403) {
                    return@withContext AttachmentUrlOutcome.Unauthorized
                }
                if (response.status.value !in 200..299) {
                    CrashReporter.reportWarning(
                        "Planning Center attachment open returned HTTP ${response.status.value}",
                        tags = mapOf("subsystem" to "planning_center")
                    )
                    return@withContext AttachmentUrlOutcome.Failure
                }
                val root = json.parseToJsonElement(response.body()).jsonObject
                val url = root["data"]?.jsonObject?.get("attributes")?.jsonObject
                    ?.get("attachment_url")?.jsonPrimitive?.contentOrNull
                    ?: return@withContext AttachmentUrlOutcome.Failure
                AttachmentUrlOutcome.Success(url)
            } catch (e: Exception) {
                CrashReporter.reportWarning(
                    "Planning Center attachment open failed",
                    throwable = e,
                    tags = mapOf("subsystem" to "planning_center")
                )
                AttachmentUrlOutcome.Failure
            }
        }

    /** Downloads a file's bytes from an already-resolved URL (a pre-signed S3 link — no auth header needed or wanted). */
    suspend fun downloadFile(url: String, destination: File): FileDownloadOutcome = withContext(Dispatchers.IO) {
        try {
            val response = http.get(url)
            if (response.status.value !in 200..299) {
                CrashReporter.reportWarning(
                    "Planning Center attachment download returned HTTP ${response.status.value}",
                    tags = mapOf("subsystem" to "planning_center")
                )
                return@withContext FileDownloadOutcome.Failure
            }
            val bytes: ByteArray = response.body()
            destination.parentFile?.mkdirs()
            destination.writeBytes(bytes)
            FileDownloadOutcome.Success(destination)
        } catch (e: Exception) {
            CrashReporter.reportWarning(
                "Planning Center attachment download failed",
                throwable = e,
                tags = mapOf("subsystem" to "planning_center")
            )
            FileDownloadOutcome.NetworkError
        }
    }

    /** Fetches raw bytes for an attachment thumbnail preview (public S3 URL, no auth); null on any failure. */
    suspend fun fetchThumbnailBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val response = http.get(url)
            if (response.status.value in 200..299) response.body() else null
        } catch (_: Exception) {
            null
        }
    }
}

package org.churchpresenter.calendar.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** One HTTP exchange, reduced to what the client reads. */
class RelayReply(val status: Int, val body: String, val etag: String = "")

/**
 * The one step that reaches the network, taken as a parameter so the client's sequencing — which
 * header goes where, what each status means — runs in a test against a stand-in.
 */
fun interface RelayTransport {
    fun send(method: String, url: String, headers: Map<String, String>, body: String?): RelayReply
}

/** [RelayTransport] over the JDK client; TLS only. */
class HttpRelayTransport(
    private val timeout: Duration = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(timeout).build(),
) : RelayTransport {
    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): RelayReply {
        val uri = URI.create(url)
        require(uri.scheme == "https") { "relay URL must be https" }
        val request = HttpRequest.newBuilder(uri).timeout(timeout)
        headers.forEach { (name, value) -> request.header(name, value) }
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        request.method(method, publisher)
        val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
        return RelayReply(response.statusCode(), response.body(), response.headers().firstValue("ETag").orElse(""))
    }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 10L
    }
}

/** Why a call did not succeed, as the caller has to tell them apart. */
sealed class RelayFailure(message: String) : Exception(message) {
    /** The desktop token was refused: this instance no longer knows us. Stop and ask to re-pair. */
    class Unauthorized : RelayFailure("relay refused the desktop token")

    /** The instance id is already registered to another desktop. */
    class Taken : RelayFailure("instance id already registered")

    /** The relay did not accept the client key; fetch a fresh one and try again. */
    class ClientKey : RelayFailure("relay refused the client key")

    /** The relay moved on since our cursor; pull again before pushing. */
    class Conflict : RelayFailure("relay state changed since last pull")

    class Rejected(status: Int, detail: String) : RelayFailure("relay rejected the request ($status): $detail")

    class Unreachable(cause: Throwable) : RelayFailure("relay unreachable: ${cause.message}")
}

/** The desktop's half of the relay protocol. */
class RelayClient(
    relayUrl: String,
    private val instanceId: String,
    private val installId: String,
    private val transport: RelayTransport = HttpRelayTransport(),
    private val clientKey: String = "",
) {
    private val base = relayUrl.trimEnd('/') + "/i/" + instanceId

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /** Claims [instanceId] and returns the desktop token. Fails with [RelayFailure.Taken] if someone already has. */
    fun register(): String {
        val reply = call("POST", "$base/register", token = null, body = "{}")
        return decode(RegisterResponse.serializer(), reply).desktopToken
    }

    /** Enrolls a phone this desktop has approved. */
    fun enrollDevice(token: String, deviceId: String, request: EnrollRequest) {
        require(Sanitize.isId(deviceId)) { "device id" }
        val body = json.encodeToString(EnrollRequest.serializer(), request)
        call("PUT", "$base/devices/$deviceId", token, body)
    }

    fun changes(token: String, since: Long): ChangesResponse =
        decode(ChangesResponse.serializer(), call("GET", "$base/changes?since=$since", token, body = null))

    /** Replaces the relay's picture with ours, conditioned on [ifRev], the revision last seen. */
    fun putState(token: String, state: StateRequest, ifRev: Long): Long {
        val body = json.encodeToString(StateRequest.serializer(), state)
        val reply = call("PUT", "$base/state", token, body, extra = mapOf("If-Match" to ifRev.toString()))
        return decode(StateResponse.serializer(), reply).rev
    }

    fun revokeDevice(token: String, deviceId: String) {
        require(Sanitize.isId(deviceId)) { "device id" }
        call("DELETE", "$base/devices/$deviceId", token, body = null)
    }

    private fun call(
        method: String,
        url: String,
        token: String?,
        body: String?,
        extra: Map<String, String> = emptyMap(),
    ): RelayReply {
        val headers = HashMap<String, String>(extra)
        headers["Accept"] = "application/json"
        headers["X-Install"] = installId
        if (clientKey.isNotEmpty()) headers["X-Client-Key"] = clientKey
        if (body != null) headers["Content-Type"] = "application/json"
        if (token != null) headers["Authorization"] = "Bearer $token"
        val reply = try {
            transport.send(method, url, headers, body)
        } catch (e: IOException) {
            throw RelayFailure.Unreachable(e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RelayFailure.Unreachable(e)
        }
        return when (reply.status) {
            in HTTP_OK_RANGE -> reply
            HTTP_UNAUTHORIZED -> if (reply.body.contains(CLIENT_KEY_ERROR)) throw RelayFailure.ClientKey() else throw RelayFailure.Unauthorized()
            HTTP_FORBIDDEN -> throw RelayFailure.Unauthorized()
            HTTP_CONFLICT -> if (token == null) throw RelayFailure.Taken() else throw RelayFailure.Conflict()
            HTTP_PRECONDITION_FAILED -> throw RelayFailure.Conflict()
            else -> throw RelayFailure.Rejected(reply.status, reply.body.take(MAX_ERROR_CHARS))
        }
    }

    private fun <T> decode(serializer: KSerializer<T>, reply: RelayReply): T = try {
        json.decodeFromString(serializer, reply.body)
    } catch (e: IllegalArgumentException) {
        throw RelayFailure.Rejected(reply.status, "unreadable reply: ${e.message?.take(MAX_ERROR_CHARS)}")
    }

    private companion object {
        val HTTP_OK_RANGE = 200..299
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CONFLICT = 409
        const val HTTP_PRECONDITION_FAILED = 412
        const val MAX_ERROR_CHARS = 200
        const val CLIENT_KEY_ERROR = "\"client_key\""
    }
}

/** `GET` of the website's relay-config: the client key the relay currently expects. */
fun fetchClientKey(url: String, transport: RelayTransport): String? {
    val reply = try {
        transport.send("GET", url, mapOf("Accept" to "application/json"), null)
    } catch (_: IOException) {
        return null
    }
    if (reply.status !in 200..299) return null
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString(ClientKeyResponse.serializer(), reply.body).clientKey.takeIf { it.isNotBlank() }
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Serializable
private data class ClientKeyResponse(val clientKey: String = "")

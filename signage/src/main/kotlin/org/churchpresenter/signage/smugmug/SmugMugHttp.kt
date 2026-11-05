package org.churchpresenter.signage.smugmug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/** SmugMug answered [status]: 401 for a wrong API key, 404 for a site or gallery it does not know. */
class SmugMugStatusException(val status: Int) : IOException("SmugMug answered $status")

/**
 * What the SmugMug client asks of the web: one JSON reply, or one file. Behind an interface so the
 * client is tested without SmugMug.
 */
interface SmugMugHttp {
    /** The reply to GET [url], parsed; throws [SmugMugStatusException] or [IOException] when it fails. */
    suspend fun json(url: String): JsonObject

    /** Saves what [url] holds into [to], whole or not at all. */
    suspend fun download(url: String, to: Path)
}

/**
 * [SmugMugHttp] over the JDK's client: no cookies, so SmugMug sees a visitor who is not signed in. A
 * reply of 429 or 503 (too many requests, busy) is tried again after a pause that doubles. The URL is
 * never put in an error, since it carries the API key.
 */
class JdkSmugMugHttp(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(TIMEOUT)
        .build(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : SmugMugHttp {
    override suspend fun json(url: String): JsonObject {
        val body = send(url, HttpResponse.BodyHandlers.ofString())
        return try {
            JSON.parseToJsonElement(body).jsonObject
        } catch (e: SerializationException) {
            throw IOException("SmugMug sent something that is not JSON", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("SmugMug sent something that is not JSON", e)
        }
    }

    override suspend fun download(url: String, to: Path) {
        val part = to.resolveSibling(".${to.fileName}.part")
        try {
            send(url, HttpResponse.BodyHandlers.ofFile(part))
            withContext(Dispatchers.IO) { Files.move(part, to, StandardCopyOption.REPLACE_EXISTING) }
        } finally {
            withContext(Dispatchers.IO) { Files.deleteIfExists(part) }
        }
    }

    private suspend fun <T> send(url: String, handler: HttpResponse.BodyHandler<T>): T {
        val request = HttpRequest.newBuilder(URI(url))
            .header("Accept", "application/json")
            .timeout(TIMEOUT)
            .GET()
            .build()
        var wait = FIRST_WAIT_MS
        var status = BUSY
        for (attempt in 1..TRIES) {
            val response = call(request, handler)
            status = response.statusCode()
            if (status == OK) return response.body()
            if (status != TOO_MANY && status != BUSY || attempt == TRIES) break
            pause(wait)
            wait *= 2
        }
        throw SmugMugStatusException(status)
    }

    /** [request] sent, off the caller's thread; an interrupt is an [IOException], as a dropped line is. */
    private suspend fun <T> call(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> =
        withContext(Dispatchers.IO) {
            try {
                client.send(request, handler)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("interrupted", e)
            }
        }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(30)
        val JSON = Json { ignoreUnknownKeys = true }
        const val TRIES = 4
        const val FIRST_WAIT_MS = 2_000L
        const val OK = 200
        const val TOO_MANY = 429
        const val BUSY = 503
    }
}

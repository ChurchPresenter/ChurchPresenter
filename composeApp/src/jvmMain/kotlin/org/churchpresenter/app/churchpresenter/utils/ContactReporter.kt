package org.churchpresenter.app.churchpresenter.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.BuildConfig

/**
 * Sends a contact / feedback message to the ChurchPresenter server, which relays
 * it by email.
 *
 * No credentials ever live in the app: the desktop client only POSTs the message
 * to a public HTTPS endpoint — exactly the shape of call [LiveMapReporter] already
 * makes. Bot abuse is bounded server-side by a per-IP rate limit; when it trips the
 * server returns HTTP 429 and we ask the user to finish in the browser, where the
 * web contact form is protected by Turnstile.
 */
object ContactReporter {

    @Serializable
    data class ContactRequest(
        val type: String,
        val name: String,
        val message: String,
        val email: String = "",
        val context: String = "",
        // Honeypot — always empty from a real client; the server rejects non-empty.
        val company: String = "",
    )

    sealed interface Outcome {
        /** Delivered (or accepted in local dev). */
        data object Success : Outcome
        /** Rejected by validation; [error] is the server's reason if available. */
        data class Invalid(val error: String?) : Outcome
        /** Per-IP rate limit hit — escalate the user to [WEB_CONTACT_URL]. */
        data object RateLimited : Outcome
        /** Couldn't reach the server (no connection / timeout) — retryable; hint to check the network. */
        data object NetworkError : Outcome
        /** Server-side error (5xx / unexpected status) — transient, safe to retry. */
        data object Failure : Outcome
    }

    /** Public web contact form (guarded by Turnstile) — the rate-limit escalation target. */
    const val WEB_CONTACT_URL = "https://www.churchpresenter.org/contact"
    private const val ENDPOINT = "https://www.churchpresenter.org/api/contact-app"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val http by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 5_000
            }
        }
    }

    @Serializable
    private data class ErrorBody(val error: String? = null)

    /** Non-sensitive diagnostics appended to the message body to aid bug-report triage. */
    fun defaultContext(): String =
        "Church Presenter ${BuildConfig.VERSION_DISPLAY} · " +
            "${System.getProperty("os.name")} ${System.getProperty("os.version")}"

    suspend fun submit(request: ContactRequest): Outcome = withContext(Dispatchers.IO) {
        try {
            val response = http.post(ENDPOINT) {
                header(HttpHeaders.UserAgent, "ChurchPresenter/${BuildConfig.APP_VERSION}")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ContactRequest.serializer(), request))
            }
            when (response.status.value) {
                200 -> Outcome.Success
                429 -> Outcome.RateLimited
                in 400..499 -> {
                    val error = runCatching {
                        json.decodeFromString(ErrorBody.serializer(), response.bodyAsText()).error
                    }.getOrNull()
                    Outcome.Invalid(error)
                }
                else -> Outcome.Failure
            }
        } catch (_: Exception) {
            // Connection refused / no route / DNS / timeout — surfaced as a network error
            // so the UI can tell the user to check their connection.
            Outcome.NetworkError
        }
    }
}

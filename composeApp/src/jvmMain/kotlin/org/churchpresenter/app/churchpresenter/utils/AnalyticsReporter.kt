package org.churchpresenter.app.churchpresenter.utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.churchpresenter.app.churchpresenter.AnalyticsConfig
import java.io.File
import java.util.UUID

/**
 * Sends events to Google Analytics 4 via the Measurement Protocol.
 *
 * Credentials are baked in at build time via AnalyticsConfig (generated from
 * $signingRepo/analytics.properties). When either value is blank the reporter
 * is disabled and no HTTP calls are made.
 */
object AnalyticsReporter {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val http by lazy { HttpClient(CIO) }

    private val clientId: String by lazy { resolveClientId() }
    private val sessionId: String = System.currentTimeMillis().toString()

    fun initialize() {
        Runtime.getRuntime().addShutdownHook(Thread {
            try { http.close() } catch (_: Exception) {}
            scope.cancel()
        })
    }

    fun isEnabled(): Boolean = AnalyticsConfig.MEASUREMENT_ID.isNotBlank() && AnalyticsConfig.API_SECRET.isNotBlank()

    fun logAppOpen() = logEvent("app_open")

    fun logPageView(pageName: String) = logEvent("page_view", mapOf(
        "page_title" to pageName,
        "page_location" to "app://churchpresenter/${pageName.lowercase().replace(" ", "_")}"
    ))

    fun logButtonClick(label: String, context: String = "") {
        val params = buildMap<String, Any> {
            put("button_label", label)
            if (context.isNotBlank()) put("context", context)
        }
        logEvent("button_click", params)
    }

    fun logSongDisplayed(songNumber: Int, title: String, songbook: String) =
        logEvent("song_displayed", mapOf("song_number" to songNumber, "title" to title, "songbook" to songbook))

    fun logVerseDisplayed(bibleName: String, bookName: String, chapter: Int, verseNumber: Int) =
        logEvent("verse_displayed", mapOf("bible_name" to bibleName, "book_name" to bookName, "chapter" to chapter, "verse_number" to verseNumber))

    private fun logEvent(name: String, params: Map<String, Any> = emptyMap()) {
        if (!isEnabled()) return
        scope.launch {
            try {
                val body = buildJsonObject {
                    put("client_id", clientId)
                    put("events", buildJsonArray {
                        add(buildJsonObject {
                            put("name", name)
                            put("params", buildJsonObject {
                                // Required for GA4 Realtime active-user counting
                                put("session_id", sessionId)
                                put("engagement_time_msec", 1)
                                params.forEach { (k, v) ->
                                    when (v) {
                                        is String -> put(k, v)
                                        is Int -> put(k, v)
                                        is Long -> put(k, v)
                                        is Double -> put(k, v)
                                        is Boolean -> put(k, v)
                                    }
                                }
                            })
                        })
                    })
                }.toString()

                http.post("https://www.google-analytics.com/mp/collect") {
                    parameter("measurement_id", AnalyticsConfig.MEASUREMENT_ID)
                    parameter("api_secret", AnalyticsConfig.API_SECRET)
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            } catch (_: Exception) {}
        }
    }

    private fun resolveClientId(): String {
        val file = File(System.getProperty("user.home"), ".churchpresenter/analytics_client_id")
        return try {
            if (file.exists()) {
                file.readText().trim().takeIf { it.isNotBlank() } ?: createClientId(file)
            } else {
                createClientId(file)
            }
        } catch (_: Exception) { UUID.randomUUID().toString() }
    }

    private fun createClientId(file: File): String {
        val id = UUID.randomUUID().toString()
        try { file.parentFile?.mkdirs(); file.writeText(id) } catch (_: Exception) {}
        return id
    }
}

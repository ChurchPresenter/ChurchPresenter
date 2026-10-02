package org.churchpresenter.stt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.utils.TrainingDataLogger
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val HTTP_OK = 200
private const val MODEL_POLL_INTERVAL_MS = 60_000L

/**
 * Keys the training-data and on-screen history logs by [sessionId] from now on, so a service's
 * opening songs share its session's files instead of waiting for the first Bible detection
 * (which still sets it too, as a fallback). Two sources feed it: `/api/health`, read on connect
 * and on every poll, which knows the session before anyone speaks; and the top-level
 * `session_id` STT puts on every socket payload, which follows a new session the moment it
 * starts. Null leaves the current id alone.
 */
internal fun applySessionId(sessionId: String?) {
    if (sessionId != null) TrainingDataLogger.sessionId = sessionId
}

/**
 * Help Dev: the periodic capture of the live STT session's `.db`, and the session id read alongside
 * it. [STTManager] starts it when the socket connects and stops it when it disconnects.
 */
internal class STTCapture(private val scope: CoroutineScope) {

    // Read live from a background loop, not observed by Compose — kept in sync with the
    // "Help Dev" checkbox (BibleEngineSettings.helpDevMode) by a LaunchedEffect in main.kt, since
    // connect() (and therefore startDbCapture()) only runs once per explicit Connect click.
    var helpDevModeEnabled: Boolean = false
    private var dbCaptureJob: Job? = null

    // Base URL of the STT server the capture loop is pulling from, kept so [stop] can take one
    // final snapshot after the loop stops.
    private var captureBaseUrl: String? = null

    /**
     * Whether disconnecting should pull one last .db snapshot. The periodic loop only runs while
     * connected, so without this the archived .db stops at the last 60-second tick — and in practice
     * further back: `2026-07-19_102718.db` ends 5 minutes before the service did, mid-sentence, which
     * silently turned 7 references the engine had actually detected live into replay "misses". A
     * snapshot is only worth pulling when Help Dev is on (nothing else reads it) and a server is
     * known.
     */
    fun shouldCaptureFinalSnapshot(helpDev: Boolean, baseUrl: String?): Boolean =
        helpDev && !baseUrl.isNullOrBlank()

    /**
     * Periodically pulls a fresh copy of the live STT session's .db into
     * ~/.churchpresenter/bible-stt-logs/ (same folder TrainingDataLogger writes to), so the whole
     * session can be submitted as one folder without a manual, end-of-service pull from the STT
     * server — which may not be running by then. Read-only against the STT server (GET only,
     * never triggers its file_mover, which defaults to deleting the source file). Gated by
     * [helpDevModeEnabled] so ordinary users never pay the periodic download cost.
     */
    fun startDbCapture(baseUrl: String) {
        captureBaseUrl = baseUrl
        dbCaptureJob?.cancel()
        dbCaptureJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                // Every tick, Help Dev or not: STT starts a new session id for each service.
                applySessionId(fetchSessionId(baseUrl))
                if (helpDevModeEnabled) runCatching { captureDbSnapshot(baseUrl) }
                delay(MODEL_POLL_INTERVAL_MS)
            }
        }
    }

    /** Stops the loop and, when Help Dev wants it, takes the one final snapshot. */
    fun stop() {
        dbCaptureJob?.cancel()
        dbCaptureJob = null
        val baseUrl = captureBaseUrl
        if (shouldCaptureFinalSnapshot(helpDevModeEnabled, baseUrl)) {
            // Deliberately NOT on dbCaptureJob — that one was just cancelled. Best-effort: a server
            // that has already gone away simply leaves the last periodic snapshot in place.
            scope.launch(Dispatchers.IO) { runCatching { captureDbSnapshot(baseUrl!!) } }
        }
    }

    /**
     * The STT server's current session id, from `GET /api/health`'s `session_id`. Null when the
     * server is unreachable, answers non-200, or has no session yet. Never throws.
     */
    fun fetchSessionId(baseUrl: String): String? = runCatching {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/health"))
            .GET()
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != HTTP_OK) return@runCatching null
        JSONObject(response.body()).stringOrNull("session_id")
    }.getOrNull()

    fun captureDbSnapshot(baseUrl: String) {
        // Shares TrainingDataLogger's 30-day retention sweep so these .db snapshots don't
        // accumulate forever in bible-stt-logs — see cleanupOldLogsOnce()'s doc for why this
        // cross-package call is `internal` instead of TrainingDataLogger writing the file itself.
        TrainingDataLogger.cleanupOldLogsOnce()

        val client = HttpClient.newHttpClient()
        val dbName = remoteDbName(client, baseUrl) ?: return

        val encodedPath = URLEncoder.encode(dbName, "UTF-8")
        val downloadRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/file-manager/download?path=$encodedPath"))
            .GET()
            .build()
        val downloadResponse = client.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray())
        if (downloadResponse.statusCode() != HTTP_OK) return

        val logDir = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
        val target = File(logDir, File(dbName).name)
        val tmp = File(logDir, "${target.name}.tmp")
        tmp.writeBytes(downloadResponse.body())
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun remoteDbName(client: HttpClient, baseUrl: String): String? {
        val statusRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/transcription/status"))
            .GET()
            .build()
        val statusResponse = client.send(statusRequest, HttpResponse.BodyHandlers.ofString())
        if (statusResponse.statusCode() != HTTP_OK) return null
        return JSONObject(statusResponse.body()).optJSONObject("state")?.stringOrNull("db_name")
    }
}

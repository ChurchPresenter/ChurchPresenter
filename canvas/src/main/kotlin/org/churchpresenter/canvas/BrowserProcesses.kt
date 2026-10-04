package org.churchpresenter.canvas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.churchpresenter.diagnostics.Log

/** The headless browser processes behind the browser source: finding one, its port, and stopping it. */
internal object BrowserProcesses {
    private val httpClient = HttpClient.newHttpClient()
    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
    @Volatile private var zombiesCleaned = false

    /** The executable `which`/`where` reports for [name], when it exists on disk. */
    private fun browserOnPath(whichCmd: String, name: String): String? = try {
        val proc = ProcessBuilder(whichCmd, name).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        val path = output.lines().firstOrNull()?.trim()
        if (proc.waitFor() == 0 && !path.isNullOrBlank() && java.io.File(path).exists()) path else null
    } catch (_: Exception) {
        null
    }

    internal fun findBrowserExecutable(): String? {
        val osName = System.getProperty("os.name", "").lowercase()
        val isWindows = osName.contains("win")

        // Check well-known install paths first
        val candidates = when {
            isWindows -> listOf(
                "${System.getenv("ProgramFiles(x86)")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${System.getenv("ProgramFiles")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${System.getenv("LOCALAPPDATA")}\\Microsoft\\Edge\\Application\\msedge.exe",
                "${System.getenv("ProgramFiles")}\\Google\\Chrome\\Application\\chrome.exe",
                "${System.getenv("ProgramFiles(x86)")}\\Google\\Chrome\\Application\\chrome.exe",
                "${System.getenv("LOCALAPPDATA")}\\Google\\Chrome\\Application\\chrome.exe"
            )
            osName.contains("mac") -> listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                // Homebrew paths
                "/opt/homebrew/bin/chromium",
                "/usr/local/bin/chromium"
            )
            else -> listOf(
                "/usr/bin/google-chrome-stable",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium-browser",
                "/usr/bin/chromium",
                "/snap/bin/chromium",
                "/usr/bin/microsoft-edge-stable",
                "/usr/bin/microsoft-edge"
            )
        }
        val found = candidates.firstOrNull { path ->
            try { java.io.File(path).exists() } catch (_: Exception) { false }
        }
        if (found != null) return found

        // Fallback: check PATH
        val names = if (isWindows) listOf("msedge.exe", "chrome.exe")
                    else listOf(
                        "google-chrome-stable",
                        "google-chrome",
                        "chromium-browser",
                        "chromium",
                        "microsoft-edge-stable"
                    )
        val whichCmd = if (isWindows) "where" else "which"
        return names.firstNotNullOfOrNull { name -> browserOnPath(whichCmd, name) }
    }

    internal fun findFreePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    /**
     * Kill orphaned headless Chrome/Edge processes from previous runs.
     * Only runs once per app session, on the first acquire() call.
     */
    internal fun killZombieBrowsers() {
        if (zombiesCleaned) return
        zombiesCleaned = true
        try {
            if (isWindows) {
                // Find headless Chrome/Edge processes via wmic
                val proc = ProcessBuilder(
                    "wmic", "process", "where",
                    "CommandLine like '%--headless%' and (Name='msedge.exe' or Name='chrome.exe')",
                    "get", "ProcessId"
                ).redirectErrorStream(true).start()
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor(WMIC_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                val pids = Regex("\\d+").findAll(output).map { it.value }.toList()
                for (pid in pids) {
                    Log.warn("BrowserSource", "Killing zombie browser process: PID $pid")
                    ProcessBuilder("taskkill", "/F", "/T", "/PID", pid)
                        .redirectErrorStream(true).start()
                        .waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                }
            } else {
                // On Linux/macOS, kill headless chrome/edge processes
                ProcessBuilder("pkill", "-f", "--headless.*--remote-debugging-port")
                    .redirectErrorStream(true).start()
                    .waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
            }
        } catch (_: Exception) {}
    }

    internal suspend fun waitForCdpReady(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:$port/json/version"))
                    .GET().build()
                val response = withContext(Dispatchers.IO) {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                }
                if (response.statusCode() == HTTP_OK) return true
            } catch (_: Exception) {
                // Not ready yet
            }
            delay(DEVTOOLS_POLL_INTERVAL_MS)
        }
        return false
    }

    internal fun getPageWebSocketUrl(port: Int): String? {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/json"))
                .GET().build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val pages = Json.parseToJsonElement(response.body()).jsonArray
            // Find the actual page tab, not extension background pages
            val page = pages.firstOrNull { entry ->
                entry.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "page"
            } ?: pages.firstOrNull()
            page?.jsonObject?.get("webSocketDebuggerUrl")?.jsonPrimitive?.contentOrNull
        } catch (e: IOException) {
            pageUrlFailed(e)
        } catch (e: InterruptedException) {
            pageUrlFailed(e)
        } catch (e: IllegalArgumentException) {
            // A reply that is not the JSON array DevTools sends.
            pageUrlFailed(e)
        }
    }

    private fun pageUrlFailed(e: Exception): String? {
        Log.warn("BrowserSource", "getPageWebSocketUrl error: ${e.message}")
        return null
    }

    internal fun killProcess(process: Process) {
        try {
            if (isWindows) {
                try {
                    val pid = process.pid()
                    ProcessBuilder("taskkill", "/F", "/T", "/PID", pid.toString())
                        .redirectErrorStream(true).start()
                        .waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Throwable) {}
            } else {
                process.destroyForcibly()
            }
            process.waitFor(PROCESS_KILL_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Throwable) {
            process.destroyForcibly()
        }
    }
}

private const val HTTP_OK = 200
private const val DEVTOOLS_POLL_INTERVAL_MS = 500L
private const val WMIC_TIMEOUT_S = 5L
private const val PROCESS_KILL_TIMEOUT_S = 3L

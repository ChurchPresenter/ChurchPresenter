package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

sealed class TunnelStatus {
    data object Idle : TunnelStatus()
    data object Downloading : TunnelStatus()
    data object Starting : TunnelStatus()
    data class Connected(val url: String) : TunnelStatus()
    data class Error(val message: String) : TunnelStatus()
}

class TunnelManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tunnelUrl = MutableStateFlow<String?>(null)
    val tunnelUrl: StateFlow<String?> = _tunnelUrl.asStateFlow()

    private val _status = MutableStateFlow<TunnelStatus>(TunnelStatus.Idle)
    val status: StateFlow<TunnelStatus> = _status.asStateFlow()

    private var process: Process? = null
    private var monitorJob: Job? = null

    private val dataDir = File(System.getProperty("user.home"), ".churchpresenter")
    private val binaryFile = File(dataDir, "cloudflared.exe")
    private val tmpFile = File(dataDir, "cloudflared.exe.tmp")

    private val downloadUrl =
        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"

    private val urlRegex = Regex("""https://[a-z0-9-]+\.trycloudflare\.com""")

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            process?.destroyForcibly()
        })
    }

    fun start(localPort: Int) {
        if (_status.value is TunnelStatus.Downloading || _status.value is TunnelStatus.Starting) return

        scope.launch {
            try {
                if (!binaryFile.exists()) {
                    downloadBinary()
                }
                startTunnel(localPort)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = TunnelStatus.Error(e.message ?: "Unknown error")
                _tunnelUrl.value = null
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        process?.destroyForcibly()
        process = null
        _tunnelUrl.value = null
        _status.value = TunnelStatus.Idle
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun downloadBinary() {
        _status.value = TunnelStatus.Downloading
        dataDir.mkdirs()
        tmpFile.delete()

        val connection = URI(downloadUrl).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000

        try {
            connection.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
            }

            if (!tmpFile.renameTo(binaryFile)) {
                binaryFile.delete()
                if (!tmpFile.renameTo(binaryFile)) {
                    throw RuntimeException("Failed to move downloaded binary into place")
                }
            }
        } finally {
            tmpFile.delete()
            connection.disconnect()
        }
    }

    private suspend fun startTunnel(localPort: Int) {
        _status.value = TunnelStatus.Starting

        val pb = ProcessBuilder(
            binaryFile.absolutePath, "tunnel", "--url", "http://localhost:$localPort"
        )
        pb.redirectErrorStream(true)

        val proc = pb.start()
        process = proc

        monitorJob = scope.launch {
            var foundUrl = false
            try {
                proc.inputStream.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val match = urlRegex.find(line)
                        if (match != null && !foundUrl) {
                            foundUrl = true
                            val url = match.value
                            _tunnelUrl.value = url
                            _status.value = TunnelStatus.Connected(url)
                        }
                        line = reader.readLine()
                    }
                }
            } catch (_: Exception) {
                // Process was destroyed
            }

            // Process exited
            if (foundUrl && _status.value is TunnelStatus.Connected) {
                _status.value = TunnelStatus.Error("Tunnel disconnected")
                _tunnelUrl.value = null
            } else if (!foundUrl) {
                _status.value = TunnelStatus.Error("Tunnel failed to start")
                _tunnelUrl.value = null
            }
        }

        // Wait up to 30s for URL to appear
        withTimeoutOrNull(30_000) {
            while (_tunnelUrl.value == null && monitorJob?.isActive == true) {
                delay(200)
            }
        }

        if (_tunnelUrl.value == null && monitorJob?.isActive == true) {
            stop()
            _status.value = TunnelStatus.Error("Tunnel timed out — no public URL received")
        }
    }
}

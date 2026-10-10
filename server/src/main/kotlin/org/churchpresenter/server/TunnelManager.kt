package org.churchpresenter.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties
import org.churchpresenter.sharedui.utils.addGuardedShutdownHook

private const val HTTP_OK = 200
private const val TUNNEL_READY_TIMEOUT_MS = 30_000L
private const val TUNNEL_POLL_INTERVAL_MS = 200L
private const val CLOUDFLARED_RELEASES = "https://github.com/cloudflare/cloudflared/releases/download"
private const val CLOUDFLARED_PINS = "/tunnel/cloudflared-builds.properties"

sealed class TunnelStatus {
    data object Idle : TunnelStatus()
    data object Downloading : TunnelStatus()
    data object Starting : TunnelStatus()
    data class Connected(val url: String) : TunnelStatus()
    data class Error(val message: String) : TunnelStatus()
}

private val TUNNEL_URL_REGEX = Regex("""https://[a-z0-9-]+\.trycloudflare\.com""")

internal fun extractTunnelUrl(line: String): String? = TUNNEL_URL_REGEX.find(line)?.value

internal fun cloudflaredAsset(isWin: Boolean, isMac: Boolean, isArm: Boolean): String = when {
    isWin -> "cloudflared-windows-amd64.exe"
    isMac && isArm -> "cloudflared-darwin-arm64.tgz"
    isMac -> "cloudflared-darwin-amd64.tgz"
    isArm -> "cloudflared-linux-arm64"
    else -> "cloudflared-linux-amd64"
}

/**
 * The cloudflared release the tunnel runs and the SHA-256 of each platform's asset in it, read
 * from `tunnel/cloudflared-builds.properties`.
 */
internal class CloudflaredPins(private val props: Properties) {
    val version: String get() = props.getProperty("version").orEmpty()

    fun sha256(asset: String): String = props.getProperty(asset).orEmpty()

    fun downloadUrl(asset: String): String = "$CLOUDFLARED_RELEASES/$version/$asset"

    companion object {
        fun load(): CloudflaredPins = CloudflaredPins(
            Properties().apply {
                CloudflaredPins::class.java.getResourceAsStream(CLOUDFLARED_PINS)?.use { load(it) }
            },
        )
    }
}

/**
 * [bytes] if their SHA-256 is [expectedSha256]; otherwise an [IOException], so nothing unverified
 * is ever written where it can run. A missing pin refuses too.
 */
internal fun verifiedDownload(bytes: ByteArray, expectedSha256: String): ByteArray {
    if (expectedSha256.isBlank()) throw IOException("No pinned SHA-256 for this cloudflared build")
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    if (!actual.equals(expectedSha256, ignoreCase = true)) {
        throw IOException("cloudflared download failed verification (SHA-256 $actual, expected $expectedSha256)")
    }
    return bytes
}

/** Whether to fetch cloudflared: none installed, or one from a pin other than [pinnedVersion]. */
internal fun needsCloudflaredDownload(
    binaryExists: Boolean,
    installedVersion: String?,
    pinnedVersion: String,
): Boolean = !binaryExists || installedVersion?.trim() != pinnedVersion

internal fun moveBinaryIntoPlace(downloaded: File, target: File) {
    if (downloaded.renameTo(target)) return
    target.delete()
    if (!downloaded.renameTo(target)) {
        throw IOException("Failed to move downloaded binary into place")
    }
}

internal fun checkExtracted(exitCode: Int, binaryExists: Boolean) {
    if (exitCode != 0 || !binaryExists) {
        throw IOException("Failed to extract cloudflared from archive (exit $exitCode)")
    }
}

internal fun tunnelExitStatus(foundUrl: Boolean, current: TunnelStatus): TunnelStatus? = when {
    foundUrl && current is TunnelStatus.Connected -> TunnelStatus.Error("Tunnel disconnected")
    !foundUrl -> TunnelStatus.Error("Tunnel failed to start")
    else -> null
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

    private val os = System.getProperty("os.name").lowercase()
    private val arch = System.getProperty("os.arch").lowercase()

    private val isMac = os.contains("mac")
    private val isWin = os.contains("win")
    private val isArm = arch.contains("aarch64") || arch.contains("arm")

    private val binaryName = if (isWin) "cloudflared.exe" else "cloudflared"
    private val binaryFile = File(dataDir, binaryName)
    private val tmpFile = File(dataDir, if (isMac) "cloudflared.tgz.tmp" else "$binaryName.tmp")

    private val versionFile = File(dataDir, "cloudflared.version")
    private val pins = CloudflaredPins.load()
    private val asset = cloudflaredAsset(isWin, isMac, isArm)

    init {
        addGuardedShutdownHook("tunnel") {
            process?.destroyForcibly()
        }
    }

    fun start(localPort: Int) {
        if (_status.value is TunnelStatus.Downloading || _status.value is TunnelStatus.Starting) return

        scope.launch {
            try {
                val installed = if (versionFile.exists()) versionFile.readText() else null
                if (needsCloudflaredDownload(binaryFile.exists(), installed, pins.version)) {
                    downloadBinary()
                }
                startTunnel(localPort)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Downloading the binary, or starting it.
                tunnelFailed(e)
            } catch (e: IllegalStateException) {
                tunnelFailed(e)
            } catch (e: IllegalArgumentException) {
                tunnelFailed(e)
            }
        }
    }

    private fun tunnelFailed(e: Exception) {
        _status.value = TunnelStatus.Error(e.message ?: "Unknown error")
        _tunnelUrl.value = null
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

        val client = HttpClient.newBuilder()
            // NORMAL follows redirects but refuses HTTPS→HTTP downgrades — we're fetching an executable
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI(pins.downloadUrl(asset)))
            .timeout(Duration.ofSeconds(120))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != HTTP_OK) {
            throw IOException("Download failed (HTTP ${response.statusCode()})")
        }

        try {
            tmpFile.writeBytes(verifiedDownload(response.body(), pins.sha256(asset)))
            installDownloadedBinary()
            binaryFile.setExecutable(true)
            versionFile.writeText(pins.version)
        } finally {
            tmpFile.delete()
        }
    }

    private fun installDownloadedBinary() {
        if (isMac) {
            binaryFile.delete()
            val result = ProcessBuilder("tar", "-xzf", tmpFile.absolutePath, "-C", dataDir.absolutePath, "cloudflared")
                .redirectErrorStream(true)
                .start()
            checkExtracted(result.waitFor(), binaryFile.exists())
            return
        }
        moveBinaryIntoPlace(tmpFile, binaryFile)
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
                        val url = extractTunnelUrl(line)
                        if (url != null && !foundUrl) {
                            foundUrl = true
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
            tunnelExitStatus(foundUrl, _status.value)?.let {
                _status.value = it
                _tunnelUrl.value = null
            }
        }

        // Wait up to 30s for URL to appear
        withTimeoutOrNull(TUNNEL_READY_TIMEOUT_MS) {
            while (_tunnelUrl.value == null && monitorJob?.isActive == true) {
                delay(TUNNEL_POLL_INTERVAL_MS)
            }
        }

        if (_tunnelUrl.value == null && monitorJob?.isActive == true) {
            stop()
            _status.value = TunnelStatus.Error("Tunnel timed out — no public URL received")
        }
    }
}

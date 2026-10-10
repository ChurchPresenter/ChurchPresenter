package org.churchpresenter.updater

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.CoroutineContext

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000

/**
 * The steps the update window's download takes that reach past it: counting the download, fetching
 * the installer, and running it and quitting. One holder, so a test hands in all three as fakes
 * through a single parameter.
 */
internal class UpdateSteps(
    val reportDownloadStarted: (version: String) -> Unit = UpdateChecker::reportDownloadStarted,
    val download: suspend (url: String, report: suspend (DownloadState) -> Unit) -> Unit = ::downloadInstaller,
    val install: (File) -> DownloadState.Error? = ::installAndQuit,
)

/**
 * The update window's download, from Download to Install Now: [download] counts it and fetches the
 * installer for [info], reporting each stage into [state]; [install] runs the finished file, and a
 * failure to start it lands in [state] to be shown in place.
 *
 * A plain state holder rather than composable code because the window around it is a
 * `DialogWindow`, which cannot be composed headless; this keeps the sequence drivable by a test.
 * Each stage is set on [ui], the dispatcher the window reads [state] on.
 */
@Stable
internal class UpdateDownloadFlow(
    private val info: UpdateInfo?,
    private val scope: CoroutineScope,
    private val steps: UpdateSteps = UpdateSteps(),
    private val ui: CoroutineContext = Dispatchers.Main,
) {
    var state by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set

    private var job: Job? = null

    /** Starts the download, or returns null when there is no installer to fetch. */
    fun download(): Job? {
        val url = info?.downloadUrl ?: return null
        // Count this as an app-updater download (fire-and-forget, own scope —
        // never delays or fails the actual download below).
        steps.reportDownloadStarted(info.latestVersion)
        return scope.launch(Dispatchers.IO) {
            steps.download(url) { stage -> withContext(ui) { state = stage } }
        }.also { job = it }
    }

    /**
     * Stops a download in progress and goes back to offering one. The download notices at its next
     * chunk and deletes what it had written; a report already on its way is dropped with it.
     */
    fun cancel() {
        job?.cancel()
        job = null
        state = DownloadState.Idle
    }

    fun install(file: File) {
        steps.install(file)?.let { state = it }
    }
}

/**
 * Downloads the installer at [downloadUrl] into a temp file, reporting each stage through [report]:
 * progress as the bytes land, then [DownloadState.Done] with the file, or [DownloadState.Error] when
 * the connection, the download or the temp file fails -- a malformed URL included.
 *
 * Split out of [UpdateAvailableDialog] so the download can be driven against a local server; the
 * dialog hops each report onto the UI dispatcher.
 */
internal suspend fun downloadInstaller(downloadUrl: String, report: suspend (DownloadState) -> Unit) {
    var tempFile: File? = null
    try {
        val url = URI(downloadUrl).toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.connect()

        val contentLength = connection.contentLengthLong
        val suffix = installerSuffixFor(downloadUrl)
        // NB: do not deleteOnExit() — the installer is launched as the
        // app exits via exitProcess(0), and the shutdown hook would
        // delete the file out from under the installer.
        val file = File.createTempFile(UPDATE_INSTALLER_PREFIX, suffix).also { tempFile = it }

        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                copyReportingProgress(input, output, contentLength) { progress ->
                    report(DownloadState.Downloading(progress))
                }
            }
        }
        connection.disconnect()
        report(DownloadState.Done(file))
    } catch (e: CancellationException) {
        // Cancelled from the window: a half-written installer must not be left for the next launch.
        tempFile?.delete()
        throw e
    } catch (e: IOException) {
        // The connection, the download or the temp file -- a malformed URL is one too.
        report(DownloadState.Error(e.message ?: "Download failed"))
    } catch (e: IllegalArgumentException) {
        report(DownloadState.Error(e.message ?: "Download failed"))
    }
}

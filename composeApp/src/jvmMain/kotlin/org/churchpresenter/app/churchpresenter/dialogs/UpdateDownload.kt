package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.app.churchpresenter.utils.UPDATE_INSTALLER_PREFIX
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000

/**
 * Downloads the installer at [downloadUrl] into a temp file, reporting each stage through [report]:
 * progress as the bytes land, then [DownloadState.Done] with the file, or [DownloadState.Error] when
 * the connection, the download or the temp file fails -- a malformed URL included.
 *
 * Split out of [UpdateAvailableDialog] so the download can be driven against a local server; the
 * dialog hops each report onto the UI dispatcher.
 */
internal suspend fun downloadInstaller(downloadUrl: String, report: suspend (DownloadState) -> Unit) {
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
        val tempFile = File.createTempFile(UPDATE_INSTALLER_PREFIX, suffix)

        connection.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                copyReportingProgress(input, output, contentLength) { progress ->
                    report(DownloadState.Downloading(progress))
                }
            }
        }
        connection.disconnect()
        report(DownloadState.Done(tempFile))
    } catch (e: IOException) {
        // The connection, the download or the temp file -- a malformed URL is one too.
        report(DownloadState.Error(e.message ?: "Download failed"))
    } catch (e: IllegalArgumentException) {
        report(DownloadState.Error(e.message ?: "Download failed"))
    }
}

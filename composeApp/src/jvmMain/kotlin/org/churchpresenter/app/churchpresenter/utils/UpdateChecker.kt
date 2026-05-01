package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.BuildConfig
import java.net.HttpURLConnection
import java.net.URI

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val releaseNotes: String
)

object UpdateChecker {

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/ChurchPresenter/ChurchPresenter/releases/latest"
    private const val RELEASES_URL =
        "https://github.com/ChurchPresenter/ChurchPresenter/releases/latest"

    /**
     * Checks GitHub for a newer release.
     * Returns [UpdateInfo] if a newer version is available, null otherwise.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URI(GITHUB_API_URL).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ChurchPresenter/${BuildConfig.APP_VERSION}")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000

            if (connection.responseCode != 200) return@withContext null

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            // Parse tag_name (e.g. "v2025.1.0" or "2025.1.0")
            val tagName = parseJsonString(body, "tag_name") ?: return@withContext null
            val latestVersion = tagName.removePrefix("v")
            val releaseNotes = parseJsonString(body, "body") ?: ""
            val releaseUrl = parseJsonString(body, "html_url") ?: RELEASES_URL

            if (isNewerVersion(latestVersion, BuildConfig.APP_VERSION)) {
                UpdateInfo(
                    latestVersion = latestVersion,
                    releaseUrl = releaseUrl,
                    releaseNotes = releaseNotes.take(500)
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Simple JSON string field extractor (avoids adding a JSON dependency).
     */
    private fun parseJsonString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern.find(json)?.groupValues?.getOrNull(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    /**
     * Compares two version strings in YYYY.MAJOR.MINOR format.
     * Returns true if [latest] is newer than [current].
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val size = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}


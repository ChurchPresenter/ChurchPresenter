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
     * Uses manual parsing instead of regex to avoid StackOverflowError on long values.
     */
    private fun parseJsonString(json: String, key: String): String? {
        val needle = "\"$key\""
        val keyIdx = json.indexOf(needle)
        if (keyIdx < 0) return null
        // Skip past key, optional whitespace, colon, optional whitespace, opening quote
        var i = keyIdx + needle.length
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != ':') return null
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        i++ // skip opening quote
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                val next = json[i + 1]
                when (next) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    else -> { sb.append('\\'); sb.append(next) }
                }
                i += 2
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
                i++
            }
        }
        return null
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


package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.BuildConfig
import java.net.HttpURLConnection
import java.net.URI

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val downloadUrl: String? = null,
    val isPrerelease: Boolean = false
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
}

/**
 * How often the automatic startup check is allowed to run. Manual "Check for Updates…"
 * always runs regardless of this setting — it only gates the silent background check.
 */
@Serializable
enum class UpdateCheckInterval(private val days: Int?) {
    EVERY_LAUNCH(0),
    WEEKLY(7),
    MONTHLY(30),
    EVERY_2_MONTHS(60),
    EVERY_3_MONTHS(90),
    EVERY_6_MONTHS(180),
    NEVER(null);

    fun isDueSince(lastCheckedAtMillis: Long): Boolean {
        val intervalDays = days ?: return false
        if (intervalDays == 0) return true
        val elapsedMillis = System.currentTimeMillis() - lastCheckedAtMillis
        return elapsedMillis >= intervalDays * 24L * 60 * 60 * 1000
    }
}

object UpdateChecker {

    private const val RELEASES_API_URL =
        "https://api.github.com/repos/ChurchPresenter/ChurchPresenter/releases?per_page=50"
    const val RELEASES_URL =
        "https://github.com/ChurchPresenter/ChurchPresenter/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks GitHub for a newer release that has an installer for the current OS.
     *
     * Walks releases newest→oldest and stops at the first one that contains a
     * download for the detected platform — i.e. the latest build available for this
     * OS, skipping newer releases that omit it. Returns [UpdateCheckResult.Available]
     * (with a non-null [UpdateInfo.downloadUrl]) only when that build is newer than the
     * running app, so users are never prompted toward a release they can't actually
     * install. [UpdateCheckResult.UpToDate] covers both "genuinely up to date" and
     * "request/parse failed" — this mirrors the previous nullable return and is
     * intentional, since there's no user-facing distinction between the two today.
     *
     * When [includePrereleases] is true, releases flagged `prerelease` on GitHub are
     * eligible too; otherwise they're skipped exactly like drafts.
     */
    suspend fun checkForUpdate(includePrereleases: Boolean): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URI(RELEASES_API_URL).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ChurchPresenter/${BuildConfig.APP_VERSION}")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000

            if (connection.responseCode != 200) return@withContext UpdateCheckResult.UpToDate

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            // GitHub returns releases sorted by created_at descending.
            val releases = json.parseToJsonElement(body).jsonArray
            for (rel in releases) {
                val obj = rel.jsonObject
                if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true) continue
                val isPrerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true
                if (isPrerelease && !includePrereleases) continue

                val urls = (obj["assets"]?.jsonArray ?: continue)
                    .mapNotNull { it.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull }
                // Skip releases that have no installer for the detected OS.
                val downloadUrl = selectDownloadUrl(urls) ?: continue

                // First OS-matching release = latest build available for this OS.
                val latestVersion = (obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: continue)
                    .removePrefix("v")
                return@withContext if (isNewerVersion(latestVersion, BuildConfig.APP_VERSION)) {
                    UpdateCheckResult.Available(
                        UpdateInfo(
                            latestVersion = latestVersion,
                            releaseUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: RELEASES_URL,
                            releaseNotes = (obj["body"]?.jsonPrimitive?.contentOrNull ?: "").take(500),
                            downloadUrl = downloadUrl,
                            isPrerelease = isPrerelease
                        )
                    )
                } else {
                    UpdateCheckResult.UpToDate
                }
            }
            UpdateCheckResult.UpToDate
        } catch (_: Exception) {
            UpdateCheckResult.UpToDate
        }
    }

    private fun selectDownloadUrl(urls: List<String>): String? {
        val os = System.getProperty("os.name", "").lowercase()
        val arch = System.getProperty("os.arch", "").lowercase()
        return when {
            os.contains("win") ->
                urls.firstOrNull { it.endsWith(".msi", ignoreCase = true) }
            os.contains("mac") && arch == "aarch64" ->
                urls.firstOrNull { it.contains("arm64", ignoreCase = true) && it.endsWith(".dmg", ignoreCase = true) }
            os.contains("mac") ->
                urls.firstOrNull { !it.contains("arm64", ignoreCase = true) && it.endsWith(".dmg", ignoreCase = true) }
            else ->
                urls.firstOrNull { it.endsWith(".deb", ignoreCase = true) }
        }
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


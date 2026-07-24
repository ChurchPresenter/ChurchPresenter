package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import kotlin.time.Duration.Companion.seconds

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
    // Count-only beacon on churchpresenter.org that attributes downloads to the
    // app's updater (vs. the website's download buttons vs. GitHub directly).
    private const val DOWNLOAD_BEACON_URL =
        "https://www.churchpresenter.org/api/download"

    private val json = Json { ignoreUnknownKeys = true }

    private val beaconScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
    suspend fun checkForUpdate(includePrereleases: Boolean): UpdateCheckResult =
        checkForUpdate(includePrereleases, RELEASES_API_URL)

    internal suspend fun checkForUpdate(includePrereleases: Boolean, apiUrl: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val body = fetchReleasesFrom(apiUrl) ?: return@withContext UpdateCheckResult.UpToDate
            selectUpdate(body, includePrereleases, BuildConfig.APP_VERSION)
        }

    internal fun fetchReleasesFrom(apiUrl: String): String? {
        return try {
            val url = URI(apiUrl).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ChurchPresenter/${BuildConfig.APP_VERSION}")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000

            if (connection.responseCode != 200) return null

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            body
        } catch (_: Exception) {
            null
        }
    }

    internal fun selectUpdate(body: String, includePrereleases: Boolean, currentVersion: String): UpdateCheckResult {
        return try {
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
                return if (isNewerVersion(latestVersion, currentVersion)) {
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

    // The website's platform naming for the four installer builds — same
    // branching as selectDownloadUrl, so the beacon reports the platform whose
    // installer is actually being downloaded.
    private fun currentPlatformId(): String {
        val os = System.getProperty("os.name", "").lowercase()
        val arch = System.getProperty("os.arch", "").lowercase()
        return when {
            os.contains("win") -> "windows"
            os.contains("mac") && arch == "aarch64" -> "macos_arm64"
            os.contains("mac") -> "macos_x64"
            else -> "linux"
        }
    }

    // Same quick-retry shape as LiveMapReporter: a transient blip at the moment
    // the user clicks Download shouldn't drop the beacon. No slow retry — the
    // app exits to launch the installer soon after, killing the coroutine.
    private const val QUICK_ATTEMPTS = 3
    private val QUICK_RETRY_DELAY = 5.seconds

    /**
     * Fire-and-forget beacon telling churchpresenter.org that an in-app update
     * download started, so app-updater downloads can be counted separately from
     * website and GitHub-direct downloads. Counting only: the installer itself
     * still downloads straight from GitHub, so a failure here (offline stats
     * endpoint, blocked network) never affects the update — it's retried a few
     * times in quick succession, then dropped. Skipped for dev builds so test
     * downloads don't skew the public stats — the same IS_RELEASE signal
     * LiveMapReporter uses for ?src=dev.
     */
    fun reportDownloadStarted(version: String) {
        if (!BuildConfig.IS_RELEASE) return
        beaconScope.launch {
            // True once the server answered at all — any HTTP status counts,
            // since a 4xx/5xx means the request arrived and a retry won't help.
            fun tryBeacon(): Boolean = try {
                val url = URI("$DOWNLOAD_BEACON_URL?platform=${currentPlatformId()}&source=app&version=$version").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                // The website's CSRF middleware 403s cross-origin POSTs with a
                // form-like (or absent) content type; JSON passes it.
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChurchPresenter/${BuildConfig.APP_VERSION}")
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.responseCode // send the request
                connection.disconnect()
                true
            } catch (_: Exception) {
                // Non-fatal — silently ignore network errors.
                false
            }

            repeat(QUICK_ATTEMPTS) { attempt ->
                if (tryBeacon()) return@launch
                if (attempt < QUICK_ATTEMPTS - 1) delay(QUICK_RETRY_DELAY)
            }
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


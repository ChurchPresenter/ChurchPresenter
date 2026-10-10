package org.churchpresenter.updater

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val CONNECT_TIMEOUT_MS = 5_000
private const val READ_TIMEOUT_MS = 5_000
private const val HTTP_OK = 200
/**
 * A ceiling on the release notes kept, against a runaway body — not a length to show. The window's
 * notes panel scrolls, and a release's notes run to a few thousand characters.
 */
private const val RELEASE_NOTES_MAX_CHARS = 20_000

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val releaseNotes: String,
    val downloadUrl: String? = null,
    val isPrerelease: Boolean = false,
    /** The version running now, so the window can show the jump from one to the other. */
    val currentVersion: String = "",
    /** When GitHub published the release, or null when it did not say. */
    val publishedAt: Instant? = null,
    /** The installer's size in bytes, or null when GitHub did not say. */
    val downloadSize: Long? = null,
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
}

object UpdateChecker {

    private const val RELEASES_API_URL =
        "https://api.github.com/repos/ChurchPresenter/ChurchPresenter/releases?per_page=50"
    const val RELEASES_URL =
        "https://github.com/ChurchPresenter/ChurchPresenter/releases/latest"
    /** Where the pull requests a release's notes name are, by number. */
    const val PULLS_URL = "https://github.com/ChurchPresenter/ChurchPresenter/pull"
    // Count-only beacon on churchpresenter.org that attributes downloads to the
    // app's updater (vs. the website's download buttons vs. GitHub directly).
    private const val DOWNLOAD_BEACON_URL =
        "https://www.churchpresenter.org/api/download"

    private val json = Json { ignoreUnknownKeys = true }

    private val beaconScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** What this build is; set once at startup by [initialize]. */
    @Volatile
    private var identity = UpdaterIdentity()

    /** Tells the updater which build is running. `main.kt` calls it once, before any check. */
    fun initialize(identity: UpdaterIdentity) {
        this.identity = identity
    }

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

    internal suspend fun checkForUpdate(
        includePrereleases: Boolean,
        apiUrl: String,
        storeInstall: Boolean = StorePackage.isInstalled,
    ): UpdateCheckResult =
        // The Store updates its own installs; installing a release MSI over one would leave two copies.
        if (storeInstall) UpdateCheckResult.UpToDate else withContext(Dispatchers.IO) {
            val body = fetchReleasesFrom(apiUrl) ?: return@withContext UpdateCheckResult.UpToDate
            selectUpdate(body, includePrereleases, identity.appVersion)
        }

    internal fun fetchReleasesFrom(apiUrl: String): String? {
        return try {
            val url = URI(apiUrl).toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "ChurchPresenter/${identity.appVersion}")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            if (connection.responseCode != HTTP_OK) return null

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
            // First OS-matching release = latest build available for this OS.
            val candidate = json.parseToJsonElement(body).jsonArray
                .firstNotNullOfOrNull { installableRelease(it.jsonObject, includePrereleases) }
                ?: return UpdateCheckResult.UpToDate
            if (isNewerVersion(candidate.latestVersion, currentVersion)) {
                UpdateCheckResult.Available(candidate.copy(currentVersion = currentVersion))
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (_: Exception) {
            UpdateCheckResult.UpToDate
        }
    }

    /**
     * The installer for the detected OS among the release's assets, with its size when GitHub gave
     * one, or null when the release has none.
     */
    private fun installerAsset(obj: JsonObject): Pair<String, Long?>? {
        val assets = obj["assets"]?.jsonArray?.map { it.jsonObject } ?: return null
        val sizes = assets.mapNotNull { asset ->
            asset["browser_download_url"]?.jsonPrimitive?.contentOrNull?.let { url ->
                url to asset["size"]?.jsonPrimitive?.longOrNull
            }
        }.toMap()
        val url = selectDownloadUrl(sizes.keys.toList()) ?: return null
        return url to sizes[url]
    }

    /**
     * The release as an [UpdateInfo], or null when it is a draft, a pre-release the caller does not
     * want, or carries no installer for the detected OS.
     */
    private fun installableRelease(obj: JsonObject, includePrereleases: Boolean): UpdateInfo? {
        if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
        val (downloadUrl, downloadSize) = installerAsset(obj) ?: return null
        val latestVersion = obj["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v") ?: return null
        // A tag that is not `YY.MAJOR.MINOR` — the rolling `nightly` pre-release — can never be newer
        // than anything, and because it is re-created every night it is always the newest entry in
        // the list. Returning it would stop the walk there and hide every real pre-release behind it.
        if (latestVersion.split(".").any { it.toIntOrNull() == null }) return null

        val isPrerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull == true
        if (isPrerelease && !includePrereleases) return null

        return UpdateInfo(
            latestVersion = latestVersion,
            releaseUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: RELEASES_URL,
            releaseNotes = capReleaseNotes(obj["body"]?.jsonPrimitive?.contentOrNull ?: ""),
            downloadUrl = downloadUrl,
            isPrerelease = isPrerelease,
            publishedAt = obj["published_at"]?.jsonPrimitive?.contentOrNull?.let(::parseInstantOrNull),
            downloadSize = downloadSize,
        )
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
        reportDownloadStarted(version, identity)
    }

    /**
     * [reportDownloadStarted] with what it reads handed in: the build, where the beacon goes and the
     * pause between attempts. Returns the sending job, or null for a dev build, so a test can wait
     * for the attempts to finish rather than for a clock.
     */
    internal fun reportDownloadStarted(
        version: String,
        identity: UpdaterIdentity,
        beaconUrl: String = DOWNLOAD_BEACON_URL,
        retryDelay: Duration = QUICK_RETRY_DELAY,
    ): Job? {
        if (!identity.isRelease) return null
        return beaconScope.launch {
            // True once the server answered at all — any HTTP status counts,
            // since a 4xx/5xx means the request arrived and a retry won't help.
            fun tryBeacon(): Boolean = try {
                val url = URI("$beaconUrl?platform=${currentPlatformId()}&source=app&version=$version")
                    .toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                // The website's CSRF middleware 403s cross-origin POSTs with a
                // form-like (or absent) content type; JSON passes it.
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChurchPresenter/${identity.appVersion}")
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.responseCode // send the request
                connection.disconnect()
                true
            } catch (_: Exception) {
                // Non-fatal — silently ignore network errors.
                false
            }

            repeat(QUICK_ATTEMPTS) { attempt ->
                if (tryBeacon()) return@launch
                if (attempt < QUICK_ATTEMPTS - 1) delay(retryDelay)
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

/** The installer among [urls] for the OS and CPU this app is running on, or null. */
internal fun selectDownloadUrl(urls: List<String>): String? {
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
internal fun currentPlatformId(): String {
    val os = System.getProperty("os.name", "").lowercase()
    val arch = System.getProperty("os.arch", "").lowercase()
    return when {
        os.contains("win") -> "windows"
        os.contains("mac") && arch == "aarch64" -> "macos_arm64"
        os.contains("mac") -> "macos_x64"
        else -> "linux"
    }
}

/** [text] as an instant, or null when it is not an ISO-8601 timestamp. */
internal fun parseInstantOrNull(text: String): Instant? =
    try {
        Instant.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

/**
 * [notes] whole when they fit under [max] characters, or cut back to the last whole line that does, so
 * a group or a bullet is never shown half-written.
 */
internal fun capReleaseNotes(notes: String, max: Int = RELEASE_NOTES_MAX_CHARS): String {
    if (notes.length <= max) return notes
    val cut = notes.take(max)
    val lastBreak = cut.lastIndexOf('\n')
    return if (lastBreak > 0) cut.take(lastBreak).trimEnd() else cut
}

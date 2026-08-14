package org.churchpresenter.app.churchpresenter.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.hasNoPrimaryTarget
import org.churchpresenter.app.churchpresenter.viewmodel.FileManager
import java.awt.GraphicsEnvironment
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Sends an anonymous, city-level ping to the ChurchPresenter live world map
 * (churchpresenter.org/map) when the app is opened.
 *
 * No personal data is transmitted — Cloudflare derives a city-level coordinate
 * server-side from the network layer. No IP address is stored.
 */
object LiveMapReporter {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val http by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
            }
        }
    }

    private const val PING_URL = "https://www.churchpresenter.org/api/ping"

    // If the first ping fails (e.g. network isn't up yet at launch), retry a
    // few times in quick succession, then fall back to a slow, long-running
    // retry rather than giving up until the next app launch.
    private const val QUICK_ATTEMPTS = 3
    private val QUICK_RETRY_DELAY = 5.seconds
    private const val SLOW_ATTEMPTS = 15
    private val SLOW_RETRY_DELAY = 10.minutes

    // BuildConfig.IS_RELEASE is true only for packaged installer builds (see the
    // generateBuildConfig task in build.gradle.kts). A `run`/IDE launch is a
    // developer build, which pings with ?src=dev so test launches are tracked
    // separately and don't skew real-user stats on the live map.
    private val isDevBuild: Boolean = !BuildConfig.IS_RELEASE

    // Same os.name convention already used in AutoStartManager.kt/UpdateChecker.kt.
    // Explicitly checks for "linux" rather than treating it as the else-case,
    // matching the website's own os validation (unrecognized -> "unknown"
    // rather than assumed) instead of guessing for anything unexpected.
    private val os: String = osTag(System.getProperty("os.name", ""))

    internal fun osTag(osName: String): String = osName.lowercase().let {
        when {
            it.contains("win") -> "windows"
            it.contains("mac") -> "macos"
            it.contains("linux") -> "linux"
            else -> "unknown"
        }
    }

    /**
     * How the install is set up, in counts and booleans only — never a display name, file name,
     * host or address, so this stays as anonymous as the ping it rides on.
     *
     * Sent so the project can see how many operators drive more than one screen, how large a
     * translation stack a real setup carries, and whether the stage monitor and the lower-third
     * output mode are used at all rather than guessed at.
     */
    data class SetupFacts(
        /** Interface language code, e.g. "en" — which of the shipped locales are actually in use. */
        val language: String = "",
        val screens: Int = 0,
        val bibles: Int = 0,
        val stageMonitor: Boolean = false,
        val lowerThird: Boolean = false,
        /** Songbooks indexed, and songs across all of them. */
        val songbooks: Int = 0,
        val songs: Int = 0,
        /** How long the previous run lasted, in minutes; 0 when unknown. */
        val sessionMinutes: Int = 0,
    )

    /**
     * Songbook and song counts, read from the song folder.
     *
     * Touches the disk, so it is called from inside the ping coroutine rather than on the startup
     * path — a library on a slow network share must not delay the app opening. Counts only: no
     * folder name, no path, no title.
     */
    internal fun gatherSongCounts(settings: AppSettings): Pair<Int, Int> = try {
        val folders = FileManager().getSongFoldersInDirectory(settings.songSettings.storageDirectory)
        folders.size to folders.sumOf { it.second }
    } catch (_: Exception) {
        0 to 0
    }

    /** Displays attached right now, or 0 when they can't be enumerated (headless, no window server). */
    internal fun detectScreenCount(): Int = try {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size
    } catch (_: Exception) {
        0
    }

    /**
     * @param screenCount displays attached, from [detectScreenCount] — taken as a parameter so the
     * derivation itself stays pure and testable on a machine with no display.
     */
    internal fun setupFacts(
        settings: AppSettings,
        screenCount: Int,
        songCounts: Pair<Int, Int> = 0 to 0,
        sessionMinutes: Int = 0,
    ): SetupFacts {
        // Outputs with no display chosen are configuration the operator has switched off. Reading
        // their display mode would report a lower third or a stage monitor nobody is projecting.
        val activeOutputs = settings.projectionSettings.screenAssignments.filterNot(::hasNoPrimaryTarget)
        return SetupFacts(
            language = settings.language,
            screens = screenCount,
            bibles = settings.bibleSettings.translationList().size,
            stageMonitor = activeOutputs.any { it.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR },
            lowerThird = activeOutputs.any { it.isLowerThird },
            songbooks = songCounts.first,
            songs = songCounts.second,
            sessionMinutes = sessionMinutes,
        )
    }

    /**
     * The events this instance may report — all of them, or none at all.
     *
     * An instance with Instance Link enabled is attached to another one: it either mirrors that
     * primary or drives it, and either way the same service is already being reported by the
     * primary's own launch. Only the master reports, so one Sunday is counted once rather than once
     * per room.
     */
    internal fun eventsToReport(
        settings: AppSettings,
        pending: Map<UsageEvent, Int>,
    ): Map<UsageEvent, Int> = if (settings.instanceLink.enabled) emptyMap() else pending

    /**
     * @param repoSlug The git origin this build was made from, as "owner/name"
     * (see the build-provenance helpers in build.gradle.kts). The app is
     * open-source and hardcodes [PING_URL], so without this a fork's launches
     * are indistinguishable from real installs on the live map. Only the slug
     * is sent — never the remote URL, which can embed credentials.
     * @param commit Short commit hash the build was made from.
     * @param buildType "release", "snapshot", "dirty" or "nogit" — separates a
     * real end-user run from a self-compiled one, including inside a fork
     * (src=dev only covers our own IDE launches).
     *
     * Each is omitted when unknown; the server treats a missing value the same
     * as an unrecognised one and still counts the ping.
     */
    internal fun buildPingUrl(
        os: String,
        version: String,
        updateCheckInterval: UpdateCheckInterval?,
        isDevBuild: Boolean,
        repoSlug: String = BuildConfig.REPO_SLUG,
        commit: String = BuildConfig.COMMIT_HASH,
        buildType: String = BuildConfig.BUILD_TYPE,
        setup: SetupFacts = SetupFacts(),
        events: Map<UsageEvent, Int> = emptyMap(),
    ): String = buildString {
        append(PING_URL)
        append("?platform=desktop")
        append("&os=$os")
        append("&version=$version")
        if (updateCheckInterval != null) append("&updateCheck=${updateCheckInterval.name.lowercase()}")
        if (isDevBuild) append("&src=dev")
        appendBuildFacts(repoSlug, commit, buildType)
        appendSetupFacts(setup)
        appendEventCounts(events)
    }

    private fun StringBuilder.appendBuildFacts(repoSlug: String, commit: String, buildType: String) {
        if (repoSlug.isNotBlank() && repoSlug != "unknown") append("&repo=$repoSlug")
        if (commit.isNotBlank() && commit != "unknown") append("&commit=$commit")
        if (buildType.isNotBlank() && buildType != "unknown") append("&build=$buildType")
    }

    // Omitted rather than sent as 0/false when unknown or unused, so the server can tell a setup
    // that has none from a build too old to report it.
    private fun StringBuilder.appendSetupFacts(setup: SetupFacts) {
        if (setup.language.isNotBlank()) append("&lang=${setup.language}")
        if (setup.screens > 0) append("&screens=${setup.screens}")
        if (setup.bibles > 0) append("&bibles=${setup.bibles}")
        if (setup.stageMonitor) append("&stageMonitor=true")
        if (setup.lowerThird) append("&lowerThird=true")
        if (setup.songbooks > 0) append("&songbooks=${setup.songbooks}")
        if (setup.songs > 0) append("&songs=${setup.songs}")
        if (setup.sessionMinutes > 0) append("&sessionMinutes=${setup.sessionMinutes}")
    }

    // Everything above describes the session that is starting. These are things that already
    // happened, counted since the last ping the server took, so they can simply be added up.
    private fun StringBuilder.appendEventCounts(events: Map<UsageEvent, Int>) {
        UsageEvent.entries.forEach { event ->
            val count = events[event] ?: 0
            if (count > 0) append("&${event.param}=$count")
        }
    }

    /**
     * @param installId Stable anonymous install id, sent as the X-Install-Id
     * header so the server dedupes repeat launches to one row per install. Pass
     * null (the default) to opt out — the server then falls back to a coarse
     * geo-grid dedupe. Callers should only pass an id when analytics is enabled.
     * @param updateCheckInterval The user's configured UpdateCheckInterval
     * setting (see UpdateChecker.kt), sent as-is — this is informational only,
     * not tied to analytics opt-in/out.
     * @param setup Anonymous counts and booleans describing the setup — see [SetupFacts]. Like
     * [updateCheckInterval] this carries nothing that identifies an install, so it is not tied to
     * the analytics opt-in either; the install id above still is.
     * @param events [UsageEvent] counts that have happened since the last delivered ping — see
     * [UsageEventStore]. The ping itself is the record that a session started; these are the record
     * of what was done in the sessions before it.
     * @param onDelivered Run once the server has actually taken the ping, so [events] are marked
     * reported only then. A launch with no network reports them again next time rather than losing
     * them, and nothing is ever counted twice.
     */
    fun pingOnOpen(
        installId: String? = null,
        updateCheckInterval: UpdateCheckInterval? = null,
        setup: () -> SetupFacts = { SetupFacts() },
        events: Map<UsageEvent, Int> = emptyMap(),
        onDelivered: () -> Unit = {},
    ) {
        scope.launch {
            // Gathered here rather than at the call site: it enumerates displays and scans the song
            // folder, neither of which belongs on the path between launching and showing a window.
            val url = buildPingUrl(
                os, BuildConfig.APP_VERSION, updateCheckInterval, isDevBuild,
                setup = setup(), events = events,
            )
            if (ping(url, installId)) onDelivered()
        }
    }

    /** @return true once the server has taken the ping, false if every attempt failed. */
    internal suspend fun ping(url: String, installId: String?): Boolean {
        suspend fun tryPing(): Boolean = try {
            http.get(url) {
                if (!installId.isNullOrBlank()) header("X-Install-Id", installId)
            }
            true
        } catch (_: Exception) {
            // Non-fatal — silently ignore network errors.
            false
        }
        return pingWithRetry(::tryPing)
    }

    internal suspend fun pingWithRetry(
        tryPing: suspend () -> Boolean,
        quickAttempts: Int = QUICK_ATTEMPTS,
        quickDelay: Duration = QUICK_RETRY_DELAY,
        slowAttempts: Int = SLOW_ATTEMPTS,
        slowDelay: Duration = SLOW_RETRY_DELAY,
    ): Boolean {
        repeat(quickAttempts) { attempt ->
            if (tryPing()) return true
            if (attempt < quickAttempts - 1) delay(quickDelay)
        }
        repeat(slowAttempts) {
            delay(slowDelay)
            if (tryPing()) return true
        }
        return false
    }
}

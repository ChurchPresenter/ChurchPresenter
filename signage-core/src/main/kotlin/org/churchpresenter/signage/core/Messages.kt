package org.churchpresenter.signage.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a player sends over `/api/signage/ws`. The first message on every connection is [Hello].
 * A paired player puts its token in the `Authorization: Bearer` header of the WebSocket request; an
 * unpaired one connects without it and puts a [Hello.pairingCode] in its hello.
 */
@Serializable
sealed interface PlayerMessage {
    @Serializable
    @SerialName("hello")
    data class Hello(
        /** Random, made once at install and kept for the life of the player. */
        val playerId: String,
        val agentVersion: String,
        val protocolMin: Int,
        val protocolMax: Int,
        /** The version of the manifest it is playing, or null when it has none yet. */
        val manifestVersion: String? = null,
        val screenWidth: Int,
        val screenHeight: Int,
        val platform: Platform,
        /** Shown on the TV and in the Signage tab while an unpaired player waits for approval. */
        val pairingCode: String? = null,
        /** What it can do beyond the basics, from [Features]; ChurchPresenter offers only those. */
        val features: List<String> = emptyList(),
    ) : PlayerMessage

    @Serializable
    @SerialName("status")
    data class Status(
        val showingPlaylistId: String? = null,
        val showingItemSha256: String? = null,
        val reason: String? = null,
        val lastSyncMs: Long? = null,
        val freeDiskBytes: Long? = null,
        val cec: CecState = CecState.UNKNOWN,
        val errors: List<String> = emptyList(),
    ) : PlayerMessage

    /** The answer to [ServerMessage.PreviewRequest]: what the screen shows, as a small JPEG. */
    @Serializable
    @SerialName("preview")
    data class Preview(
        val jpegBase64: String,
        val width: Int,
        val height: Int,
        val takenAtMs: Long,
    ) : PlayerMessage

    /**
     * The answer to [ServerMessage.WebTest]: for each frame rate tried, what reached the screen and
     * what it cost. [error] says why there are no runs, when the test could not be done.
     */
    @Serializable
    @SerialName("web_test_result")
    data class WebTestResult(
        val runs: List<WebTestRun> = emptyList(),
        val error: String? = null,
    ) : PlayerMessage

    /** How an update sent with [ServerMessage.UpdateAvailable] is going. */
    @Serializable
    @SerialName("update_progress")
    data class UpdateProgress(
        val version: String,
        val state: UpdateState,
        /** Why it failed or was rolled back; a percentage while downloading. */
        val message: String? = null,
    ) : PlayerMessage
}

/**
 * One frame rate of a [PlayerMessage.WebTestResult]: the test page drawn at most [fps] frames a
 * second, and on average [shownFps] reached the screen.
 */
@Serializable
data class WebTestRun(
    val fps: Int,
    val shownFps: Double,
    /** How busy the whole processor was, 0–100; null where the player cannot tell. */
    val cpuPercent: Double? = null,
    /** The hottest the processor got, in °C; null where the player cannot tell. */
    val maxTempC: Double? = null,
)

/** What a player lists in [PlayerMessage.Hello.features]. */
object Features {
    /** Answers [ServerMessage.PreviewRequest]. */
    const val PREVIEW = "preview"

    /** Installs [ServerMessage.UpdateAvailable] and reports [PlayerMessage.UpdateProgress]. */
    const val UPDATE = "update"

    /** Answers [ServerMessage.Wipe]. */
    const val WIPE = "wipe"

    /** Plays [MediaKind.STREAM] items. */
    const val STREAM = "stream"

    /** Draws [MediaKind.WEB] items: listed only when the player has a Chromium. */
    const val WEB = "web"

    /** Answers [ServerMessage.WebTest]; listed with [WEB]. */
    const val WEB_TEST = "web_test"
}

@Serializable
enum class UpdateState {
    @SerialName("downloading") DOWNLOADING,

    @SerialName("checking") CHECKING,

    @SerialName("installing") INSTALLING,

    /** Installed; the player restarts into it and says hello with the new version. */
    @SerialName("restarting") RESTARTING,

    @SerialName("failed") FAILED,

    /** The new version did not start healthily, so the player went back to the one before. */
    @SerialName("rolled_back") ROLLED_BACK,
}

/** What ChurchPresenter sends a player. */
@Serializable
sealed interface ServerMessage {
    /** The player's protocol range does not include ChurchPresenter's; the connection closes. */
    @Serializable
    @SerialName("unsupported")
    data class Unsupported(val serverProtocol: Int) : ServerMessage

    /** An unpaired player is waiting for someone to approve [code] in the Signage tab. */
    @Serializable
    @SerialName("pairing_pending")
    data class PairingPending(val code: String) : ServerMessage

    /** Approved. The player stores [token] and [ownerId] and reconnects with the token. */
    @Serializable
    @SerialName("paired")
    data class Paired(val token: String, val ownerId: String, val ownerName: String) : ServerMessage

    @Serializable
    @SerialName("pairing_rejected")
    data object PairingRejected : ServerMessage

    /** Revoked or released: the player forgets its owner and goes back to waiting for pairing. */
    @Serializable
    @SerialName("released")
    data object Released : ServerMessage

    /** A newer manifest is waiting at `GET /api/signage/manifest`. */
    @Serializable
    @SerialName("manifest_changed")
    data class ManifestChanged(val version: String) : ServerMessage

    /** A manual switch; null releases the current one. */
    @Serializable
    @SerialName("override")
    data class SetOverride(val override: ScreenOverride?) : ServerMessage

    /** Shows the screen's name full screen for a few seconds, to tell screens apart. */
    @Serializable
    @SerialName("identify")
    data class Identify(val seconds: Int = DEFAULT_IDENTIFY_SECONDS) : ServerMessage {
        companion object {
            const val DEFAULT_IDENTIFY_SECONDS = 10
        }
    }

    /** Restarts what draws the screen. */
    @Serializable
    @SerialName("refresh")
    data object Refresh : ServerMessage

    /**
     * Deletes everything the player has stored (pictures, videos, manifests, a manual switch) and
     * fetches its manifest again; it stays paired. Sent with a manifest that names nothing to play,
     * the screen is left on its idle card holding no content.
     */
    @Serializable
    @SerialName("wipe")
    data object Wipe : ServerMessage

    /**
     * Shows a built-in moving test page at each of [fps] frames a second for [secondsEach] seconds,
     * then goes back to what it played and answers with [PlayerMessage.WebTestResult].
     */
    @Serializable
    @SerialName("web_test")
    data class WebTest(
        val fps: List<Int> = DEFAULT_FPS,
        val secondsEach: Int = DEFAULT_SECONDS_EACH,
    ) : ServerMessage {
        companion object {
            val DEFAULT_FPS = listOf(15, 30)
            const val DEFAULT_SECONDS_EACH = 20
        }
    }

    /** Asks for a [PlayerMessage.Preview] no wider than [maxWidth]. Sent only while someone looks. */
    @Serializable
    @SerialName("preview_request")
    data class PreviewRequest(val maxWidth: Int = DEFAULT_PREVIEW_WIDTH) : ServerMessage {
        companion object {
            const val DEFAULT_PREVIEW_WIDTH = 480
        }
    }

    /**
     * Install this agent build, fetched from [path] on ChurchPresenter. The player believes only
     * [release], the release index exactly as published, once [releaseSignature] checks out against
     * the key it was built with; the build must then hash to that index's entry for its platform.
     */
    @Serializable
    @SerialName("update_available")
    data class UpdateAvailable(
        val agentVersion: String,
        val protocolMin: Int,
        val protocolMax: Int,
        val sha256: String,
        val path: String,
        val release: String = "",
        val releaseSignature: String = "",
    ) : ServerMessage
}

@Serializable
enum class Platform {
    @SerialName("linux_arm64") LINUX_ARM64,

    @SerialName("linux_x64") LINUX_X64,

    @SerialName("windows_x64") WINDOWS_X64,
}

@Serializable
enum class CecState {
    @SerialName("unknown") UNKNOWN,

    @SerialName("working") WORKING,

    @SerialName("unavailable") UNAVAILABLE,
}

/** Whether a player speaking [min]..[max] can talk to this build. */
fun supportsProtocol(min: Int, max: Int, protocol: Int = PROTOCOL_VERSION): Boolean = protocol in min..max

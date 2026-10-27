package org.churchpresenter.signage.core

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything one screen needs to run on its own: its playlists, its weekly schedule and what to fall
 * back on. ChurchPresenter writes one per screen; the player keeps the last complete one on disk and
 * plays from it whether or not ChurchPresenter is reachable.
 *
 * Files are named by their SHA-256 only; the player fetches each from
 * `GET /api/signage/files/{sha256}` and checks the hash before using it. A link (a stream or a web
 * page) is no file: the player opens its URL when it plays.
 */
@Serializable
data class Manifest(
    val protocol: Int = PROTOCOL_VERSION,
    /** The id of the ChurchPresenter that owns this screen; a player ignores manifests from any other. */
    val ownerId: String,
    val screen: ScreenSettings,
    val playlists: List<Playlist> = emptyList(),
    val schedule: List<ScheduleBlock> = emptyList(),
    /** Plays outside every schedule block, and whenever the wanted playlist cannot play. */
    val defaultPlaylistId: String? = null,
    val idleCard: IdleCard = IdleCard(),
    /** An IANA zone id (`"America/Chicago"`); schedule times are wall-clock times in it. */
    val timeZone: String,
    /**
     * Whether the screen may send crash reports: the owning ChurchPresenter's own analytics
     * setting. A screen with no manifest yet reports, as a ChurchPresenter does before it is set up.
     */
    val diagnostics: Boolean = true,
) {
    fun playlist(id: String?): Playlist? = id?.let { wanted -> playlists.firstOrNull { it.id == wanted } }

    /** Every file the manifest refers to, once each; links have none. */
    fun fileHashes(): Set<String> = buildSet {
        playlists.forEach { playlist -> playlist.items.filterNot { it.kind.isLink }.forEach { add(it.sha256) } }
        idleCard.logoSha256?.let(::add)
    }
}

@Serializable
data class ScreenSettings(
    val name: String,
    val orientation: Orientation = Orientation.LANDSCAPE,
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
) {
    companion object {
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
    }
}

@Serializable
enum class Orientation {
    @SerialName("landscape") LANDSCAPE,

    @SerialName("portrait") PORTRAIT,

    @SerialName("landscape_flipped") LANDSCAPE_FLIPPED,

    @SerialName("portrait_flipped") PORTRAIT_FLIPPED,
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val items: List<PlaylistItem> = emptyList(),
)

/**
 * One entry of a playlist: a file by its hash, or a link by its [url].
 *
 * [url], [refreshMs] and [maxFps] are written only when set, so an item without them encodes exactly as it did
 * before links existed and an older manifest keeps its version.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PlaylistItem(
    /** The file's hash; empty for a link. */
    val sha256: String = "",
    val kind: MediaKind,
    /**
     * How long a picture stays up. A video plays to its end when this is null; a link stays up for as
     * long as its playlist plays.
     */
    val durationMs: Long? = null,
    val transition: Transition = Transition.CROSSFADE,
    val transitionMs: Long = DEFAULT_TRANSITION_MS,
    /** What a [MediaKind.STREAM] or [MediaKind.WEB] item opens. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val url: String? = null,
    /** How often a [MediaKind.WEB] page reloads; never when null. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val refreshMs: Long? = null,
    /**
     * At most how many frames a second a [MediaKind.WEB] page is drawn at; [DEFAULT_WEB_FPS] when
     * null. More is smoother and costs the player more.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val maxFps: Int? = null,
) {
    companion object {
        const val DEFAULT_TRANSITION_MS = 800L
        const val DEFAULT_WEB_FPS = 15
    }
}

/**
 * What an item is. [STREAM] and [WEB] are links: a player that does not list [Features.STREAM] or
 * [Features.WEB] cannot decode them, so ChurchPresenter never sends it one.
 */
@Serializable
enum class MediaKind {
    @SerialName("image") IMAGE,

    @SerialName("video") VIDEO,

    /** A video or live stream at a URL (a file, HLS, RTSP, or a page yt-dlp reads, such as YouTube). */
    @SerialName("stream") STREAM,

    /** A web page, drawn by the player's Chromium. */
    @SerialName("web") WEB,
    ;

    /** Opened from a URL rather than a file. */
    val isLink: Boolean get() = this == STREAM || this == WEB
}

@Serializable
enum class Transition {
    @SerialName("crossfade") CROSSFADE,

    @SerialName("cut") CUT,
}

/** What a screen shows when nothing else can play. */
@Serializable
data class IdleCard(
    val title: String = "",
    val logoSha256: String? = null,
)

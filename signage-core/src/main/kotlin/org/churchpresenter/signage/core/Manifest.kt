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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScreenSettings(
    val name: String,
    val orientation: Orientation = Orientation.LANDSCAPE,
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
    /**
     * What the touch gesture on the screen asks for before it switches between the playlist and an
     * interactive page, as [KioskPin.hash] makes it; no PIN is asked when null. Set, changed and
     * removed in ChurchPresenter only; the PIN itself never leaves it.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val kioskPinHash: String? = null,
    /** Whether holding the screen's corner switches to and from a touch page at all. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val kioskGesture: Boolean = true,
    /**
     * The playlist the corner gesture switches to; the first with a touch page when null, or when this
     * one has none.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val kioskGesturePlaylistId: String? = null,
    /**
     * A message written over whatever the screen shows, from ChurchPresenter's Signage tab, until it
     * ends or is cleared; none when null. Only on a player with [Features.MESSAGE].
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val message: ScreenMessage? = null,
) {
    companion object {
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
    }
}

/**
 * A message for the screen: [text] in a band across the top over what plays, or [fullScreen] in place
 * of it, until [untilMs] (epoch milliseconds) or, when that is null, until it is cleared.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScreenMessage(
    val text: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fullScreen: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val untilMs: Long? = null,
) {
    /** Whether it still shows at [nowMs]: it has words, and has not run out. */
    fun showsAt(nowMs: Long): Boolean = text.isNotBlank() && (untilMs == null || nowMs < untilMs)
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
 * [url], [refreshMs], [maxFps] and the interactive fields are written only when set, so an item without
 * them encodes exactly as it did before they existed and an older manifest keeps its version.
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
    /**
     * How often a [MediaKind.WEB] page reloads; never when null. An [interactive] page reloads only
     * while nobody is using it, and only on a player with [Features.TOUCH_RELOAD].
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val refreshMs: Long? = null,
    /**
     * At most how many frames a second a [MediaKind.WEB] page is drawn at; [DEFAULT_WEB_FPS] when
     * null. More is smoother and costs the player more.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val maxFps: Int? = null,
    /**
     * A [MediaKind.WEB] page people use on a touchscreen, such as an event sign-up: shown in a visible
     * browser instead of mpv, and staying up until its playlist goes off screen. Nothing typed into it
     * is kept: the browser starts from nothing after [idleResetMs] without a touch.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val interactive: Boolean = false,
    /**
     * How long an [interactive] page waits without a touch before it starts over;
     * [DEFAULT_IDLE_RESET_MS] when null.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val idleResetMs: Long? = null,
    /**
     * Other sites an [interactive] page may go to, as host names (`"churchcenter.com"` also allows its
     * subdomains), or [ANY_HOST] for every site. Its own [url]'s site is always allowed; any other page
     * is not opened. Only where the page itself goes is checked: what it embeds (a payment field, a
     * map) always loads.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val allowedHosts: List<String> = emptyList(),
    /**
     * What an [interactive] page's screen shows while nobody is using it: the playlist with this id,
     * played as any playlist is. A touch opens the page, and its idle reset comes back here. At most
     * one of this and [idleUrl] is set; neither means the page itself stays up.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val idlePlaylistId: String? = null,
    /**
     * What an [interactive] page's screen shows while nobody is using it: this web page, in the same
     * browser. The first tap on it opens the page (and does nothing on this one), and its idle reset
     * comes back here.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val idleUrl: String? = null,
    /**
     * How often [idleUrl] reloads while it is showing, so a slideshow picks up new pictures; never when
     * null, and never while someone uses the page. Only on a player with [Features.TOUCH_RELOAD].
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val idleRefreshMs: Long? = null,
    /**
     * The line written over [idlePlaylistId] or [idleUrl] so people know the screen can be touched,
     * such as "Tap to sign up"; nothing is written when null or blank.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val idlePrompt: String? = null,
    /**
     * CSS added after a [MediaKind.WEB] page's own on every page it shows, to restyle a site (hide its
     * menu, say); none when null or blank. On an [interactive] page it is not added to [idleUrl].
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pageCss: String? = null,
    /**
     * JavaScript run on every page a [MediaKind.WEB] item shows, once the page's document is parsed
     * (to point a site's Home link back at the page, say); none when null or blank. On an [interactive]
     * page it is not run on [idleUrl].
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pageScript: String? = null,
) {
    companion object {
        const val DEFAULT_TRANSITION_MS = 800L
        const val DEFAULT_WEB_FPS = 15
        const val DEFAULT_IDLE_RESET_MS = 90_000L
        const val MIN_IDLE_RESET_MS = 15_000L
        const val MAX_IDLE_RESET_MS = 30L * 60_000L

        /** In [allowedHosts]: an interactive page may go to any site. */
        const val ANY_HOST = "*"
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

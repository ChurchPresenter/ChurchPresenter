package org.churchpresenter.signage.tab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.PlaylistItem
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.UUID

/**
 * A playlist the tab lists and a screen can play: a folder, a single picture or video, or a link. A
 * new kind is one more subclass here and one more case in [buildManifest].
 */
sealed interface PlaylistSource {
    val id: String
    val name: String
}

/**
 * A playlist added in the tab rather than read from the signage folder, kept with its name in the
 * tab's settings. Added playlists are dev mode only: with it off they are neither listed nor sent.
 */
@Serializable
sealed interface AddedPlaylist : PlaylistSource

/** One picture or video from anywhere on this computer, which stays up (or loops) while it plays. */
@Serializable
@SerialName("file")
data class FilePlaylist(
    override val id: String,
    override val name: String,
    val path: String,
    val kind: MediaKind,
) : AddedPlaylist {
    companion object {
        fun create(name: String, path: String, kind: MediaKind) =
            FilePlaylist("file:${UUID.randomUUID()}", name, path, kind)
    }
}

/**
 * A web page or a stream, opened by the screen itself; it needs the internet there, and shows the
 * idle card when it cannot load. A web page reloads every [refreshMinutes], never when 0, and is shown
 * at 30 frames a second rather than 15 when [smooth]. An [interactive] page is used on a touchscreen
 * instead, such as an event sign-up: it neither reloads nor counts frames.
 */
@Serializable
@SerialName("link")
data class LinkPlaylist(
    override val id: String,
    override val name: String,
    val url: String,
    val kind: LinkKind,
    val refreshMinutes: Int = 0,
    val smooth: Boolean = false,
    val interactive: InteractiveLink? = null,
) : AddedPlaylist {
    companion object {
        /** Frames a second for a page that moves smoothly; the screen's own default is 15. */
        const val SMOOTH_FPS = 30

        fun create(link: NewLink) = with(link) {
            val id = "link:${UUID.randomUUID()}"
            LinkPlaylist(id, name.trim(), url.trim(), kind, refreshMinutes, smooth, interactive)
        }
    }
}

/**
 * How a web page people use on a touchscreen behaves: it starts over, keeping nothing anyone typed,
 * after [idleResetSeconds] without a touch, and may go only to its own site and [allowedHosts].
 */
@Serializable
data class InteractiveLink(
    val idleResetSeconds: Int = DEFAULT_IDLE_RESET_SECONDS,
    val allowedHosts: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_IDLE_RESET_SECONDS = 90

        /** In [allowedHosts]: the page may go to any site. */
        const val ANY_HOST = PlaylistItem.ANY_HOST
        val IDLE_RESET_SECONDS = 15..1800

        /** Sites typed one after another, split at commas, spaces or new lines. */
        fun hostsFrom(typed: String): List<String> =
            typed.split(',', ' ', '\n').map(String::trim).filter(String::isNotEmpty)
    }
}

/**
 * A rotation: other entries of the list, shown one after another, each for its own time — a web
 * page, a picture, a stream, a folder's pictures in turn. Its steps point at those entries, so a
 * link changed once changes in every rotation that shows it. A rotation is never a step of one.
 */
@Serializable
@SerialName("rotation")
data class RotationPlaylist(
    override val id: String,
    override val name: String,
    val steps: List<RotationStep>,
) : AddedPlaylist {
    companion object {
        fun create(name: String, steps: List<RotationStep>) =
            RotationPlaylist("rotation:${UUID.randomUUID()}", name, steps)
    }
}

/**
 * One step of a rotation: the entry [sourceId], for [seconds] — none for a folder (one round of its
 * pictures) or a video that plays to its end.
 */
@Serializable
data class RotationStep(val sourceId: String, val seconds: Int? = null)

/** A link as the Link dialog makes it, before it has an id. */
data class NewLink(
    val name: String,
    val url: String,
    val kind: LinkKind,
    val refreshMinutes: Int = 0,
    val smooth: Boolean = false,
    val interactive: InteractiveLink? = null,
)

/** What a link is: a page the screen draws, or a video stream it plays. */
@Serializable
enum class LinkKind {
    @SerialName("web") WEB,

    @SerialName("stream") STREAM,
    ;

    companion object {
        private val STREAM_SCHEMES = setOf("rtsp", "rtsps", "rtmp", "rtmps", "srt", "udp", "rtp")
        private val STREAM_EXTENSIONS = setOf("m3u8", "mpd", "mp4", "m4v", "mov", "webm", "mkv", "ts", "flv")
        private val STREAM_HOSTS = listOf("youtube.com", "youtu.be", "vimeo.com", "twitch.tv")

        /**
         * A stream for a streaming address, a video file, or a video site the screen's player reads
         * (YouTube, Vimeo, Twitch); a web page for anything else.
         */
        fun guess(url: String): LinkKind {
            val uri = try {
                URI(url.trim())
            } catch (_: URISyntaxException) {
                return WEB
            }
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            val fileName = uri.path.orEmpty().substringAfterLast('/')
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val stream = scheme in STREAM_SCHEMES ||
                extension in STREAM_EXTENSIONS ||
                STREAM_HOSTS.any { host == it || host.endsWith(".$it") }
            return if (stream) STREAM else WEB
        }
    }
}

/** What a playlist is, as its row shows it. */
enum class SourceKind { FOLDER, PICTURE, VIDEO, WEB, STREAM, ROTATION }

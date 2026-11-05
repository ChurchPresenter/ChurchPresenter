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
 * A playlist the tab lists and a screen can play: a folder, a single picture or video, a link, or a
 * rotation. A new kind is one more subclass here and one more case in [buildManifest].
 */
sealed interface PlaylistSource {
    val id: String
    val name: String
}

/** A playlist added in the tab, kept with its name in the tab's settings. */
@Serializable
sealed interface AddedPlaylist : PlaylistSource

/**
 * A playlist played from a folder in the signage folder ([path], relative to it), each picture up for
 * [pictureSeconds]: one picked in the tab, or one this computer fills itself. Read into a
 * [FolderPlaylist] before it is sent.
 */
sealed interface FolderBacked : AddedPlaylist {
    val path: String
    val pictureSeconds: Int
}

/**
 * A folder: every picture and video directly in it, in file-name order, each picture up for
 * [pictureSeconds]; read again as it changes. Its copy is in the signage folder (see
 * [SignageMedia]). Read into a [FolderPlaylist] before it is sent.
 */
@Serializable
@SerialName("folder")
data class PickedFolderPlaylist(
    override val id: String,
    override val name: String,
    override val path: String,
    override val pictureSeconds: Int = DEFAULT_PICTURE_SECONDS,
) : FolderBacked {
    companion object {
        const val DEFAULT_PICTURE_SECONDS = 10
        val PICTURE_SECONDS = 1..600

        fun create(name: String, path: String) = PickedFolderPlaylist("dir:${UUID.randomUUID()}", name, path)
    }
}

/**
 * A random slideshow of [photoCount] photos from the public galleries of a SmugMug [site] (its address,
 * such as `https://photos.example.org`, or the account's nickname), [excludedAlbumKeys] left out and
 * unlisted galleries too when [includeUnlisted]. A new set is picked every [refreshHours]. The photos
 * are downloaded into the signage folder ([path]) and played from there as a folder's are, so the
 * screens need nothing new and keep playing the last set while SmugMug cannot be reached.
 */
@Serializable
@SerialName("smugmug")
data class SmugMugPlaylist(
    override val id: String,
    override val name: String,
    val site: String,
    val photoCount: Int = DEFAULT_PHOTO_COUNT,
    override val pictureSeconds: Int = DEFAULT_PICTURE_SECONDS,
    val refreshHours: Int = DEFAULT_REFRESH_HOURS,
    val excludedAlbumKeys: Set<String> = emptySet(),
    val includeUnlisted: Boolean = false,
) : FolderBacked {
    /** Where its photos are kept, in the signage folder. */
    override val path: String get() = "$FOLDER/${id.substringAfter(':')}"

    companion object {
        /** The folder in the signage folder each slideshow's photos are kept under. */
        const val FOLDER = "SmugMug"
        const val DEFAULT_PHOTO_COUNT = 50
        const val DEFAULT_PICTURE_SECONDS = 8
        const val DEFAULT_REFRESH_HOURS = 6
        val PHOTO_COUNTS = 10..500
        val REFRESH_HOURS = 1..168

        fun create(name: String) = SmugMugPlaylist("smugmug:${UUID.randomUUID()}", name, site = "")
    }
}

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
 * instead, such as an event sign-up: it neither reloads nor counts frames. A web page's [pageCode] is
 * the church's own CSS and JavaScript for it, to restyle a site it does not own.
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
    val pageCode: PageCode? = null,
) : AddedPlaylist {
    companion object {
        /** Frames a second for a page that moves smoothly; the screen's own default is 15. */
        const val SMOOTH_FPS = 30

        fun create(link: NewLink) = with(link) {
            val id = "link:${UUID.randomUUID()}"
            LinkPlaylist(id, name.trim(), url.trim(), kind, refreshMinutes, smooth, interactive, pageCode)
        }
    }
}

/**
 * How a web page people use on a touchscreen behaves: it starts over, keeping nothing anyone typed,
 * after [idleResetSeconds] without a touch, and may go only to its own site and [allowedHosts]. While
 * nobody uses it the screen can play the playlist [idlePlaylistId] or show the page [idleUrl] instead
 * (at most one is set), with [idlePrompt] written over it; a touch opens the page, and its idle reset
 * goes back.
 */
@Serializable
data class InteractiveLink(
    val idleResetSeconds: Int = DEFAULT_IDLE_RESET_SECONDS,
    val allowedHosts: List<String> = emptyList(),
    val idlePlaylistId: String? = null,
    val idleUrl: String? = null,
    val idlePrompt: String? = null,
    /** How often [idleUrl] reloads while it shows, in minutes; never when 0. */
    val idleRefreshMinutes: Int = 0,
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
 * The church's own [css] and [script] for a web page, added to every page it shows: to hide a site's
 * menu, say, or point its Home link back at the page. A touchscreen page's idle page is left as it is.
 */
@Serializable
data class PageCode(val css: String = "", val script: String = "") {
    companion object {
        /** What was written, or null when both are blank. */
        fun of(css: String, script: String): PageCode? =
            PageCode(css.trim(), script.trim()).takeIf { it.css.isNotEmpty() || it.script.isNotEmpty() }
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
    val pageCode: PageCode? = null,
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
enum class SourceKind { FOLDER, PICTURE, VIDEO, WEB, STREAM, ROTATION, SMUGMUG }

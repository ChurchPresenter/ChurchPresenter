package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.server.InstanceLinkStatus
import java.io.File
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.server.InstanceLinkLogSide
import org.churchpresenter.server.InstanceLinkLogger
import org.churchpresenter.server.InstanceLinkViewModel

/** Where fetched background image/video bytes are cached, keyed by slot — BackgroundConfig's
 *  image/video fields need a local path, not bytes, same reasoning as [instanceLinkPictureCacheDir]. */
internal val instanceLinkBackgroundCacheDir: File by lazy {
    File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/backgrounds").apply { mkdirs() }
}

/**
 * Whether this instance mirrors the primary's live output onto its own presenter — the single
 * decision behind every "does the follower follow?" gate in `main.kt` (live state, the dedicated
 * presentation-slide broadcast, and display_cleared).
 *
 * Only [InstanceLinkRole.CONTROLLED] mirrors. A [InstanceLinkRole.CONTROLLER] drives the primary
 * and keeps its own output: it goes live locally *and* sends the command, so mirroring the primary
 * as well would echo that command straight back and overwrite the content it had just put up — with
 * the primary's version of it, refetched over the network. The primary's connect snapshot replays
 * its current live state to every client, so an ungated Controller is clobbered the moment it
 * connects, before the operator does anything at all.
 */
internal fun shouldMirrorRemoteOutput(role: InstanceLinkRole): Boolean =
    role == InstanceLinkRole.CONTROLLED

/**
 * Whether content should be sourced from the primary rather than from this machine — the same
 * decision as [shouldMirrorRemoteOutput], plus a live connection to source it over.
 *
 * Gates the remote-asset fallbacks (picture bytes, presentation slides, the media stream URL). Those
 * exist for a *mirrored* schedule item, whose file only lives on the primary's disk. A Controller's
 * schedule is its own local one, so routing it through the primary streams the wrong bytes — or none
 * at all, for an item id the primary has never seen.
 */
internal fun shouldUseRemoteContent(status: InstanceLinkStatus, role: InstanceLinkRole): Boolean =
    status == InstanceLinkStatus.CONNECTED && shouldMirrorRemoteOutput(role)

/**
 * Whether to replace this instance's backgrounds with the primary's — [shouldUseRemoteContent] plus
 * the explicit opt-in, which is off by default because backgrounds are usually venue-specific.
 */
internal fun shouldMirrorRemoteBackgrounds(
    status: InstanceLinkStatus,
    role: InstanceLinkRole,
    mirrorBackgrounds: Boolean
): Boolean = mirrorBackgrounds && shouldUseRemoteContent(status, role)

/**
 * Downloads the primary's configured background image/video assets (only for slots it actually has
 * set — most churches only use one or two) into a local cache, then returns a [BackgroundSettings]
 * copy with every image/video path rewritten to the cached file. BiblePresenter/SongPresenter then
 * render it exactly like a local background — no changes needed in either presenter. Only called
 * when the follower opted in via InstanceLinkSettings.mirrorBackgrounds; colors/gradients/opacity/
 * type are plain values already carried by [remote] as-is, no transfer needed for those.
 */
internal suspend fun downloadMirroredBackgroundSettings(
    remote: BackgroundSettings,
    instanceLinkViewModel: InstanceLinkViewModel
): BackgroundSettings {
    suspend fun cache(slot: String, path: String, isVideo: Boolean): String {
        if (path.isBlank()) return path
        val ext = File(path).extension.ifBlank { if (isVideo) "mp4" else "jpg" }
        val kind = if (isVideo) "video" else "image"
        val cacheFile = File(instanceLinkBackgroundCacheDir, "$slot-$kind.$ext")
        if (!cacheFile.exists()) {
            val bytes = instanceLinkViewModel.fetchBackgroundAsset(slot, isVideo)
            if (bytes == null) {
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.FOLLOWER, "background_asset_fetch_failed",
                    mapOf("slot" to slot, "isVideo" to isVideo)
                )
                return ""
            }
            // Temp-file + rename — same cancellation-safety reasoning as the picture cache:
            // the surrounding effect can be restarted mid-download.
            val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(cacheFile)) tmp.delete()
            if (!cacheFile.exists()) return ""
        }
        return cacheFile.absolutePath
    }
    return remote.withoutCameras().copy(
        defaultBackgroundImage = cache(Constants.BACKGROUND_SLOT_DEFAULT, remote.defaultBackgroundImage, false),
        defaultBackgroundVideo = cache(Constants.BACKGROUND_SLOT_DEFAULT, remote.defaultBackgroundVideo, true),
        defaultLowerThirdBackgroundImage = cache(
            Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD,
            remote.defaultLowerThirdBackgroundImage,
            false
        ),
        defaultLowerThirdBackgroundVideo = cache(
            Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD,
            remote.defaultLowerThirdBackgroundVideo,
            true
        ),
        bibleBackground = remote.bibleBackground.copy(
            backgroundImage = cache(Constants.BACKGROUND_SLOT_BIBLE, remote.bibleBackground.backgroundImage, false),
            backgroundVideo = cache(Constants.BACKGROUND_SLOT_BIBLE, remote.bibleBackground.backgroundVideo, true)
        ),
        bibleLowerThirdBackground = remote.bibleLowerThirdBackground.copy(
            backgroundImage = cache(
                Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD,
                remote.bibleLowerThirdBackground.backgroundImage,
                false
            ),
            backgroundVideo = cache(
                Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD,
                remote.bibleLowerThirdBackground.backgroundVideo,
                true
            )
        ),
        songBackground = remote.songBackground.copy(
            backgroundImage = cache(Constants.BACKGROUND_SLOT_SONG, remote.songBackground.backgroundImage, false),
            backgroundVideo = cache(Constants.BACKGROUND_SLOT_SONG, remote.songBackground.backgroundVideo, true)
        ),
        songLowerThirdBackground = remote.songLowerThirdBackground.copy(
            backgroundImage = cache(
                Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD,
                remote.songLowerThirdBackground.backgroundImage,
                false
            ),
            backgroundVideo = cache(
                Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD,
                remote.songLowerThirdBackground.backgroundVideo,
                true
            )
        )
    )
}

/**
 * [this] with every camera background dropped.
 *
 * A picture or a clip is fetched and cached, so a mirrored one resolves here. A camera cannot be:
 * the primary's device path names the primary's hardware, and the danger is not that it fails to
 * open on the follower but that it **succeeds** — `avfoundation://0` is a camera on almost any
 * machine, just not the one that was chosen. Dropping it puts the follower on its own configured
 * background, which is the same thing `cache` does by returning "" when an asset cannot be had.
 */
private fun BackgroundSettings.withoutCameras(): BackgroundSettings = copy(
    defaultBackgroundCamera = CameraDeviceRef(),
    defaultLowerThirdBackgroundCamera = CameraDeviceRef(),
    bibleBackground = bibleBackground.copy(camera = CameraDeviceRef()),
    bibleLowerThirdBackground = bibleLowerThirdBackground.copy(camera = CameraDeviceRef()),
    songBackground = songBackground.copy(camera = CameraDeviceRef()),
    songLowerThirdBackground = songLowerThirdBackground.copy(camera = CameraDeviceRef()),
)

package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Manifest
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Playlist
import org.churchpresenter.signage.core.PlaylistItem
import org.churchpresenter.signage.core.ScreenSettings
import org.churchpresenter.signage.hub.PairedScreen
import java.nio.file.Path

/**
 * [screen]'s manifest: every playlist it can play, playing the one its plan names (the first when it
 * names none, or one that is gone), and the file each hash is served from. A plan of
 * [ScreenPlan.NOTHING] gives a manifest with no playlists, so the screen shows its idle card and
 * keeps no pictures or videos.
 *
 * A link goes only to a screen whose player listed its kind in [features]: an older player cannot
 * read one, and shows its idle card for that playlist instead.
 */
fun buildManifest(
    screen: PairedScreen,
    ownerId: String,
    settings: SignageSettings,
    playlists: List<HashedPlaylist>,
    timeZone: String,
    /** This ChurchPresenter's analytics setting: whether the screen may send crash reports. */
    diagnostics: Boolean = true,
    /** What the screen's player said it can do, the last time it connected. */
    features: Collection<String> = emptyList(),
): Pair<Manifest, Map<String, Path>> {
    val files = mutableMapOf<String, Path>()
    fun fileItems(hashed: List<Pair<FolderFile, String?>>, durationMs: (MediaKind) -> Long?) =
        hashed.mapNotNull { (file, sha) ->
            sha ?: return@mapNotNull null
            files[sha] = file.path
            PlaylistItem(sha256 = sha, kind = file.kind, durationMs = durationMs(file.kind))
        }
    val built = playlists.map { hashed ->
        val items = when (val source = hashed.playlist) {
            is FolderPlaylist -> fileItems(source.files.map { it to hashed.hashes[it] }) { kind ->
                if (kind == MediaKind.IMAGE) settings.pictureSeconds * MS_PER_SECOND else null
            }
            // A lone picture stays up and a lone video loops, for as long as the playlist plays.
            is FilePlaylist -> fileItems(hashed.hashes.toList()) { null }
            is LinkPlaylist -> listOfNotNull(linkItem(source, features))
        }
        Playlist(hashed.playlist.id, hashed.playlist.name, items)
    }.filter { it.items.isNotEmpty() }
    val wanted = settings.plan(screen.id).playlistId
    if (wanted == ScreenPlan.NOTHING) {
        return Manifest(
            ownerId = ownerId,
            screen = ScreenSettings(name = screen.name, width = screen.screenWidth, height = screen.screenHeight),
            timeZone = timeZone,
            diagnostics = diagnostics,
        ) to emptyMap()
    }
    val manifest = Manifest(
        ownerId = ownerId,
        screen = ScreenSettings(name = screen.name, width = screen.screenWidth, height = screen.screenHeight),
        playlists = built,
        defaultPlaylistId = built.firstOrNull { it.id == wanted }?.id ?: built.firstOrNull()?.id,
        timeZone = timeZone,
        diagnostics = diagnostics,
    )
    return manifest to files
}

/** [link] as a playlist item, or null where the screen cannot play it. */
private fun linkItem(link: LinkPlaylist, features: Collection<String>): PlaylistItem? {
    val (kind, feature) = when (link.kind) {
        LinkKind.WEB -> MediaKind.WEB to Features.WEB
        LinkKind.STREAM -> MediaKind.STREAM to Features.STREAM
    }
    if (feature !in features) return null
    val web = kind == MediaKind.WEB
    val refreshMs = (link.refreshMinutes * MS_PER_MINUTE).takeIf { web && it > 0 }
    val maxFps = LinkPlaylist.SMOOTH_FPS.takeIf { web && link.smooth }
    return PlaylistItem(kind = kind, url = link.url, refreshMs = refreshMs, maxFps = maxFps)
}

private const val MS_PER_SECOND = 1_000L
private const val MS_PER_MINUTE = 60_000L

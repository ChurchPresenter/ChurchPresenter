package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.Manifest
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Playlist
import org.churchpresenter.signage.core.PlaylistItem
import org.churchpresenter.signage.core.ScreenSettings
import org.churchpresenter.signage.hub.PairedScreen
import java.nio.file.Path

/**
 * [screen]'s manifest: every folder playlist, playing the one its plan names (the first when it
 * names none, or one that is gone), and the file each hash is served from. A plan of
 * [ScreenPlan.NOTHING] gives a manifest with no playlists, so the screen shows its idle card and
 * keeps no pictures or videos.
 */
fun buildManifest(
    screen: PairedScreen,
    ownerId: String,
    settings: SignageSettings,
    playlists: List<HashedPlaylist>,
    timeZone: String,
    /** This ChurchPresenter's analytics setting: whether the screen may send crash reports. */
    diagnostics: Boolean = true,
): Pair<Manifest, Map<String, Path>> {
    val files = mutableMapOf<String, Path>()
    val built = playlists.map { hashed ->
        val items = hashed.playlist.files.mapNotNull { file ->
            val sha = hashed.hashes[file] ?: return@mapNotNull null
            files[sha] = file.path
            PlaylistItem(
                sha256 = sha,
                kind = file.kind,
                durationMs = if (file.kind == MediaKind.IMAGE) settings.pictureSeconds * MS_PER_SECOND else null,
            )
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

private const val MS_PER_SECOND = 1_000L

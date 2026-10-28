package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.BlockAction
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
    // Every playlist but the rotations first: a rotation is made of their items.
    val items = playlists.associate { hashed ->
        hashed.playlist.id to when (val source = hashed.playlist) {
            is FolderPlaylist -> fileItems(source.files.map { it to hashed.hashes[it] }) { kind ->
                if (kind == MediaKind.IMAGE) settings.pictureSeconds * MS_PER_SECOND else null
            }
            // A lone picture stays up and a lone video loops, for as long as the playlist plays.
            is FilePlaylist -> fileItems(hashed.hashes.toList()) { null }
            is LinkPlaylist -> listOfNotNull(linkItem(source, features))
            is RotationPlaylist -> emptyList()
        }
    }
    val sources = playlists.associate { it.playlist.id to it.playlist }
    val built = playlists.map { hashed ->
        val source = hashed.playlist
        val playlistItems = when (source) {
            is RotationPlaylist -> rotationItems(source, items, sources, features)
            else -> items.getValue(source.id)
        }
        Playlist(source.id, source.name, playlistItems)
    }.filter { it.items.isNotEmpty() }
    val wanted = settings.plan(screen.id).playlistId
    if (wanted == ScreenPlan.NOTHING) {
        return Manifest(
            ownerId = ownerId,
            screen = screenSettings(screen, settings),
            timeZone = timeZone,
            diagnostics = diagnostics,
        ) to emptyMap()
    }
    val manifest = Manifest(
        ownerId = ownerId,
        screen = screenSettings(screen, settings),
        playlists = built,
        defaultPlaylistId = built.firstOrNull { it.id == wanted }?.id ?: built.firstOrNull()?.id,
        // A block that plays what this screen cannot (a playlist gone, or links it cannot rotate) is left
        // out; the screen plays its own playlist then, as outside any block.
        schedule = settings.plan(screen.id).schedule.filter { block ->
            val action = block.action
            action !is BlockAction.Play || built.any { it.id == action.playlistId }
        },
        timeZone = timeZone,
        diagnostics = diagnostics,
    )
    return manifest to files
}

/**
 * [rotation]'s items, step by step from the other playlists' [items]: a folder's own (its pictures
 * for the picture time), a picture or video for its step's time (a video without one to its end),
 * and a link for its time only where the player can rotate it — an older one draws every page of a
 * playlist at once and never leaves one.
 */
private fun rotationItems(
    rotation: RotationPlaylist,
    items: Map<String, List<PlaylistItem>>,
    sources: Map<String, PlaylistSource>,
    features: Collection<String>,
): List<PlaylistItem> = rotation.steps.flatMap { step ->
    val stepItems = items[step.sourceId].orEmpty()
    val stepMs = step.seconds?.let { it * MS_PER_SECOND }
    when (sources[step.sourceId]) {
        is FolderPlaylist -> stepItems
        is FilePlaylist -> stepItems.map { item ->
            item.copy(durationMs = stepMs ?: DEFAULT_PICTURE_STEP_MS.takeIf { item.kind == MediaKind.IMAGE })
        }
        is LinkPlaylist -> stepItems.takeIf { Features.ROTATION in features }.orEmpty()
            .map { it.copy(durationMs = stepMs ?: DEFAULT_LINK_STEP_MS) }
        is RotationPlaylist, null -> emptyList()
    }
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

/** A picture step with no time, and a link step with none. */
private const val DEFAULT_PICTURE_STEP_MS = 10_000L
private const val DEFAULT_LINK_STEP_MS = 60_000L
private const val MS_PER_MINUTE = 60_000L

/** The screen's name, size and how it is mounted. */
private fun screenSettings(screen: PairedScreen, settings: SignageSettings) = ScreenSettings(
    name = screen.name,
    orientation = settings.plan(screen.id).orientation,
    width = screen.screenWidth,
    height = screen.screenHeight,
)

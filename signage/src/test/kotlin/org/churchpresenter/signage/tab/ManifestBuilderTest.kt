package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.hub.PairedScreen
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManifestBuilderTest {
    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_X64, "26.0.9", 1920, 1080, 0)
    private val picture = FolderFile(Path("/s/a.jpg"), MediaKind.IMAGE, 1, 1)
    private val video = FolderFile(Path("/s/b.mp4"), MediaKind.VIDEO, 1, 1)
    private val unreadable = FolderFile(Path("/s/c.jpg"), MediaKind.IMAGE, 1, 1)
    private val playlists = listOf(
        HashedPlaylist(
            FolderPlaylist("folder:", "Signage", listOf(picture, video, unreadable), "/s", pictureSeconds = 7),
            mapOf(picture to "a", video to "b", unreadable to null),
        ),
        HashedPlaylist(FolderPlaylist("folder:Events", "Events", listOf(picture), "/s/Events"), mapOf(picture to "a")),
        HashedPlaylist(
            FolderPlaylist("folder:Broken", "Broken", listOf(unreadable), "/s/Broken"),
            mapOf(unreadable to null),
        ),
    )

    private fun build(plan: String?) = buildManifest(
        screen = screen,
        ownerId = "owner",
        settings = SignageSettings(screens = mapOf("p" to ScreenPlan(plan))),
        playlists = playlists,
        timeZone = "UTC",
    )

    @Test
    fun everyPlaylistGoesWithTheOnePlannedPlaying() {
        val (manifest, files) = build("folder:Events")
        // The playlist with nothing readable is left out.
        assertEquals(listOf("folder:", "folder:Events"), manifest.playlists.map { it.id })
        assertEquals("folder:Events", manifest.defaultPlaylistId)
        assertEquals(listOf(7_000L, null), manifest.playlists[0].items.map { it.durationMs })
        assertEquals(mapOf("a" to picture.path, "b" to video.path), files)
        assertEquals("Lobby", manifest.screen.name)
    }

    @Test
    fun noPlanOrAGonePlaylistPlaysTheFirst() {
        assertEquals("folder:", build(null).first.defaultPlaylistId)
        assertEquals("folder:", build("folder:Gone").first.defaultPlaylistId)
    }

    @Test
    fun theScreenReportsCrashesOnlyAsThisChurchPresenterAllows() {
        assertEquals(true, build(null).first.diagnostics)
        val off = buildManifest(screen, "owner", SignageSettings(), playlists, "UTC", diagnostics = false)
        assertEquals(false, off.first.diagnostics)
        val empty = buildManifest(
            screen, "owner", SignageSettings(screens = mapOf("p" to ScreenPlan(ScreenPlan.NOTHING))), playlists, "UTC",
            diagnostics = false,
        )
        assertEquals(false, empty.first.diagnostics)
    }

    @Test
    fun nothingLeavesTheScreenEmpty() {
        val (manifest, files) = build(ScreenPlan.NOTHING)
        assertEquals(emptyList(), manifest.playlists)
        assertNull(manifest.defaultPlaylistId)
        assertEquals(emptyMap(), files)
    }
}

package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.MediaKind
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlaylistItem
import org.churchpresenter.signage.hub.PairedScreen
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** A screen's manifest with added playlists: a lone picture or video, and links only where the player can play them. */
class ManifestLinksTest {
    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.31", 1920, 1080, 0)
    private val welcome = FolderFile(Path("/pictures/welcome.png"), MediaKind.IMAGE, 1, 1)
    private val weather =
        LinkPlaylist("link:w", "Weather", "https://weather.example.org", LinkKind.WEB, refreshMinutes = 10)
    private val smooth = weather.copy(id = "link:s", name = "Ticker", smooth = true)
    private val live =
        LinkPlaylist("link:l", "Live", "https://example.org/live.m3u8", LinkKind.STREAM, refreshMinutes = 10)
    private val playlists = listOf(
        HashedPlaylist(
            FilePlaylist("file:w", "Welcome", welcome.path.toString(), MediaKind.IMAGE),
            mapOf(welcome to "a"),
        ),
        HashedPlaylist(FilePlaylist("file:x", "Gone", "/pictures/gone.png", MediaKind.IMAGE)),
        HashedPlaylist(weather),
        HashedPlaylist(smooth),
        HashedPlaylist(live),
    )

    private fun build(features: List<String>, plan: String? = null) = buildManifest(
        screen = screen,
        ownerId = "owner",
        settings = SignageSettings(screens = mapOf("p" to ScreenPlan(plan))),
        playlists = playlists,
        timeZone = "UTC",
        features = features,
    )

    @Test
    fun aLonePictureStaysUpAndItsFileIsServed() {
        val (manifest, files) = build(emptyList())
        val welcomePlaylist = manifest.playlists.single { it.id == "file:w" }
        assertEquals(listOf(PlaylistItem("a", MediaKind.IMAGE)), welcomePlaylist.items)
        assertEquals(mapOf("a" to welcome.path), files)
        // A playlist whose file is gone has nothing to play, and is left out.
        assertEquals(listOf("file:w"), manifest.playlists.map { it.id })
    }

    @Test
    fun linksGoOnlyToAPlayerThatListedTheirKind() {
        assertEquals(listOf("file:w", "link:l"), build(listOf(Features.STREAM)).first.playlists.map { it.id })
        assertEquals(
            listOf("file:w", "link:w", "link:s", "link:l"),
            build(listOf(Features.STREAM, Features.WEB)).first.playlists.map { it.id },
        )
    }

    @Test
    fun aWebPageCarriesItsReloadAndSmoothMotionAStreamNeither() {
        val manifest = build(listOf(Features.STREAM, Features.WEB), plan = "link:s").first
        val items = manifest.playlists.associate { it.id to it.items.single() }
        assertEquals(PlaylistItem(kind = MediaKind.WEB, url = weather.url, refreshMs = 600_000), items["link:w"])
        val smoothItem = PlaylistItem(kind = MediaKind.WEB, url = weather.url, refreshMs = 600_000, maxFps = 30)
        assertEquals(smoothItem, items["link:s"])
        assertEquals(PlaylistItem(kind = MediaKind.STREAM, url = live.url), items["link:l"])
        assertEquals("link:s", manifest.defaultPlaylistId)
    }

    @Test
    fun aScreenSetToALinkItCannotPlayPlaysTheFirstPlaylist() {
        assertEquals("file:w", build(emptyList(), plan = "link:w").first.defaultPlaylistId)
    }
}

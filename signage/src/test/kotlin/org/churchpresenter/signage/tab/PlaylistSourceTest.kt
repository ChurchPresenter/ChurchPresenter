package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.MediaKind
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The playlists added by hand: how a link's kind is guessed, how they are kept, and how they are listed. */
@OptIn(ExperimentalPathApi::class)
class PlaylistSourceTest {
    private val dir = createTempDirectory("signage-sources")

    @AfterTest
    fun cleanUp() = dir.deleteRecursively()

    @Test
    fun aStreamIsGuessedFromItsAddressAndAnythingElseIsAWebPage() {
        val streams = listOf(
            "https://example.org/live/index.m3u8",
            "https://cdn.example.org/clips/welcome.MP4?token=1",
            "rtsp://camera.local/stream",
            "rtmp://live.example.org/app",
            "https://www.youtube.com/@gracechurch/live",
            "https://youtu.be/abc",
            "https://vimeo.com/123",
        )
        streams.forEach { assertEquals(LinkKind.STREAM, LinkKind.guess(it), it) }
        val pages = listOf(
            "https://weather.example.org/city",
            "http://192.168.2.240",
            "https://example.org/page.html",
            "not a url ^",
        )
        pages.forEach { assertEquals(LinkKind.WEB, LinkKind.guess(it), it) }
    }

    @Test
    fun addedPlaylistsAreKeptWithTheSettings() {
        val store = SignageStore(dir)
        val link = LinkPlaylist.create("Weather", "https://example.org", LinkKind.WEB, 10, smooth = true)
        val file = FilePlaylist.create("Welcome", "/pictures/welcome.png", MediaKind.IMAGE)
        assertTrue(link.id.startsWith("link:") && file.id.startsWith("file:"))
        store.saveSettings(SignageSettings(added = listOf(link, file)))
        assertEquals(listOf(link, file), SignageStore(dir).loadSettings().added)
        // Settings saved before playlists could be added still load, with none.
        dir.resolve("signage.json").writeText("""{"folder":"","pictureSeconds":10,"screens":{}}""")
        assertEquals(emptyList(), SignageStore(dir).loadSettings().added)
    }

    @Test
    fun removingAnAddedPlaylistSendsItsScreensBackToTheFirst() {
        val link = LinkPlaylist("link:1", "Weather", "https://example.org", LinkKind.WEB)
        val nothing = ScreenPlan(ScreenPlan.NOTHING)
        val settings = SignageSettings(
            screens = mapOf("a" to ScreenPlan("link:1"), "b" to ScreenPlan("folder:"), "c" to nothing),
            added = listOf(link),
        )
        val after = settings.withoutAdded("link:1")
        assertEquals(emptyList(), after.added)
        assertEquals(mapOf("a" to ScreenPlan(), "b" to ScreenPlan("folder:"), "c" to nothing), after.screens)
    }

    @Test
    fun anAddedPlaylistIsListedWithItsFileOrAddressAndAMissingFileSaysSo() {
        val picture = dir.resolve("welcome.png").also { it.writeText("png") }
        val file = FilePlaylist("file:1", "Welcome", picture.toString(), MediaKind.IMAGE)
        val present = checkNotNull(file.currentFile())
        assertEquals(
            PlaylistSummary("file:1", "Welcome", kind = SourceKind.PICTURE, detail = "welcome.png"),
            HashedPlaylist(file, mapOf(present to "hash")).addedSummary(),
        )
        val gone = file.copy(path = dir.resolve("gone.mp4").toString(), kind = MediaKind.VIDEO)
        assertNull(gone.currentFile())
        assertEquals(
            PlaylistSummary("file:1", "Welcome", kind = SourceKind.VIDEO, detail = "gone.mp4", missing = true),
            HashedPlaylist(gone).addedSummary(),
        )
        val stream = LinkPlaylist("link:1", "Live", "rtsp://camera", LinkKind.STREAM)
        val listed = HashedPlaylist(stream).addedSummary()
        assertEquals(SourceKind.STREAM to "rtsp://camera", listed.kind to listed.detail)
        assertTrue(HashedPlaylist(stream).addedSummary().added)
        val filter = picturesAndVideosFilter("Pictures and videos")
        assertTrue(filter.extensions.toSet().containsAll(listOf("jpg", "png", "mp4", "mov")))
    }
}

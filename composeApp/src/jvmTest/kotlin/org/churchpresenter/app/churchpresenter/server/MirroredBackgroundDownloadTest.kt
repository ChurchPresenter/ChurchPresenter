package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundConfig
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel
import java.io.File
import java.nio.file.Files
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [downloadMirroredBackgroundSettings] — what a follower does when the operator has asked it to
 * mirror the primary's backgrounds.
 *
 * The DTO the primary sends carries its **own local file paths**, which mean nothing on the
 * follower's machine. So every configured image/video slot has to be fetched as bytes, written into
 * a local cache, and the path rewritten to point there before any presenter sees it. Get that wrong
 * and the overflow room shows a black screen behind the lyrics — the settings arrived, the file
 * behind them never did.
 *
 * Driven against a real [CompanionServer] as the primary and a real, connected
 * [InstanceLinkViewModel] as the follower — the same approach as [ApplyRemoteLiveStateRemoteFetchTest]
 * and `InstanceLinkClientTest`, and for the same reason: the fetch path *is* the thing under test,
 * so stubbing it would leave nothing worth asserting.
 *
 * As in [ApplyRemoteLiveStateRemoteFetchTest], `user.home` is deliberately **not** swapped:
 * [instanceLinkBackgroundCacheDir] resolves once per JVM from the permanent `build/test-home` that
 * Gradle already points at. The cache file name is `slot-kind.ext` with nothing per-test in it, so
 * each test clears the slots it uses first — otherwise a file left by an earlier run would be
 * indistinguishable from a successful fetch, and the cache-reuse test would pass without ever
 * having fetched anything.
 */
class MirroredBackgroundDownloadTest {

    companion object {
        private lateinit var server: CompanionServer
        private lateinit var link: InstanceLinkViewModel
        private lateinit var primaryAssets: File
        private var port: Int = 0

        @JvmStatic
        @BeforeClass
        fun startPrimary() {
            TestSingletons.latchToTestHome()
            primaryAssets = Files.createTempDirectory("cp-mirrored-backgrounds").toFile()

            server = CompanionServer()
            server.start(port = 39_860)
            port = runBlocking {
                withTimeoutOrNull(10_000) {
                    while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                        kotlinx.coroutines.delay(25)
                    }
                    server.serverUrl.value.substringAfterLast(':').toInt()
                }
            } ?: error("server did not start")

            link = InstanceLinkViewModel()
            link.connect(host = "127.0.0.1",
                port = port,
                apiKey = "",
                deviceId = "test-follower",
                reconnectDelayMs = 60_000)
            awaitUntil("the link to connect") { link.connectionStatus.value == InstanceLinkStatus.CONNECTED }
        }

        @JvmStatic
        @AfterClass
        fun stopPrimary() {
            runCatching { link.dispose() }
            runCatching { server.stop() }
            runCatching { primaryAssets.deleteRecursively() }
        }

        /** Ends on the condition itself; the timeout only fails the run. */
        private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return
                Thread.sleep(20)
            }
            throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────

    /** A file on the *primary's* disk, whose contents identify it. */
    private fun primaryAsset(name: String, contents: String = "contents of $name"): File =
        File(primaryAssets, name).also { it.writeText(contents) }

    /** Clears this slot's cached files so a leftover from an earlier run cannot be read as a fetch. */
    private fun clearCache(slot: String) {
        instanceLinkBackgroundCacheDir.listFiles()
            ?.filter { it.name.startsWith("$slot-") }
            ?.forEach { it.delete() }
    }

    private fun download(remote: BackgroundSettings): BackgroundSettings =
        runBlocking { downloadMirroredBackgroundSettings(remote, link) }

    private fun assertCached(path: String, expectedContents: String) {
        val file = File(path)
        assertEquals(
            instanceLinkBackgroundCacheDir.canonicalFile, file.canonicalFile.parentFile,
            "the rewritten path must point into the follower's own cache, not at the primary's disk"
        )
        assertTrue(file.exists(), "the cached file should have been written: $path")
        assertEquals(expectedContents, file.readText(), "the cached bytes should be the primary's")
    }

    // ── Fetching ────────────────────────────────────────────────────────────────

    @Test
    fun `a configured image is fetched and its path rewritten to the local cache`() {
        clearCache(Constants.BACKGROUND_SLOT_BIBLE)
        val asset = primaryAsset("bible.jpg")
        val remote = BackgroundSettings(bibleBackground = BackgroundConfig(backgroundImage = asset.absolutePath))
        server.updateBackgroundSettings(remote)

        val mirrored = download(remote)

        assertTrue(
            mirrored.bibleBackground.backgroundImage != asset.absolutePath,
            "the primary's own path is meaningless on this machine and must not survive"
        )
        assertCached(mirrored.bibleBackground.backgroundImage, "contents of bible.jpg")
    }

    @Test
    fun `an image and a video in the same slot are cached as separate files`() {
        clearCache(Constants.BACKGROUND_SLOT_SONG)
        val image = primaryAsset("song.png")
        val video = primaryAsset("song.mp4")
        val remote = BackgroundSettings(
            songBackground = BackgroundConfig(
                backgroundImage = image.absolutePath,
                backgroundVideo = video.absolutePath
            )
        )
        server.updateBackgroundSettings(remote)

        val mirrored = download(remote)

        assertCached(mirrored.songBackground.backgroundImage, "contents of song.png")
        assertCached(mirrored.songBackground.backgroundVideo, "contents of song.mp4")
        assertTrue(
            mirrored.songBackground.backgroundImage != mirrored.songBackground.backgroundVideo,
            "the image must not overwrite the video of the same slot"
        )
    }

    @Test
    fun `every slot the operator configured is mirrored, not just the first`() {
        listOf(
            Constants.BACKGROUND_SLOT_DEFAULT,
            Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD,
            Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD,
            Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD,
        ).forEach(::clearCache)

        val remote = BackgroundSettings(
            defaultBackgroundImage = primaryAsset("default.jpg").absolutePath,
            defaultLowerThirdBackgroundImage = primaryAsset("default-lt.jpg").absolutePath,
            bibleLowerThirdBackground = BackgroundConfig(backgroundImage = primaryAsset("bible-lt.jpg").absolutePath),
            songLowerThirdBackground = BackgroundConfig(backgroundImage = primaryAsset("song-lt.jpg").absolutePath),
        )
        server.updateBackgroundSettings(remote)

        val mirrored = download(remote)

        assertCached(mirrored.defaultBackgroundImage, "contents of default.jpg")
        assertCached(mirrored.defaultLowerThirdBackgroundImage, "contents of default-lt.jpg")
        assertCached(mirrored.bibleLowerThirdBackground.backgroundImage, "contents of bible-lt.jpg")
        assertCached(mirrored.songLowerThirdBackground.backgroundImage, "contents of song-lt.jpg")
    }

    @Test
    fun `the settings that need no transfer come through untouched`() {
        clearCache(Constants.BACKGROUND_SLOT_BIBLE)
        val remote = BackgroundSettings(
            defaultBackgroundColor = "#123456",
            defaultBackgroundType = Constants.BACKGROUND_COLOR,
            defaultBackgroundOpacity = 0.42f,
            bibleBackground = BackgroundConfig(
                backgroundColor = "#ABCDEF",
                backgroundOpacity = 0.75f,
                gradientEnabled = true,
                gradientBottomColor = "#FEDCBA"
            )
        )
        server.updateBackgroundSettings(remote)

        val mirrored = download(remote)

        // Colours, opacities and gradients are plain values already carried by the DTO — a copy that
        // dropped one of these would show the right picture in the wrong colours.
        assertEquals(remote, mirrored, "with nothing to fetch the settings should come back unchanged")
    }

    // ── When the fetch cannot succeed ───────────────────────────────────────────

    @Test
    fun `a slot the primary cannot serve is blanked rather than left pointing at a missing file`() {
        clearCache(Constants.BACKGROUND_SLOT_BIBLE)
        // The follower is told about a path, but the primary has no background configured, so the
        // asset endpoint refuses it. Keeping the path would leave the presenter loading a file that
        // does not exist on this machine.
        server.updateBackgroundSettings(BackgroundSettings())
        val remote = BackgroundSettings(
            bibleBackground = BackgroundConfig(backgroundImage = "/primary/only/missing.jpg")
        )

        val mirrored = download(remote)

        assertEquals("", mirrored.bibleBackground.backgroundImage)
    }

    @Test
    fun `an unconfigured slot is left alone and nothing is fetched for it`() {
        listOf(Constants.BACKGROUND_SLOT_BIBLE, Constants.BACKGROUND_SLOT_SONG).forEach(::clearCache)
        val remote = BackgroundSettings()
        server.updateBackgroundSettings(remote)

        val mirrored = download(remote)

        assertEquals("", mirrored.bibleBackground.backgroundImage)
        assertEquals("", mirrored.songBackground.backgroundVideo)
        assertTrue(
            instanceLinkBackgroundCacheDir.listFiles().orEmpty().none {
                it.name.startsWith("${Constants.BACKGROUND_SLOT_BIBLE}-") ||
                    it.name.startsWith("${Constants.BACKGROUND_SLOT_SONG}-")
            },
            "a blank path is not a download"
        )
    }

    // ── The cache ───────────────────────────────────────────────────────────────

    @Test
    fun `a slot already in the cache is not fetched again`() {
        clearCache(Constants.BACKGROUND_SLOT_BIBLE)
        val asset = primaryAsset("bible.jpg", "the first version")
        val remote = BackgroundSettings(bibleBackground = BackgroundConfig(backgroundImage = asset.absolutePath))
        server.updateBackgroundSettings(remote)

        val first = download(remote)
        assertCached(first.bibleBackground.backgroundImage, "the first version")

        // Change what the primary would serve. A second download that re-fetched would pick this up;
        // reusing the cache — which is the point, mid-service bandwidth — must not.
        asset.writeText("the second version")

        val second = download(remote)

        assertEquals(first.bibleBackground.backgroundImage, second.bibleBackground.backgroundImage)
        assertEquals(
            "the first version", File(second.bibleBackground.backgroundImage).readText(),
            "the cached copy should have been reused rather than re-downloaded"
        )
    }
}

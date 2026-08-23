package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.testPort
import org.churchpresenter.companionserver.CompanionServer
import org.churchpresenter.companionserver.InstanceLinkStatus
import org.churchpresenter.companionserver.LiveStateDto

/**
 * The two [applyRemoteLiveState] branches [ApplyRemoteLiveStateTest] deliberately leaves out:
 * PICTURES and LOWER_THIRD both fetch bytes from the primary over [InstanceLinkViewModel], so this
 * drives a real [CompanionServer] as the primary — same approach as `InstanceLinkClientTest` — and a
 * real, connected [InstanceLinkViewModel] as the follower.
 *
 * [instanceLinkPictureCacheDir] resolves its path from `user.home` once per JVM (like
 * `InstanceLinkLogger`), so — unlike most test classes in this suite — `user.home` is deliberately
 * **not** swapped here; the project's own Gradle config already points it at a permanent
 * `build/test-home` for the whole `jvmTest` run (see `TestSingletons`'s doc comment for why a
 * per-test temp dir would be actively wrong for a `by lazy` singleton: the dir gets deleted out from
 * under it after the first test that resolves it). Each test instead uses its own folder-id and
 * clears its own cache file first, so a stale file from an earlier run of this suite can't be
 * mistaken for a fresh fetch.
 */
class ApplyRemoteLiveStateRemoteFetchTest {

    private lateinit var server: CompanionServer
    private lateinit var link: InstanceLinkViewModel
    private var port: Int = 0

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()

        server = CompanionServer()
        server.start(port = testPort(39_820))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")

        link = InstanceLinkViewModel()
        link.connect(host = "127.0.0.1", port = port, apiKey = "", deviceId = "test-device", reconnectDelayMs = 60_000)
        awaitUntil("the link to connect") { link.connectionStatus.value == InstanceLinkStatus.CONNECTED }
    }

    @AfterTest
    fun tearDown() {
        runCatching { link.dispose() }
        runCatching { server.stop() }
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun apply(state: LiveStateDto): PresenterManager {
        val presenter = PresenterManager()
        runBlocking { applyRemoteLiveState(state = state, presenterManager = presenter, instanceLinkViewModel = link) }
        return presenter
    }

    /** Clears this test's own slot in the process-wide picture cache before it runs, so a leftover
     *  file from an earlier run of this suite can't be mistaken for a fresh fetch. */
    private fun clearPictureCache(folderId: String, index: Int) {
        File(instanceLinkPictureCacheDir, "${folderId}_$index.jpg").delete()
    }

    // ── PICTURES ───────────────────────────────────────────────────────────────

    @Test
    fun `a live picture is fetched, cached locally, and displayed by its cache path`() {
        clearPictureCache("apply-remote-fetch-1", 0)
        val dir = Files.createTempDirectory("cp-remote-fetch-pictures").toFile()
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val imageFile = File(dir, "photo.jpg").apply { writeBytes(imageBytes) }
        server.updatePictures(
            folderId = "apply-remote-fetch-1",
            folderName = "Folder",
            folderPath = dir.absolutePath,
            imageFiles = listOf(imageFile),
        )

        val presenter = apply(LiveStateDto(
            contentType = "PICTURES",
            pictureFolderId = "apply-remote-fetch-1",
            pictureIndex = 0,
        ))

        val cachedPath = presenter.selectedImagePath.value
        assertTrue(cachedPath != null && File(cachedPath).exists(), "expected a real cache file, got $cachedPath")
        assertEquals(imageBytes.toList(), File(cachedPath).readBytes().toList())
        assertEquals(Presenting.PICTURES, presenter.presentingMode.value)
    }

    @Test
    fun `a second apply of the same picture reuses the cache instead of re-fetching`() {
        clearPictureCache("apply-remote-fetch-2", 0)
        val dir = Files.createTempDirectory("cp-remote-fetch-pictures-cache").toFile()
        val imageFile = File(dir, "photo.jpg").apply { writeBytes(byteArrayOf(9)) }
        server.updatePictures(
            folderId = "apply-remote-fetch-2",
            folderName = "Folder",
            folderPath = dir.absolutePath,
            imageFiles = listOf(imageFile),
        )

        val first = apply(LiveStateDto(
            contentType = "PICTURES",
            pictureFolderId = "apply-remote-fetch-2",
            pictureIndex = 0,
        ))
        val firstPath = first.selectedImagePath.value

        // Delete the folder server-side — if the second apply re-fetched, it would now fail.
        server.updatePictures(
            folderId = "apply-remote-fetch-2",
            folderName = "Folder",
            folderPath = dir.absolutePath,
            imageFiles = emptyList(),
        )
        val second = apply(LiveStateDto(
            contentType = "PICTURES",
            pictureFolderId = "apply-remote-fetch-2",
            pictureIndex = 0,
        ))

        assertEquals(firstPath, second.selectedImagePath.value, "the cache file path must be stable across applies")
    }

    @Test
    fun `a picture the primary does not have leaves the mode unswitched`() {
        clearPictureCache("apply-remote-fetch-missing", 0)
        val presenter = apply(LiveStateDto(
            contentType = "PICTURES",
            pictureFolderId = "apply-remote-fetch-missing",
            pictureIndex = 0,
        ))

        // Unlike every other branch in this function, a failed PICTURES fetch returns before
        // reaching the trailing setPresentingMode/setShowPresenterWindow calls — so, discovered
        // here rather than asserted as an existing contract: the mode does NOT switch on this one
        // failure path, unlike the "missing folder-id/index in the payload" case just below.
        assertNull(presenter.selectedImagePath.value)
        assertEquals(Presenting.NONE, presenter.presentingMode.value)
    }

    @Test
    fun `a PICTURES state missing its folder-id or index still switches the mode`() {
        val presenter = apply(LiveStateDto(contentType = "PICTURES"))
        assertNull(presenter.selectedImagePath.value)
        assertEquals(
            Presenting.PICTURES,
            presenter.presentingMode.value,
            "this guard falls through normally, unlike a failed fetch",
        )
    }

    // ── LOWER_THIRD ────────────────────────────────────────────────────────────

    @Test
    fun `a live lower third is fetched by name and played as Lottie content`() {
        val dir = Files.createTempDirectory("cp-remote-fetch-lowerthirds").toFile()
        File(dir, "Welcome.json").writeText("""{"v":"5.9.0","layers":[]}""")
        server.updateAtemConfig(AtemSettings(), lowerThirdFolder = dir.absolutePath)

        val presenter = apply(LiveStateDto(contentType = "LOWER_THIRD", lowerThirdName = "Welcome"))

        assertEquals("""{"v":"5.9.0","layers":[]}""", presenter.lottieJsonContent.value)
        assertEquals(Presenting.LOWER_THIRD, presenter.presentingMode.value)
    }

    @Test
    fun `a lower third preset the primary does not have is a quiet no-op`() {
        val dir = Files.createTempDirectory("cp-remote-fetch-lowerthirds-empty").toFile()
        server.updateAtemConfig(AtemSettings(), lowerThirdFolder = dir.absolutePath)

        val presenter = apply(LiveStateDto(contentType = "LOWER_THIRD", lowerThirdName = "no-such-preset"))

        assertEquals("", presenter.lottieJsonContent.value)
        assertEquals(Presenting.LOWER_THIRD, presenter.presentingMode.value, "the mode still switches")
    }
}

package org.churchpresenter.lowerthird.render

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.settings.AtemSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pre-generating the cache: every variant a lottie will be consumed at is rendered in the
 * background as soon as the file appears, so playback and "Send to ATEM" stream a ready file.
 *
 * The lotties here are tiny (64×36, a tenth of a second) so each render is a few frames; the waits
 * are on the cache files themselves, bounded only to fail the test.
 */
class LottieRenderCachePrepareTest {

    private lateinit var originalHome: String
    private lateinit var tempHome: File
    private lateinit var folder: File

    /** 64×36 at 30fps for three frames: a clip, and small enough to render in a blink. */
    private val tinyLottie = """{"v":"5.7.4","fr":30,"ip":0,"op":3,"w":64,"h":36,"layers":[]}"""

    /** An ATEM at the lottie's own size, so the ATEM and desktop variants are one entry or two cheap ones. */
    private val atem = AtemSettings(host = "127.0.0.1", renderWidth = 64, renderHeight = 36)

    @BeforeTest
    fun isolate() {
        // Load skia against the suite's home before the swap; see LowerThirdAtemUploadTest.
        Class.forName("org.jetbrains.skia.Surface")
        originalHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("lrc-prepare-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        folder = Files.createTempDirectory("lrc-prepare-folder").toFile()
    }

    @AfterTest
    fun restore() {
        System.setProperty("user.home", originalHome)
        tempHome.deleteRecursively()
        folder.deleteRecursively()
    }

    private fun awaitReady(json: String, variant: LottieRenderCache.Variant) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!LottieRenderCache.isReady(json, variant)) {
            check(System.currentTimeMillis() < deadline) { "$variant was never cached" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `a new lottie gets its desktop clip, and with an ATEM its still and clip too`() {
        val file = File(folder, "Welcome.json").apply { writeText(tinyLottie) }

        LottieRenderCache.ensureForFile(file, atem)

        awaitReady(tinyLottie, LottieRenderCache.desktopVariant(tinyLottie, atem)!!)
        awaitReady(tinyLottie, LottieRenderCache.atemVariant(tinyLottie, atem, clip = false))
        awaitReady(tinyLottie, LottieRenderCache.atemVariant(tinyLottie, atem, clip = true))
    }

    @Test
    fun `the folder pass prepares every lottie in it and skips other JSON`() {
        File(folder, "Welcome.json").writeText(tinyLottie)
        File(folder, "notes.json").writeText("""{"not":"a lottie"}""")
        File(folder, "readme.txt").writeText(tinyLottie)

        LottieRenderCache.ensureForFolder(folder.absolutePath, atem = null)

        awaitReady(tinyLottie, LottieRenderCache.desktopVariant(tinyLottie, null)!!)
    }

    @Test
    fun `progress reads full once the entry is cached`() = runBlocking {
        val variant = LottieRenderCache.desktopVariant(tinyLottie, null)!!

        LottieRenderCache.prepare(tinyLottie, variant).await()

        val progress = withTimeout(WAIT_MS) { LottieRenderCache.progressFlow(tinyLottie, variant).first { it == 1f } }
        assertEquals(1f, progress)
        assertTrue(LottieRenderCache.isReady(tinyLottie, variant))
    }

    @Test
    fun `a cached clip reads back the size, rate and length it was rendered at`() = runBlocking {
        val variant = LottieRenderCache.desktopVariant(tinyLottie, null)!!
        val file = LottieRenderCache.prepare(tinyLottie, variant).await()

        LottieRenderCache.Reader(file).use { reader ->
            assertEquals(64, reader.width)
            assertEquals(36, reader.height)
            assertEquals(LottieRenderCache.PLAYBACK_FPS * 100, reader.fpsX100)
            assertEquals(variant.frameCount, reader.frameCount)
            assertEquals(64 * 36, reader.nextFrameArgb().size, "a frame decodes to every pixel")
        }
    }

    @Test
    fun `a preset that cannot be read is reported, not thrown, and nothing is cached`() = runBlocking {
        LottieRenderCache.ensureForFile(File(folder, "gone.json"), atem).join()

        assertTrue(LottieCacheFiles.cacheDir.listFiles().orEmpty().none { it.extension == "lrcc" })
    }

    @Test
    fun `preparing an entry already on disk does not render it again`() = runBlocking {
        val variant = LottieRenderCache.desktopVariant(tinyLottie, null)!!
        val file = LottieRenderCache.prepare(tinyLottie, variant).await()
        val written = file.lastModified()
        file.setLastModified(written - 60_000)

        val again = LottieRenderCache.prepare(tinyLottie, variant).await()

        assertEquals(file, again)
        assertEquals(written - 60_000, again.lastModified(), "the file on disk was used as it was")
    }

    @Test
    fun `the folder pass with no folder set only tidies the cache`() {
        LottieRenderCache.ensureForFolder("", atem)

        assertTrue(LottieCacheFiles.cacheDir.listFiles().orEmpty().none { it.extension == "lrcc" })
    }

    @Test
    fun `an ATEM with no address gets no ATEM variants`() = runBlocking {
        val file = File(folder, "Welcome.json").apply { writeText(tinyLottie) }
        val unaddressed = AtemSettings(host = "", renderWidth = 64, renderHeight = 36)

        LottieRenderCache.ensureForFile(file, unaddressed).join()
        LottieRenderCache.awaitInFlight()

        assertTrue(LottieRenderCache.isReady(tinyLottie, LottieRenderCache.desktopVariant(tinyLottie, unaddressed)!!))
        assertFalse(
            LottieRenderCache.isReady(tinyLottie, LottieRenderCache.atemVariant(tinyLottie, unaddressed, clip = false)),
            "nothing is rendered for a switcher the app cannot reach",
        )
    }

    /** A lottie that ends where it starts is a still: no clip to play, and an ATEM gets the still alone. */
    @Test
    fun `a lottie of no length gets an ATEM still and no clip`() = runBlocking {
        val still = """{"v":"5.7.4","fr":30,"ip":0,"op":0,"w":64,"h":36,"layers":[]}"""
        val file = File(folder, "Still.json").apply { writeText(still) }

        LottieRenderCache.ensureForFile(file, atem).join()
        LottieRenderCache.awaitInFlight()

        assertNull(LottieRenderCache.desktopVariant(still, atem), "there is no playback clip to prepare")
        assertTrue(LottieRenderCache.isReady(still, LottieRenderCache.atemVariant(still, atem, clip = false)))
        assertFalse(LottieRenderCache.isReady(still, LottieRenderCache.atemVariant(still, atem, clip = true)))
    }

    @Test
    fun `progress starts empty for an entry nothing has rendered`() {
        val unseen = tinyLottie.replace("\"layers\"", "\"nm\":\"unseen\",\"layers\"")
        val variant = LottieRenderCache.desktopVariant(unseen, null)!!

        assertEquals(0f, LottieRenderCache.progressFlow(unseen, variant).value)
    }

    /** The cache outlives the app: an entry a previous run left on disk reads full the first time it is asked. */
    @Test
    fun `progress reads full at once for an entry an earlier run cached`() {
        val earlier = tinyLottie.replace("\"layers\"", "\"nm\":\"earlier\",\"layers\"")
        val variant = LottieRenderCache.desktopVariant(earlier, null)!!
        LottieCacheFiles.cacheFile(LottieCacheFiles.keyFor(earlier, variant)).apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(0))
        }

        assertEquals(1f, LottieRenderCache.progressFlow(earlier, variant).value)
    }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}

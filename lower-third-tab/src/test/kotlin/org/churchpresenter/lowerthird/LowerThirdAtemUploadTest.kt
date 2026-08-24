@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import org.churchpresenter.lowerthird.SkiaLottieFrameRenderer
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.churchpresenter.ui.screenshot.latchSkikoNativeLibrary
import org.churchpresenter.companionserver.LottieRenderCache
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.atem.AtemState
import org.churchpresenter.atem.FakeAtemSwitcher
import org.churchpresenter.settings.AtemSettings
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Uploading a lower third to the switcher from the tab itself — the button an operator presses,
 * rather than the REST route a Stream Deck hits.
 *
 * `CompanionServerAtemUploadTest` covers the server's path to the same switcher. This is a **second
 * implementation**: the tab builds its own `AtemClient`, connects, transfers and disconnects inline,
 * so nothing the server suite asserts speaks for it. That is the same trap #140 recorded for the two
 * presentation-upload routes.
 *
 * The three pieces that make it affordable are all established elsewhere and reused here: a
 * [FakeAtemSwitcher] on loopback instead of a real device, the render warmed once in `@BeforeAll`
 * so no test pays skiko's ~900 ms start-up, and the payload size read out of that same warmed cache
 * because the fake completes a transfer at a byte count it has to be told in advance.
 *
 * The fixture lottie is a pair of offset solid layers, not the shared empty-layer one: a uniform
 * frame run-length-encodes to about fifty bytes and rides in a single chunk, which would make "the
 * frame reached the switcher" true without a transfer having happened.
 */
// JUnit 5, not the JUnit 4 hooks this class arrived with. `:composeApp` pins `kotlin-test` back to
// its JUnit 4 flavour with a `capabilitiesResolution` block; the modules of this build take the
// platform default, where `org.junit.BeforeClass` still *compiles* and is simply never run — so the
// warm-up below silently did nothing and the frame it measures came out at zero bytes.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LowerThirdAtemUploadTest {

    private companion object {
        const val RENDER_W = 320
        const val RENDER_H = 180

        private lateinit var tempHome: File
        private var realHome: String? = null

        /**
         * Size of the encoded frame the tab will send. Class-level on purpose: skiko's ~900 ms
         * start-up is paid once here rather than inside a test's own `time=`, which is where a
         * per-test `@BeforeTest` would have put it.
         */
        private var payloadBytes: Int = 0

        /** Two offset solids, so the render has detail and does not encode to nothing. */
        const val DETAILED_LOTTIE =
            """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":320,"h":180,"assets":[],"layers":[""" +
                """{"ddd":0,"ind":1,"ty":1,"nm":"left","sr":1,"ks":{"o":{"a":0,"k":100},""" +
                """"p":{"a":0,"k":[80,90,0]},"a":{"a":0,"k":[60,45,0]},"s":{"a":0,"k":[100,100,100]}},""" +
                """"sc":"#0088ff","sw":120,"sh":90,"ip":0,"op":60,"st":0},""" +
                """{"ddd":0,"ind":2,"ty":1,"nm":"back","sr":1,"ks":{"o":{"a":0,"k":100},""" +
                """"p":{"a":0,"k":[160,90,0]},"a":{"a":0,"k":[160,90,0]},"s":{"a":0,"k":[100,100,100]}},""" +
                """"sc":"#ff8800","sw":320,"sh":180,"ip":0,"op":60,"st":0}]}"""

        @JvmStatic
        @BeforeAll
        fun isolateHomeAndWarmRender() {
            // `latchToTestHome` is not needed here: it exists to pin `InstanceLinkLogger`'s lazy
            // path, and nothing in this module writes one. `CrashReporter`, which `LottieFrameStream`
            // does use, resolves its directory per call.
            latchSkikoNativeLibrary()
            realHome = System.getProperty("user.home")
            tempHome = Files.createTempDirectory("cp-lowerthird-atem-home").toFile()
            System.setProperty("user.home", tempHome.absolutePath)

            // Warm the render and take the encoded size from it: the fake needs the byte count up
            // front, and the tab hits this same cache entry rather than rendering again.
            val settings = AtemSettings(host = "127.0.0.1", renderWidth = RENDER_W, renderHeight = RENDER_H)
            val variant = LottieRenderCache.atemVariant(DETAILED_LOTTIE, settings, clip = false)
            val cached = runBlocking {
                LottieRenderCache.prepare(DETAILED_LOTTIE, variant, SkiaLottieFrameRenderer).await()
            }
            payloadBytes = LottieRenderCache.Reader(cached).use {
                it.nextAtemFrame(RENDER_W, RENDER_H).data.size
            }
        }

        @JvmStatic
        @AfterAll
        fun restoreHome() {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
    }

    /**
     * `AtemUploadStatus` is a process-wide object shared with the Companion upload endpoints, so a
     * status published here outlives the test that published it.
     *
     * These tests end by tearing down the composition while a transfer is still in flight, which
     * leaves behind a failed entry — "Upload failed: rememberCoroutineScope left the composition".
     * In `:composeApp` that was somebody else's fork; in this module the very next class is
     * `LowerThirdTabScreenshotTest`, which drew the leftover banner into `atem_nothing_selected` and
     * failed the comparison. Clear it here rather than defending against it there.
     */
    @AfterTest
    fun clearUploadStatus() {
        AtemUploadStatus.state.value?.let { AtemUploadStatus.clear(it.id) }
    }

    private fun state() = AtemState(
        videoMode = "1080p59.94",
        fps = 60000.0 / 1001.0,
        mixEffectCount = 4,
        downstreamKeyers = 2,
        keyersPerMe = listOf(4, 4, 4, 4),
        stillSlots = (0 until 8).map { AtemMediaSlot(index = it, isUsed = false, name = "") },
        clipSlots = listOf(AtemMediaSlot(index = 0, isUsed = false, name = "")),
    )

    @Test
    fun `the preset is rendered and pushed into the switcher's media pool`() {
        FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4).use { fake ->
            fake.expectedTransferBytes = payloadBytes

            lowerThirdTab(
                folder = lottieFolderWithContent("Welcome" to DETAILED_LOTTIE),
                atemReachable = true,
                atemHost = "127.0.0.1",
                atemPort = fake.port,
                atemRenderWidth = RENDER_W,
                atemRenderHeight = RENDER_H,
                queryAtemState = { _, _ -> state() },
            ) { _ ->
                openAtemDialog()

                onNodeWithText("Upload").performClick()

                // The upload runs off the click, so the datagrams are the signal — not the click.
                val locks = fake.awaitCommandsNamed("LOCK", 2)
                assertEquals(1, locks.first()[2].toInt(), "the media store is locked before the transfer")
                assertEquals(0, locks.last()[2].toInt(), "and released after it")

                assertEquals(1, fake.commandsNamed("FTSD").size, "one transfer was started")
                val sent = fake.commandsNamed("FTDa").sumOf {
                    ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
                }
                assertEquals(payloadBytes, sent, "every encoded byte of the rendered frame reaches the switcher")
            }
        }
    }

    @Test
    fun `the fixture renders to something worth transferring`() {
        // Guards the assertion above: a uniform frame encodes to ~50 bytes and rides in one chunk,
        // so "every byte arrived" would hold without a transfer having been exercised.
        assertTrue(
            payloadBytes > 1_000,
            "the rendered frame is only $payloadBytes bytes — the fixture has stopped producing detail"
        )
    }
}

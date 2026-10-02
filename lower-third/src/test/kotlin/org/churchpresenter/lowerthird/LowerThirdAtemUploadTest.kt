@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.churchpresenter.lowerthird.render.LottieRenderCache
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import org.churchpresenter.atem.FakeAtemSwitcher
import org.churchpresenter.settings.AtemSettings
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
class LowerThirdAtemUploadTest {

    private companion object {
        const val RENDER_W = 320
        const val RENDER_H = 180
        const val QUICK_STILL = "Upload image to ATEM slot 1"
        const val QUICK_CLIP = "Upload video to ATEM slot 1"

        private lateinit var tempHome: File
        private var realHome: String? = null

        /**
         * Size of the encoded frame the tab will send. Class-level on purpose: skiko's ~900 ms
         * start-up is paid once here rather than inside a test's own `time=`, which is where a
         * per-test `@BeforeTest` would have put it.
         */
        private var payloadBytes: Int = 0

        /** Frames in the quick clip, and the size of each (identical — the fixture is static). */
        private var clipFrames: Int = 0
        private var clipFrameBytes: Int = 0

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
            // Skiko unpacks its native library under `user.home` once per JVM; loading a skia class
            // first pins it to the suite's own home, before the swap below points at a temp dir.
            Class.forName("org.jetbrains.skia.Surface")
            realHome = System.getProperty("user.home")
            tempHome = Files.createTempDirectory("cp-lowerthird-atem-home").toFile()
            System.setProperty("user.home", tempHome.absolutePath)

            // Warm the render and take the encoded size from it: the fake needs the byte count up
            // front, and the tab hits this same cache entry rather than rendering again.
            val settings = AtemSettings(host = "127.0.0.1", renderWidth = RENDER_W, renderHeight = RENDER_H)
            val variant = LottieRenderCache.atemVariant(DETAILED_LOTTIE, settings, clip = false)
            val cached = runBlocking { LottieRenderCache.prepare(DETAILED_LOTTIE, variant).await() }
            payloadBytes = LottieRenderCache.Reader(cached).use {
                it.nextAtemFrame(RENDER_W, RENDER_H).data.size
            }

            // The quick clip button's variant: the settings' own clip rate, not a detected one.
            val clipVariant =
                LottieRenderCache.atemVariant(DETAILED_LOTTIE, settings, clip = true, fps = settings.clipFps)
            val cachedClip = runBlocking { LottieRenderCache.prepare(DETAILED_LOTTIE, clipVariant).await() }
            LottieRenderCache.Reader(cachedClip).use { reader ->
                clipFrames = reader.frameCount
                val sizes = (0 until clipFrames).map { reader.nextAtemFrame(RENDER_W, RENDER_H).data.size }
                clipFrameBytes = sizes.first()
                check(sizes.all { it == clipFrameBytes }) { "the fixture stopped being static: $sizes" }
            }
        }

        @JvmStatic
        @AfterAll
        fun restoreHome() {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
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
                // Waited for through Compose rather than the fake's blocking poll: the upload
                // coroutine resumes on the composition's test dispatcher after each IO step, and
                // only a wait that keeps that dispatcher running lets it reach the transfer.
                waitUntil(timeoutMillis = 5_000) { fake.commandsNamed("LOCK").size >= 2 }
                val locks = fake.commandsNamed("LOCK")
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
    fun `the quick image button pushes the same still without opening the dialog`() {
        FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4).use { fake ->
            fake.expectedTransferBytes = payloadBytes

            lowerThirdTab(
                folder = lottieFolderWithContent("Welcome" to DETAILED_LOTTIE),
                atemReachable = true,
                atemHost = "127.0.0.1",
                atemPort = fake.port,
                atemRenderWidth = RENDER_W,
                atemRenderHeight = RENDER_H,
                quickUpload = true,
            ) { _ ->
                selectPreset("Welcome")
                waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { hasLtButton(QUICK_STILL) }

                ltButton(QUICK_STILL).performClick()

                waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { fake.commandsNamed("LOCK").size >= 2 }
                val sent = fake.commandsNamed("FTDa").sumOf {
                    ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
                }
                assertEquals(payloadBytes, sent, "the whole still reaches the switcher's first slot")
            }
        }
    }

    @Test
    fun `the quick video button pushes every frame of the clip`() {
        FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4).use { fake ->
            fake.expectedTransferBytes = clipFrameBytes
            fake.clipFramesOnCommit = clipFrames

            lowerThirdTab(
                folder = lottieFolderWithContent("Welcome" to DETAILED_LOTTIE),
                atemReachable = true,
                atemHost = "127.0.0.1",
                atemPort = fake.port,
                atemRenderWidth = RENDER_W,
                atemRenderHeight = RENDER_H,
                quickUpload = true,
            ) { _ ->
                selectPreset("Welcome")
                waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { hasLtButton(QUICK_CLIP) }

                ltButton(QUICK_CLIP).performClick()

                // The commit is the last thing the client sends, so it is the signal the clip is up.
                waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { fake.commandsNamed("SMPC").isNotEmpty() }
                assertEquals(clipFrames, fake.commandsNamed("FTSD").size, "one transfer per frame")
            }
        }
    }

    @Test
    fun `going live with the key armed cuts the ATEM key on air`() {
        FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4).use { fake ->
            try {
                lowerThirdTab(
                    folder = lottieFolderWithContent("Welcome" to DETAILED_LOTTIE),
                    atemReachable = true,
                    atemHost = "127.0.0.1",
                    atemPort = fake.port,
                    settings = { s -> s.copy(atemSettings = s.atemSettings.copy(goLiveKey = true, keyIndex = 1)) },
                ) { reports ->
                    selectPreset("Welcome")

                    ltButton(LowerThirdLabel.GO_LIVE).performClick()
                    waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { fake.commandsNamed("CKOn").isNotEmpty() }

                    val cmd = fake.commandsNamed("CKOn").first()
                    assertEquals(1, cmd[1].toInt(), "the armed keyer")
                    assertEquals(1, cmd[2].toInt(), "goes on air")
                    assertTrue(reports.live.isEmpty(), "the key, not the output window, takes it live")
                }
            } finally {
                runBlocking { LowerThirdSequencer.stop() }
            }
        }
    }

    @Test
    fun `a transfer the switcher refuses is reported in the dialog`() {
        FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4, ftdeFatalCode = 5).use { fake ->
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

                waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                    onAllNodesWithText("Upload failed", substring = true)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
            }
        }
    }

    @Test
    fun `going live as a downstream key drives the DSK instead`() {
        FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2, keyersPerMe = 4).use { fake ->
            try {
                lowerThirdTab(
                    folder = lottieFolderWithContent("Welcome" to DETAILED_LOTTIE),
                    atemReachable = true,
                    atemHost = "127.0.0.1",
                    atemPort = fake.port,
                    settings = { s ->
                        val atem = s.atemSettings.copy(goLiveKey = true, useDownstreamKey = true, dskIndex = 1)
                        s.copy(atemSettings = atem)
                    },
                ) { _ ->
                    selectPreset("Welcome")

                    ltButton(LowerThirdLabel.GO_LIVE).performClick()
                    waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                        (fake.commandsNamed("CDsL") + fake.commandsNamed("DDsA")).isNotEmpty()
                    }

                    assertTrue(fake.commandsNamed("CKOn").isEmpty(), "no upstream keyer is touched")
                }
            } finally {
                runBlocking { LowerThirdSequencer.stop() }
            }
        }
    }

    @Test
    fun `a quick clip too long for its slot is refused, and says why`() =
        lowerThirdTab(
            folder = lottieFolderWithContent("Welcome" to DETAILED_LOTTIE),
            atemReachable = true,
            quickUpload = true,
            settings = { s -> s.copy(atemSettings = s.atemSettings.copy(detectedClipMaxFrames = listOf(10))) },
        ) { _ ->
            selectPreset("Welcome")
            waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                onAllNodes(hasContentDescription("Clip needs", substring = true))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }

            onAllNodes(hasContentDescription("Clip needs", substring = true))[0].assertIsNotEnabled()
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

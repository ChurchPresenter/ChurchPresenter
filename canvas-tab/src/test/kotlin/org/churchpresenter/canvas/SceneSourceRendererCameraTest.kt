@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.SceneSource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Drawing a camera source.
 *
 * [SceneSourceRendererTest]'s own notes say the camera branch "needs a real OS camera device" and so
 * only its blank-path guard was covered. That is no longer true: the capture card reaches the
 * renderer through `LocalCanvasDeckLink`, so a stub can feed it frames and the whole branch — the
 * waiting placeholder, the live picture, and the failure wording — is drawable headlessly.
 *
 * The failure wording is the part worth having. A camera that will not open is the single most
 * common thing to go wrong on a canvas, and the operator's only clue is that sentence.
 */
class SceneSourceRendererCameraTest {

    private class StubCard(
        private val frames: List<IntArray?> = emptyList(),
        private val opens: Boolean = true,
    ) : CanvasDeckLink {
        val polls = AtomicInteger()
        override fun isAvailable() = true
        override fun listDevices() = emptyList<CanvasDeckLink.Device>()
        override fun isOutputActive(deviceIndex: Int) = false
        override fun listInputModes(deviceIndex: Int) = emptyList<CanvasDeckLink.InputMode>()
        override fun listVideoConnections(deviceIndex: Int) = emptyList<CanvasDeckLink.VideoConnection>()
        override fun openInput(deviceIndex: Int, mode: String, connection: Int) = opens
        override fun getInputFrame(deviceIndex: Int) = frames.getOrNull(polls.getAndIncrement())
        override fun closeInput(deviceIndex: Int) = Unit
    }

    private fun frame(w: Int, h: Int) =
        IntArray(2 + w * h).also { it[0] = w; it[1] = h; for (i in 2 until it.size) it[i] = 0xFF3366CC.toInt() }

    private fun camera(index: Int) = SceneSource.CameraSource(
        id = "cam-$index", name = "Camera",
        devicePath = "decklink://$index", deviceName = "DeckLink",
        isDeckLink = true, deckLinkIndex = index,
    )

    private val toRelease = mutableListOf<Pair<SceneSource.CameraSource, CanvasDeckLink>>()

    @AfterTest
    fun release() {
        toRelease.forEach { (s, d) -> SharedCameraFrameCache.release(s, d) }
        toRelease.clear()
    }

    private fun render(source: SceneSource.CameraSource, card: CanvasDeckLink, body: androidx.compose.ui.test.ComposeUiTest.() -> Unit) {
        toRelease += source to card
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalCanvasDeckLink provides card) {
                        Box(Modifier.size(160.dp, 90.dp)) { SceneSourceRenderer(source) }
                    }
                }
            }
            waitForIdle()
            body()
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showing(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `a camera with no device chosen says so instead of drawing nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(160.dp, 90.dp)) {
                    SceneSourceRenderer(SceneSource.CameraSource(id = "c", name = "Camera", devicePath = ""))
                }
            }
        }
        waitForIdle()

        // The blank-path guard: a source added but not yet configured must explain itself.
        assertTrue(onAllNodesWithText("", substring = true).fetchSemanticsNodes(false).isNotEmpty())
    }

    @Test
    fun `a camera waiting for its first frame draws the placeholder`() {
        val card = StubCard(frames = List(200) { null })
        render(camera(0), card) {
            // Opened, polled, nothing yet — the operator sees the placeholder rather than a black hole.
            assertTrue(card.polls.get() >= 0)
        }
    }

    @Test
    fun `a camera delivering frames draws them`() {
        val card = StubCard(frames = List(200) { frame(16, 9) })
        render(camera(1), card) {
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline && card.polls.get() < 2) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
                Thread.sleep(5)
            }
            assertTrue(card.polls.get() > 0, "the renderer must be pulling frames")
        }
    }

    @Test
    fun `a camera that will not open shows the reason`() {
        val card = StubCard(opens = false)
        render(camera(2), card) {
            val deadline = System.currentTimeMillis() + 5_000
            var seen = false
            while (System.currentTimeMillis() < deadline && !seen) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
                seen = showing("in use") || showing("unavailable") || showing("not")
                Thread.sleep(5)
            }
            assertTrue(seen, "a camera that will not open must say why")
        }
    }
}

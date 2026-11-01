@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import java.awt.image.BufferedImage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The app's `AccessibleNamesTest` and `FocusRingVisibleTest` for the Signage tab, checked here: the
 * tab is dev mode only and needs a running hub, which the main-window harness does not start. The
 * tab is shown with everything it can show at once: a player waiting, a connected screen with its
 * picture and an update on offer, and playlists.
 */
class SignageTabAccessibilityTest {
    private val world = SignageWorld(folders = listOf("", "Events"))

    @AfterTest
    fun cleanUp() = world.close()

    private fun everything(block: ComposeUiTest.() -> Unit) {
        world.file("welcome.jpg")
        world.file("Events/flyer.png")
        runBlocking { world.startAndPairScreen() }
        world.scope.async {
            world.hub.pairing.await(
                PlayerMessage.Hello("waiting", "26.0.8", 1, 1, null, 1920, 1080, Platform.LINUX_ARM64, "4821"),
                "10.0.0.7",
            )
        }
        world.hub.links.reported("player-1", PlayerMessage.Preview(jpegBase64(), 480, 270, 1))
        signageTab(world) {
            waitUntil(timeoutMillis = SignageWorld.WAIT_MS) {
                listOf("4821", "Events", "Update to 26.0.9")
                    .all { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
            }
            block()
        }
    }

    private fun SemanticsNode.name(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
        config.getOrNull(SemanticsProperties.EditableText)?.text,
    ).joinToString(" ").trim()

    @Test
    fun everyControlIsNamed() = everything {
        val unnamed = onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.name().isBlank() }
            .map { "${it.config.getOrNull(SemanticsProperties.TestTag) ?: "(no tag)"} at ${it.boundsInRoot}" }
        assertTrue(unnamed.isEmpty(), "${unnamed.size} unnamed controls:\n${unnamed.joinToString("\n")}")
    }

    private class Stop(val name: String, val bounds: Rect)

    /** Presses Tab and returns where focus went, or null when nothing has it. */
    private fun ComposeUiTest.tab(): Stop? {
        onRoot().performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        return onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
            .fetchSemanticsNodes().firstOrNull()?.let { Stop(it.name(), it.boundsInRoot) }
    }

    @Test
    fun everyStopShowsFocusAndTabMovesOn() = everything {
        val stops = mutableListOf<Stop>()
        val pictures = mutableListOf<BufferedImage>()
        var presses = 0
        var stop = tab()
        fun backToFirst(at: Stop?) = at != null && stops.isNotEmpty() && stops[0].same(at)
        while (presses++ < MAX_STOPS && !backToFirst(stop)) {
            if (stop != null) {
                assertTrue(stops.isEmpty() || !stops.last().same(stop), "Tab is stuck on ${stop.name}")
                stops += stop
                pictures += onAllNodes(isRoot())[0].captureToImage().toAwtImage()
            }
            stop = tab()
        }
        assertTrue(stops.size > 1, "Tab reached ${stops.size} stops")
        // Each stop's surroundings, with it focused, against the same place while the stop farthest
        // from it has focus: a stop whose picture does not change is one nobody can see.
        val unseen = stops.indices.filter { i ->
            val farthest = stops.indices.maxBy { j -> (stops[j].bounds.center - stops[i].bounds.center).getDistance() }
            val bounds = stops[i].bounds
            bounds.width > 0 && bounds.height > 0 &&
                pictures[i].crop(bounds).contentEquals(pictures[farthest].crop(bounds))
        }.map { "${stops[it].name} at ${stops[it].bounds}" }
        assertTrue(unseen.isEmpty(), "focus is invisible on:\n${unseen.joinToString("\n")}")
    }

    private fun Stop.same(other: Stop) =
        name == other.name && (bounds.center - other.bounds.center).getDistance() < SAME_STOP

    private fun BufferedImage.crop(bounds: Rect): IntArray {
        val l = (bounds.left - PAD).toInt().coerceIn(0, width - 1)
        val t = (bounds.top - PAD).toInt().coerceIn(0, height - 1)
        val r = (bounds.right + PAD).toInt().coerceIn(l + 1, width)
        val b = (bounds.bottom + PAD).toInt().coerceIn(t + 1, height)
        return getRGB(l, t, r - l, b - t, null, 0, r - l)
    }

    private companion object {
        const val MAX_STOPS = 80
        const val PAD = 4f
        const val SAME_STOP = 4f
    }
}

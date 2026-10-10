@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import org.churchpresenter.sharedui.models.Tabs
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every control Tab lands on shows that it has focus, and Tab always moves on: around each focus
 * stop of the main window, one tab at a time, the picture changes when focus arrives. A stop where
 * nothing changes is one a keyboard operator cannot see, which is the same as not being able to
 * find it; a field that keeps the Tab key is one they cannot leave.
 *
 * The window itself and a control laid out at no size (clipped off this test window) are not
 * stops anyone sees, and are skipped. The Web tab is `:web`'s own, as in [AccessibleNamesTest].
 */
class FocusRingVisibleTest : MainDesktopComposeHarness() {

    private fun ComposeUiTest.shot(): BufferedImage = onAllNodes(isRoot())[0].captureToImage().toAwtImage()

    private class Stop(val name: String, val bounds: Rect) {
        val label get() = "$name at $bounds"

        fun distanceTo(other: Stop) = (bounds.center - other.bounds.center).getDistance()
    }

    private fun BufferedImage.crop(bounds: Rect): IntArray {
        val l = (bounds.left - PAD).toInt().coerceIn(0, width - 1)
        val t = (bounds.top - PAD).toInt().coerceIn(0, height - 1)
        val r = (bounds.right + PAD).toInt().coerceIn(l + 1, width)
        val b = (bounds.bottom + PAD).toInt().coerceIn(t + 1, height)
        return getRGB(l, t, r - l, b - t, null, 0, r - l)
    }

    private fun SemanticsNode.name(): String =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "(unnamed)"

    /** Which recorded stop [node] is: the one of its name nearest to where it is now, if near enough. */
    private fun List<Stop>.indexOf(node: SemanticsNode): Int? = indices
        .filter { this[it].name == node.name() }
        .minByOrNull { (this[it].bounds.center - node.boundsInRoot.center).getDistance() }
        ?.takeIf { (this[it].bounds.center - node.boundsInRoot.center).getDistance() < SAME_STOP_DISTANCE }

    private fun ComposeUiTest.focused(): SemanticsNode? =
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).fetchSemanticsNodes().firstOrNull()

    /** Whether a ring around [bounds] is something an operator could see: not the window, not clipped away. */
    private fun visibleStop(bounds: Rect, window: BufferedImage) =
        bounds.width > 0f && bounds.height > 0f && !(bounds.width >= window.width && bounds.height >= window.height)

    /**
     * Tab once round the window. Each stop's surroundings are kept as it looks with focus, and two
     * whole pictures to compare against: one with the first stop focused, and one with the stop
     * farthest from it. Each stop is held against whichever of those had focus farther from it, so
     * a ring already showing when the tab opens (a search box focused on opening), or the ring of
     * the stop next door, cannot pass for its own.
     */
    private fun assertEveryStopShowsFocus(tab: Tabs) = root(showingOnly(tab), devMode = false) {
        val stops = mutableListOf<Stop>()
        val focusedCrop = mutableListOf<IntArray>()
        var first: BufferedImage? = null
        var farthest: BufferedImage? = null
        var farthestStop: Stop? = null
        var wrapped = false
        while (!wrapped && stops.size < MAX_PRESSES) {
            press(Key.Tab)
            val node = focused()
            // Round once the first stop has focus again -- not on meeting any earlier name: a scrolling
            // row can bring a second chip of a name to where the first one stood.
            wrapped = node != null && stops.isNotEmpty() && stops.take(1).indexOf(node) == 0
            if (node != null && !wrapped) {
                // A field that keeps the key is a trap: Tab must always move focus somewhere else.
                val stuck = stops.size > 1 && stops.takeLast(1).indexOf(node) == 0
                assertTrue(!stuck, "on $tab, Tab is stuck on ${node.name()}: it never moves focus on")
                val stop = Stop(node.name(), node.boundsInRoot)
                val picture = shot()
                stops += stop
                focusedCrop += picture.crop(stop.bounds)
                if (first == null) {
                    first = picture
                } else if (farthestStop == null || stop.distanceTo(stops[0]) > farthestStop.distanceTo(stops[0])) {
                    farthest = picture
                    farthestStop = stop
                }
            }
        }
        val window = requireNotNull(first) { "Tab reached nothing on $tab" }
        val unseen = stops.indices.filter { i ->
            val stop = stops[i]
            val baseline = if (farthestStop == null || stop.distanceTo(stops[0]) >= stop.distanceTo(farthestStop)) {
                window
            } else {
                requireNotNull(farthest)
            }
            stops.size > 1 &&
                visibleStop(stop.bounds, window) &&
                focusedCrop[i].contentEquals(baseline.crop(stop.bounds))
        }.map { stops[it].label }
        assertTrue(unseen.isEmpty(), "on $tab, focus is invisible on:\n${unseen.joinToString("\n")}")
    }

    /** One per tab, so a new tab cannot be left out. */
    @Test
    fun `every tab has a test of its own`() {
        val covered = this::class.java.declaredMethods.map { it.name }.toSet()
        val missing = (Tabs.entries - Tabs.WEB).filter { tab -> covered.none { it.startsWith("the ${tab.name} tab") } }
        assertTrue(missing.isEmpty(), "no focus-ring test for: $missing")
    }

    @Test fun `the BIBLE tab shows focus`() = assertEveryStopShowsFocus(Tabs.BIBLE)

    @Test fun `the SONGS tab shows focus`() = assertEveryStopShowsFocus(Tabs.SONGS)

    @Test fun `the PICTURES tab shows focus`() = assertEveryStopShowsFocus(Tabs.PICTURES)

    @Test fun `the PRESENTATION tab shows focus`() = assertEveryStopShowsFocus(Tabs.PRESENTATION)

    @Test fun `the MEDIA tab shows focus`() = assertEveryStopShowsFocus(Tabs.MEDIA)

    @Test fun `the LOWER_THIRD tab shows focus`() = assertEveryStopShowsFocus(Tabs.LOWER_THIRD)

    @Test fun `the ANNOUNCEMENTS tab shows focus`() = assertEveryStopShowsFocus(Tabs.ANNOUNCEMENTS)

    @Test fun `the CANVAS tab shows focus`() = assertEveryStopShowsFocus(Tabs.CANVAS)

    @Test fun `the QA tab shows focus`() = assertEveryStopShowsFocus(Tabs.QA)

    @Test fun `the STT tab shows focus`() = assertEveryStopShowsFocus(Tabs.STT)

    @Test fun `the CROSSWORD tab shows focus`() = assertEveryStopShowsFocus(Tabs.CROSSWORD)

    @Test fun `the DICTIONARY tab shows focus`() = assertEveryStopShowsFocus(Tabs.DICTIONARY)

    @Test fun `the COMPANION_SURFACE tab shows focus`() = assertEveryStopShowsFocus(Tabs.COMPANION_SURFACE)

    private companion object {
        /** How far a stop may move between the two passes and still be the same stop. */
        const val SAME_STOP_DISTANCE = 40f

        /** Well past one full cycle of the busiest tab; each loop ends as soon as the cycle wraps. */
        const val MAX_PRESSES = 200

        /** How far outside a control its ring may be drawn -- a search well rings the whole well around its field. */
        const val PAD = 16f
    }
}

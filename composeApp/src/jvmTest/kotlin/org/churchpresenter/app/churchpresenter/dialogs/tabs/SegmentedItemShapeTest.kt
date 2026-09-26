package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.theme.AppShape

/**
 * `segmentedItemShape` rounds the outer corners of a segmented row so the buttons read as one
 * control: the first item is rounded on the left, the last on the right, anything between is square,
 * and a row of one is rounded all round.
 *
 * Every segmented row on the tab has two or three items, so the single-item case cannot be reached
 * by driving the UI — but it is the branch that decides whether a one-option row looks like a
 * button or like a fragment, so it is worth pinning. The function is a private top-level one, which
 * `AGENT.md` names as the case where reflection is the fallback rather than widening it to
 * `internal`; no production code is changed to support this test.
 */
class SegmentedItemShapeTest {

    private val method = Class.forName("org.churchpresenter.app.churchpresenter.dialogs.tabs.SongSettingsTabKt")
        .getDeclaredMethod("segmentedItemShape", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        .apply { isAccessible = true }

    private fun shapeOf(index: Int, count: Int): Shape = method.invoke(null, index, count) as Shape

    private val radius = 4.dp

    @Test
    fun `a row of one item is rounded on every corner`() {
        assertEquals(
            AppShape(radius),
            shapeOf(index = 0, count = 1),
            "a lone segment is the whole control, so all four corners are rounded",
        )
    }

    @Test
    fun `the first item of a row is rounded only on its leading edge`() {
        assertEquals(
            AppShape(topStart = radius, bottomStart = radius, topEnd = 0.dp, bottomEnd = 0.dp),
            shapeOf(index = 0, count = 3),
            "the first segment must stay square where it meets the next one",
        )
    }

    @Test
    fun `the last item of a row is rounded only on its trailing edge`() {
        assertEquals(
            AppShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = radius, bottomEnd = radius),
            shapeOf(index = 2, count = 3),
            "the last segment must stay square where it meets the previous one",
        )
    }

    @Test
    fun `an item between two others is square on both edges`() {
        assertEquals(
            AppShape(0.dp),
            shapeOf(index = 1, count = 3),
            "a middle segment touches a neighbour on both sides",
        )
    }

    @Test
    fun `a two-item row rounds one outer edge each`() {
        assertEquals(
            AppShape(topStart = radius, bottomStart = radius, topEnd = 0.dp, bottomEnd = 0.dp),
            shapeOf(index = 0, count = 2),
            "the left half of a pair",
        )
        assertEquals(
            AppShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = radius, bottomEnd = radius),
            shapeOf(index = 1, count = 2),
            "the right half of a pair",
        )
    }
}

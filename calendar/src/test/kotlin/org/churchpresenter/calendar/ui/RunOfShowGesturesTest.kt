@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.churchpresenter.calendar.CalendarStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The run of show driven by keys and by touch: the duration cell's keys, and the drag grip. */
class RunOfShowGesturesTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    @Test
    fun `Enter commits a typed length`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")
        clickFirst("5:00")
        clearFirstField()
        typeIntoFirstField("6:30")

        onAllNodes(hasSetTextAction())[0].performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        assertEquals(390, stored(folder).services.single().plannedSeconds["a"])
        assertEquals(0, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size, "the cell closed")
    }

    @Test
    fun `Escape throws a typed length away`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")
        clickFirst("5:00")
        clearFirstField()
        typeIntoFirstField("6:30")

        onAllNodes(hasSetTextAction())[0].performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(300, stored(folder).services.single().plannedSeconds["a"], "as it was")
        assertEquals(0, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun `another key in the cell is left to the field`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")
        clickFirst("5:00")

        onAllNodes(hasSetTextAction())[0].performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()

        assertEquals(1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size, "still editing")
        assertEquals(300, stored(folder).services.single().plannedSeconds["a"])
    }

    @Test
    fun `a row dragged by its grip onto the row below swaps with it`() = withCalendar(
        documentWith(
            service(items = listOf(song("a", "Amazing Grace"), song("b", "Be Thou My Vision")), planned = emptyMap()),
        ),
    ) { folder ->
        awaitText("Be Thou My Vision")
        val first = onAllNodesWithText("Amazing Grace", substring = true)[0]
        val top = first.fetchSemanticsNode().boundsInRoot.top
        val below = onAllNodesWithText("Be Thou My Vision", substring = true)[0].fetchSemanticsNode().boundsInRoot.top
        val rowPitch = below - top

        first.performTouchInput {
            // The grip sits at the row's start, inside its 9dp padding.
            down(Offset(13.dp.toPx(), centerY))
            repeat(6) { moveBy(Offset(0f, rowPitch / 4f)) }
            up()
        }
        waitForIdle()

        assertEquals(listOf("b", "a"), stored(folder).services.single().items.map { it.id })
    }

    @Test
    fun `a drag that goes nowhere moves nothing`() = withCalendar(
        documentWith(
            service(items = listOf(song("a", "Amazing Grace"), song("b", "Be Thou My Vision")), planned = emptyMap()),
        ),
    ) { folder ->
        awaitText("Be Thou My Vision")

        onAllNodesWithText("Amazing Grace", substring = true)[0].performTouchInput {
            down(Offset(13.dp.toPx(), centerY))
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, -30f))
            up()
        }
        waitForIdle()

        assertEquals(listOf("a", "b"), stored(folder).services.single().items.map { it.id })
        assertNull(stored(folder).services.single().plannedSeconds["a"])
    }
}

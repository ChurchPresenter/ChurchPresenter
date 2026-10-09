package org.churchpresenter.helper.display

import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.UndoEntry
import org.churchpresenter.helper.HelperActionExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DisplaySetupTest {

    private val laptop = HelperScreen(0, isPrimary = true, x = 0, y = 0, width = 1440, height = 900)
    private val projector = HelperScreen(1, isPrimary = false, x = 1440, y = 0, width = 1920, height = 1080)

    @Test
    fun `the flow moves through its steps`() {
        val start = DisplaySetupFlow()
        assertEquals(DisplayStep.DETECT, start.detected(listOf(laptop)).step)
        val pick = start.detected(listOf(laptop, projector))
        assertEquals(DisplayStep.PICK, pick.step)
        val confirm = pick.picking(projector)
        assertEquals(DisplayStep.CONFIRM to projector, confirm.step to confirm.picked)
        // Detecting again past picking leaves the flow where it is.
        assertEquals(confirm, confirm.detected(listOf(laptop)))
        assertEquals(DisplayStep.TEST, confirm.assigned().step)
        assertEquals(DisplayStep.DONE, confirm.assigned().confirmed().step)
        val again = confirm.assigned().retry()
        assertEquals(DisplayStep.PICK to null, again.step to again.picked)
        assertEquals(DisplayStep.DETECT, pick.detected(listOf(laptop)).step)
    }

    @Test
    fun `a screen is labelled by its name, else its number and size`() {
        assertIs<HelperText.Res>(projector.screenLabel())
        val named = projector.copy(name = "Projector").screenLabel() as HelperText.Res
        assertEquals(HelperText.Plain("Projector"), named.args.first())
        assertEquals(HelperText.Plain("2"), (projector.screenLabel() as HelperText.Res).args.first())
    }

    @Test
    fun `assigning the picked screen tests it, and can be undone`() {
        val done = mutableListOf<HelperAction>()
        val undo = UndoEntry(HelperText.Plain("screen")) { true }
        val state = HelperState().apply { displayFlow = DisplaySetupFlow(DisplayStep.CONFIRM, projector) }
        state.assignPickedScreen(
            HelperActionExecutor {
                done += it
                if (it is HelperAction.AssignAudienceScreen) ActionOutcome.Done(undo = undo) else ActionOutcome.Done()
            },
        )
        assertEquals(listOf(HelperAction.AssignAudienceScreen(projector), HelperAction.IdentifyScreens), done)
        assertEquals(DisplayStep.TEST, state.displayFlow.step)
        assertNotNull(state.undoLabel)
    }

    @Test
    fun `a refusal is said, and nothing picked does nothing`() {
        val refused = HelperActionExecutor { ActionOutcome.Refused(HelperText.Plain("live")) }
        val state = HelperState().apply { displayFlow = DisplaySetupFlow(DisplayStep.CONFIRM, projector) }
        state.assignPickedScreen(refused)
        assertEquals(HelperText.Plain("live"), assertIs<HelperReply.Message>(state.reply).text)
        val other = HelperState()
        other.assignPickedScreen(refused)
        assertEquals(HelperReply.Idle, other.reply)
        val guided = HelperState().apply { displayFlow = DisplaySetupFlow(DisplayStep.CONFIRM, projector) }
        guided.assignPickedScreen(HelperActionExecutor { ActionOutcome.DisplaySetup })
        assertEquals(DisplayStep.CONFIRM, guided.displayFlow.step)
        val numbers = HelperState()
        numbers.identifyAgain(refused)
        assertIs<HelperReply.Message>(numbers.reply)
        val fine = HelperState()
        fine.identifyAgain(HelperActionExecutor { ActionOutcome.Done() })
        assertNull((fine.reply as? HelperReply.Message))
    }
}

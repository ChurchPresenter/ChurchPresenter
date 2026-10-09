package org.churchpresenter.helper

import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.ContentScope
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.Persistence
import org.churchpresenter.helper.action.UndoEntry
import org.churchpresenter.helper.display.DisplayStep
import org.churchpresenter.helper.display.DisplaySetupFlow
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done
import org.churchpresenter.strings.generated.resources.helper_nothing_to_undo
import org.churchpresenter.strings.generated.resources.helper_undo_done
import org.churchpresenter.strings.generated.resources.helper_undo_stale
import org.churchpresenter.strings.generated.resources.helper_youre_welcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelperStateTest {

    private val done = HelperActionExecutor { ActionOutcome.Done() }
    private val blue = HelperAction.SetBackgroundColor(ContentScope.SONG, "#1565C0", "blue", Persistence.SAVED)
    private val tour = GuideTour(
        listOf(
            GuideStep(GuideTargets.mainTab(Tabs.SONGS), HelperText.Plain("songs")),
            GuideStep(GuideTargets.NEW_SONG, HelperText.Plain("new"), before = HelperAction.SelectTab(Tabs.SONGS)),
        ),
    )

    private fun said(state: HelperState) = (state.reply as HelperReply.Message).text

    @Test
    fun `an answer moves the reply into the conversation`() {
        val state = HelperState()
        state.request(blue, done)
        assertIs<HelperReply.Confirm>(state.reply)
        state.answer("Do it")
        assertEquals(HelperReply.Idle, state.reply)
        assertIs<ThreadEntry.Wick>(state.thread.entries[0])
        assertEquals(ThreadEntry.Operator("Do it"), state.thread.entries[1])
    }

    @Test
    fun `what is typed resolves to a confirmation, a question or a not-understood`() {
        val state = HelperState()
        state.onResolved(Resolution.Act(blue), done)
        assertIs<HelperReply.Confirm>(state.reply)
        state.onResolved(Resolution.Clarify(HelperText.Plain("which?"), listOf(blue)), done)
        assertIs<HelperReply.Clarify>(state.reply)
        state.onResolved(Resolution.Unknown, done)
        assertEquals(HelperReply.Unknown, state.reply)
        state.onResolved(Resolution.DidYouMean(HelperText.Plain("blue"), blue), done)
        assertEquals(blue, assertIs<HelperReply.DidYouMean>(state.reply).action)
    }

    @Test
    fun `what needs no asking is done straight away`() {
        val state = HelperState()
        state.request(HelperAction.ShowShortcut(ShortcutAction.TAKE), done)
        assertEquals(HelperReply.Shortcut(ShortcutAction.TAKE), state.reply)
        state.request(HelperAction.Greet, done)
        assertEquals(HelperReply.Greeting, state.reply)
        state.request(HelperAction.ShowCommands, done)
        assertEquals(HelperReply.Commands, state.reply)
        state.request(HelperAction.Thanks, done)
        assertEquals(helperText(Res.string.helper_youre_welcome), said(state))
    }

    @Test
    fun `undo with nothing to undo says so without asking`() {
        val state = HelperState()
        state.request(HelperAction.UndoLast, done)
        assertEquals(helperText(Res.string.helper_nothing_to_undo), said(state))
        state.undo()
        assertEquals(helperText(Res.string.helper_nothing_to_undo), said(state))
    }

    @Test
    fun `a change done can be undone, unless it moved on since`() {
        var reverts = true
        val state = HelperState()
        val undoing = HelperActionExecutor {
            ActionOutcome.Done(undo = UndoEntry(HelperText.Plain("the blue")) { reverts })
        }
        state.run(blue, undoing)
        assertTrue(assertIs<HelperReply.Message>(state.reply).canUndo)
        assertEquals(helperText(Res.string.helper_done), said(state))
        assertEquals(HelperText.Plain("the blue"), state.undoLabel)
        state.request(HelperAction.UndoLast, undoing)
        assertIs<HelperReply.Confirm>(state.reply)
        state.run(HelperAction.UndoLast, undoing)
        assertEquals(helperText(Res.string.helper_undo_done), said(state))
        reverts = false
        state.run(blue, undoing)
        state.undo()
        assertEquals(helperText(Res.string.helper_undo_stale), said(state))
    }

    @Test
    fun `refusals, tours and display setup come back from the app`() {
        val state = HelperState()
        state.run(blue, HelperActionExecutor { ActionOutcome.Done(message = HelperText.Plain("done blue")) })
        assertEquals(HelperText.Plain("done blue"), said(state))
        val refusing = HelperActionExecutor {
            ActionOutcome.Refused(HelperText.Plain("no"), instead = HelperAction.Take)
        }
        state.run(blue, refusing)
        assertEquals(HelperAction.Take, assertIs<HelperReply.Message>(state.reply).offer)
        state.run(blue, HelperActionExecutor { ActionOutcome.Guide(tour) })
        assertEquals(HelperReply.Touring(tour, 0), state.reply)
        state.displayFlow = DisplaySetupFlow(DisplayStep.DONE)
        state.run(HelperAction.StartDisplaySetup, HelperActionExecutor { ActionOutcome.DisplaySetup })
        assertEquals(HelperReply.DisplaySetup, state.reply)
        assertEquals(DisplayStep.DETECT, state.displayFlow.step)
    }

    @Test
    fun `a tour rings each step, opening what it needs, then ends`() {
        val opened = mutableListOf<HelperAction>()
        val state = HelperState()
        state.run(HelperAction.Highlight(tour), HelperActionExecutor { opened += it; ActionOutcome.Done() })
        assertEquals(GuideTargets.mainTab(Tabs.SONGS), state.session.activeTarget)
        state.nextStep(HelperActionExecutor { opened += it; ActionOutcome.Done() }, said = "Next")
        assertEquals(listOf<HelperAction>(HelperAction.SelectTab(Tabs.SONGS)), opened)
        assertEquals(GuideTargets.NEW_SONG, state.session.activeTarget)
        assertEquals(ThreadEntry.Operator("Next"), state.thread.entries.last())
        state.nextStep(done)
        assertEquals(HelperReply.Idle, state.reply)
        assertNull(state.session.activeTarget)
        // Not touring: nothing to step.
        state.nextStep(done)
        assertEquals(HelperReply.Idle, state.reply)
    }

    @Test
    fun `closing and clearing end everything`() {
        val state = HelperState().apply { isOpen = true; confirmingHide = true }
        state.run(HelperAction.Highlight(tour), done)
        state.close()
        assertEquals(false, state.isOpen)
        assertEquals(false, state.confirmingHide)
        assertNull(state.session.activeTarget)
        state.thread.said("hi")
        state.clear()
        assertTrue(state.thread.entries.isEmpty())
    }

    @Test
    fun `every reply leaves a line in the conversation, except the empty ones`() {
        val shown = listOf(
            HelperReply.Confirm(blue),
            HelperReply.Clarify(HelperText.Plain("which"), listOf(blue)),
            HelperReply.Message(HelperText.Plain("hi")),
            HelperReply.Shortcut(ShortcutAction.TAKE),
            HelperReply.Unknown,
            HelperReply.DidYouMean(HelperText.Plain("blue"), blue),
            HelperReply.Greeting,
            HelperReply.Commands,
            HelperReply.Touring(tour, 1),
        )
        shown.forEach { assertTrue(it.summary(null) != null, "$it") }
        assertNull(HelperReply.Idle.summary(null))
        assertNull(HelperReply.DisplaySetup.summary(null))
    }

    @Test
    fun `the conversation keeps only its last lines`() {
        val thread = HelperThread()
        repeat(80) { thread.said("line $it") }
        assertEquals(60, thread.entries.size)
        assertEquals(ThreadEntry.Operator("line 79"), thread.entries.last())
        thread.keep(HelperText.Plain("tip"))
        assertIs<ThreadEntry.Wick>(thread.entries.last())
    }

    @Test
    fun `use is counted when Wick carries something out, never for talk or a refusal`() {
        var used = 0
        val state = HelperState(onUsed = { used++ })
        state.onResolved(Resolution.Unknown, done)
        state.request(HelperAction.Greet, done)
        state.request(HelperAction.ShowCommands, done)
        state.request(HelperAction.Thanks, done)
        state.request(blue, done)
        assertEquals(0, used, "opening, talking and an unanswered confirmation are not use")
        val refused = HelperActionExecutor { ActionOutcome.Refused(HelperText.Plain("no")) }
        state.run(blue, refused)
        assertEquals(0, used)
        state.run(blue, done)
        assertEquals(1, used)
        state.run(HelperAction.Highlight(tour), done)
        assertEquals(2, used)
        state.run(HelperAction.ShowShortcut(ShortcutAction.TAKE), done)
        assertEquals(3, used)
        state.run(HelperAction.StartDisplaySetup, HelperActionExecutor { ActionOutcome.DisplaySetup })
        assertEquals(4, used)
        state.run(HelperAction.SelectTab(Tabs.SONGS), HelperActionExecutor { ActionOutcome.Guide(tour) })
        assertEquals(5, used)
    }

    @Test
    fun `the send-this-chat card goes away when the bubble closes or the chat is cleared`() {
        val state = HelperState()
        state.sharingChat = true
        state.close()
        assertEquals(false, state.sharingChat)
        state.sharingChat = true
        state.clear()
        assertEquals(false, state.sharingChat)
    }
}

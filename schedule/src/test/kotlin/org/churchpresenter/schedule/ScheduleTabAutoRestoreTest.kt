@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The crash-recovery prompt: the first thing the operator sees after the app comes back up having
 * died mid-service.
 *
 * Only one of its two buttons keeps the work — the other deletes the autosave outright — so which
 * one is wired to which is not a detail. The prompt is also the last chance to recover: dismissing
 * it and reopening the panel does not bring it back, by design.
 *
 * `ScheduleAutoSaveTest` covers what the view model does with the file; these tests cover the
 * dialog in front of it. See `ScheduleTabTestSupport.kt` for the harness.
 */
class ScheduleTabAutoRestoreTest {

    private val title = "Restore unsaved schedule?"

    @Test
    fun `no autosave means no prompt`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            onNodeWithText(title).assertDoesNotExist()
        }

    @Test
    fun `an autosave from this session is offered on open`() =
        scheduleTab(seed = { plantAutoSave("Amazing Grace") }) { _, _ ->
            onNodeWithText(title).assertExists("a recovered service must be offered, not silently dropped")
        }

    @Test
    fun `restoring loads the autosaved service and consumes the file`() =
        scheduleTab(seed = { plantAutoSave("Amazing Grace", "Be Thou My Vision") }) { vm, _ ->
            onNodeWithText("Restore").performClick()
            waitForIdle()

            assertEquals(
                listOf("1 - Amazing Grace", "2 - Be Thou My Vision"),
                vm.scheduleItems.map { it.displayText },
                "restore must put the recovered service back",
            )
            onNodeWithText(title).assertDoesNotExist()
            assertFalse(autoSaveExists(), "a restored autosave is spent; leaving it would re-offer stale work")
            assertEquals(
                listOf("Amazing Grace", "Be Thou My Vision"),
                orderOf("Amazing Grace", "Be Thou My Vision"),
                "and the list must redraw with it",
            )
        }

    @Test
    fun `discarding throws the autosave away and leaves the schedule alone`() =
        scheduleTab(
            seed = {
                seedService()
                plantAutoSave("Amazing Grace")
            },
        ) { vm, _ ->
            onNodeWithText("Discard").performClick()
            waitForIdle()

            assertEquals(
                listOf("Welcome", "42 - Amazing Grace", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
                "discarding must not touch what is already loaded",
            )
            onNodeWithText(title).assertDoesNotExist()
            assertFalse(autoSaveExists(), "discard means the file is gone, not merely hidden")
        }

    @Test
    fun `the prompt is offered once and not again on the next composition`() =
        scheduleTab(seed = { plantAutoSave("Amazing Grace") }) { vm, _ ->
            onNodeWithText("Discard").performClick()
            waitForIdle()

            // Collapsing and re-expanding the schedule panel remounts the tab against the same view
            // model; re-prompting there would nag on every toggle.
            assertFalse(vm.shouldPromptAutoRestore(), "the offer is made once per session")
            assertTrue(vm.scheduleItems.isEmpty())
        }
}

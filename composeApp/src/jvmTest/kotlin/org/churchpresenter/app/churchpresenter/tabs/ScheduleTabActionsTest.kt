@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.showsExactly

/**
 * The buttons: the toolbar across the top and the per-row actions.
 *
 * These are the controls an operator uses live, so each test drives the real button and asserts the
 * schedule that results — a button wired to the wrong view-model call, or to the wrong row, is the
 * kind of mistake that only shows up in front of a congregation.
 *
 * See `ScheduleTabTestSupport.kt` for the harness.
 */
class ScheduleTabActionsTest {

    // ── Reordering ──────────────────────────────────────────────────────────────

    @Test
    fun `moving an item down swaps it with the one below`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            // The song is second; sending it down puts the verse second instead.
            buttonAt(ScheduleLabel.MOVE_DOWN, 1).performClick()
            waitForIdle()

            assertEquals(
                listOf("Welcome", "John 3:16", "42 - Amazing Grace", "Notices"),
                vm.scheduleItems.map { it.displayText },
            )
            assertEquals(
                listOf("Welcome", "John 3:16", "Amazing Grace", "Notices"),
                orderOf("Welcome", "John 3:16", "Amazing Grace", "Notices"),
                "and the list redraws in the new order",
            )
        }

    @Test
    fun `moving an item up swaps it with the one above`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            buttonAt(ScheduleLabel.MOVE_UP, 3).performClick()
            waitForIdle()

            assertEquals(
                listOf("Welcome", "42 - Amazing Grace", "Notices", "John 3:16"),
                vm.scheduleItems.map { it.displayText },
            )
        }

    @Test
    fun `the first item cannot be moved off the top, nor the last off the bottom`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            val before = vm.scheduleItems.map { it.displayText }

            buttonAt(ScheduleLabel.MOVE_UP, 0).performClick()
            buttonAt(ScheduleLabel.MOVE_DOWN, 3).performClick()
            waitForIdle()

            assertEquals(before, vm.scheduleItems.map { it.displayText }, "the order is untouched")
        }

    // ── Removing ────────────────────────────────────────────────────────────────

    @Test
    fun `a row's remove button removes that row and no other`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            buttonAt(ScheduleLabel.REMOVE, 1).performClick()
            waitForIdle()

            assertEquals(
                listOf("Welcome", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
            )
        }

    @Test
    fun `remove-selected takes the selected row`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            // Driven through the action set rather than a toolbar button: the toolbar no longer
            // carries a remove, because it did nothing until a row had been clicked and said so
            // nowhere. The menu item and the Delete shortcut still land here.
            onNodeWithText("Notices").performClick()
            waitForIdle()
            registeredActions(reports).removeSelected()
            waitForIdle()

            assertEquals(
                listOf("Welcome", "42 - Amazing Grace", "John 3:16"),
                vm.scheduleItems.map { it.displayText },
            )
        }

    @Test
    fun `clicking a label row selects it, so remove-selected can clear it too`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            // "Welcome" is the label seedService() adds first. A label row used to skip its click
            // handler entirely (nothing to select or present), which also meant it could never
            // become the toolbar's selected row — this is the fix for that.
            onNodeWithText("Welcome").performClick()
            waitForIdle()
            assertEquals(
                vm.scheduleItems.first().id,
                vm.selectedItemId,
                "clicking a label row must select it, the same as any other row",
            )

            registeredActions(reports).removeSelected()
            waitForIdle()

            assertEquals(
                listOf("42 - Amazing Grace", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
                "a selected label must be removable from the menu, not just its own row button",
            )
        }

    @Test
    fun `remove-selected does nothing when nothing is selected`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            registeredActions(reports).removeSelected()
            waitForIdle()

            assertEquals(4, vm.scheduleItems.size, "no row is removed at random")
        }

    @Test
    fun `clearing empties the whole schedule`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            button(ScheduleLabel.CLEAR).performClick()
            waitForIdle()

            assertTrue(vm.scheduleItems.isEmpty())
            assertTrue(showsExactly(ScheduleLabel.DROP_HINT), "and the empty state comes back")
        }

    @Test
    fun `starting a new schedule empties it too`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            button(ScheduleLabel.NEW).performClick()
            waitForIdle()

            assertTrue(vm.scheduleItems.isEmpty())
        }

    // ── Undo and redo ───────────────────────────────────────────────────────────

    /**
     * The tooltip names the key that is actually bound.
     *
     * It shipped reading the literal `Undo (%s)`: the string used a bare `%s`, which Compose
     * resources leave unsubstituted rather than failing. Asserted by *absence of a placeholder*
     * plus presence of the key, because the rendered modifier differs by platform (`⌃Z` on macOS,
     * `Ctrl+Z` elsewhere) and pinning the whole string would only pass on one of them.
     */
    @Test
    fun `the undo and redo tooltips name the bound key`() =
        scheduleTab(seed = { seedService() }) { _, _ ->
            listOf(ScheduleLabel.UNDO, ScheduleLabel.REDO).forEach { tag ->
                val tooltip = taggedButton(tag).fetchSemanticsNode()
                    .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("")

                assertFalse("%" in tooltip, "an unsubstituted placeholder is showing: '$tooltip'")
                assertTrue("Z" in tooltip, "the bound key should be named: '$tooltip'")
                assertTrue("(" in tooltip && ")" in tooltip, "expected the key in parentheses: '$tooltip'")
            }
        }

    @Test
    fun `undo puts back a row removed by mistake`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            buttonAt(ScheduleLabel.REMOVE, 1).performClick()
            waitForIdle()
            taggedButton(ScheduleLabel.UNDO).performClick()
            waitForIdle()

            assertEquals(
                listOf("Welcome", "42 - Amazing Grace", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
                "the song is back where it was",
            )
        }

    @Test
    fun `redo takes it away again`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            buttonAt(ScheduleLabel.REMOVE, 1).performClick()
            waitForIdle()
            taggedButton(ScheduleLabel.UNDO).performClick()
            waitForIdle()
            taggedButton(ScheduleLabel.REDO).performClick()
            waitForIdle()

            assertEquals(
                listOf("Welcome", "John 3:16", "Notices"),
                vm.scheduleItems.map { it.displayText },
            )
        }

    // ── Going live ──────────────────────────────────────────────────────────────

    @Test
    fun `a row's go-live hands the host that item`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            // Go Live is only on presentable rows, so index 0 is the song, not the label.
            buttonAt(ScheduleLabel.GO_LIVE, 0).performClick()
            waitForIdle()

            assertEquals(listOf(vm.scheduleItems[1]), reports.presented, "the song went live")
        }

    @Test
    fun `each row goes live through its own kind of handler`() =
        scheduleTab(seed = { seedService() }) { _, reports ->
            buttonAt(ScheduleLabel.GO_LIVE, 1).performClick()   // the verse
            waitForIdle()
            buttonAt(ScheduleLabel.GO_LIVE, 2).performClick()   // the website
            waitForIdle()

            assertTrue(
                reports.presented[0] is ScheduleItem.BibleVerseItem &&
                    reports.presented[1] is ScheduleItem.WebsiteItem,
                "each item reached the handler for its own type: ${reports.presented}",
            )
        }

    @Test
    fun `with no handler for a type the tab just switches the output to it`() =
        // A host that does not take pictures itself still gets the output switched, so the operator
        // is not left looking at the previous content with nothing having visibly happened.
        scheduleTab(
            seed = { addPicture(folderPath = "/tmp/slides", folderName = "Slides", imageCount = 3) }
        ) { _, reports ->
            buttonAt(ScheduleLabel.GO_LIVE, 0).performClick()
            waitForIdle()

            assertEquals(listOf(Presenting.PICTURES), reports.presenting)
            assertTrue(reports.presented.isEmpty(), "no handler was given one to call")
        }

    @Test
    fun `editing a label asks the host to open the label editor for that label`() =
        scheduleTab(seed = { seedService() }) { vm, reports ->
            button(ScheduleLabel.EDIT_LABEL).performClick()
            waitForIdle()

            assertEquals(listOf(vm.scheduleItems[0]), reports.editedLabels)
        }

    @Test
    fun `adding a label is the host's job, not the tab's`() =
        scheduleTab { vm, reports ->
            button(ScheduleLabel.ADD_LABEL).performClick()
            waitForIdle()

            // The tab cannot add one itself — the colour/text dialog lives above it.
            assertEquals(1, reports.addLabelRequests)
            assertTrue(vm.scheduleItems.isEmpty(), "and nothing is added until that dialog returns")
        }

    // ── Notes ───────────────────────────────────────────────────────────────────

    @Test
    fun `a note written on a row is kept against that row`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            buttonAt(ScheduleLabel.NOTE, 1).performClick()
            waitForIdle()

            noteField().performTextReplacement("Capo 3, two verses only")
            // Typing alone is not a commit — the note is kept when the tick is pressed, so a
            // half-typed thought does not overwrite what was there. (No waitForIdle while the
            // field is open: its cursor blinks forever, so the composition never goes idle.)
            button(ScheduleLabel.NOTE_SAVE).performClick()

            val song = vm.scheduleItems[1]
            assertEquals("Capo 3, two verses only", vm.getNote(song.id))
            assertEquals("", vm.getNote(vm.scheduleItems[2].id), "and not against its neighbour")
        }

    @Test
    fun `a note is not kept until it is saved`() =
        scheduleTab(seed = { seedService() }) { vm, _ ->
            buttonAt(ScheduleLabel.NOTE, 1).performClick()
            waitForIdle()
            noteField().performTextReplacement("half a thought")

            // Clearing writes an empty note through the same callback the tick uses, so it is the
            // positive signal that a commit has happened — had typing alone committed, the text
            // above would have been written before this one.
            button(ScheduleLabel.NOTE_CLEAR).performClick()
            waitForIdle()

            assertEquals("", vm.getNote(vm.scheduleItems[1].id))
        }

    // ── Zoom ────────────────────────────────────────────────────────────────────

    @Test
    fun `zooming asks the host to store the new size`() =
        scheduleTab(itemZoomPercent = 100, seed = { seedService() }) { _, reports ->
            button(ScheduleLabel.ZOOM_IN).performClick()
            waitForIdle()

            // The tab holds no zoom state of its own — it is passed in and reported back out, so a
            // rung of the ladder is the whole observable effect.
            assertEquals(1, reports.zoomChanges.size)
            assertTrue(
                reports.zoomChanges.single() > 100,
                "zooming in asks for a larger size: ${reports.zoomChanges}",
            )
        }

    @Test
    fun `zooming out asks for a smaller size`() =
        scheduleTab(itemZoomPercent = 100, seed = { seedService() }) { _, reports ->
            button(ScheduleLabel.ZOOM_OUT).performClick()
            waitForIdle()

            assertTrue(
                reports.zoomChanges.single() < 100,
                "got ${reports.zoomChanges}",
            )
        }
}

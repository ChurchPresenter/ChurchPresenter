@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The multi-translation display-order panel — the dropdown behind the translation chip, which is the
 * only place the stacking order of three or more translations can be changed.
 *
 * The order is not cosmetic: the first translation is the *navigation* bible (the one whose book and
 * chapter list drives the tab) and the rest stack down the audience screen beneath it. Reordering
 * with the wrong index or a wrong offset silently repoints the whole tab at a different translation.
 *
 * Every row offers two ways to move: the up/down arrows, and dragging the row by its grip. Both end
 * at the same `moveBibleTranslation`, and both are covered here — the drag because it is the one an
 * operator reaches for, the arrows because they are the accessible route and because their disabled
 * state at the ends of the list is its own branch.
 *
 * The panel lives inside a `DropdownMenu`, so every test opens the chip first. Note that clicking a
 * row inside the menu does **not** close it, so an assertion after a click is still looking at the
 * open panel.
 *
 * See `BibleTabTestSupport.kt` for the harness.
 */
class BibleTabTranslationOrderTest {

    /** Three translations: the minimum that turns the one-tap swap into an ordered list. */
    private fun stack(vararg fileNames: String): (AppSettings) -> AppSettings =
        { app ->
            app.copy(
                bibleSettings = app.bibleSettings.copy(
                    translations = fileNames.map { BibleTranslationSettings(fileName = it) },
                )
            )
        }

    private val three = stack("test.spb", "second.spb", "third.spb")

    /**
     * Opens the chip's dropdown, which is where the whole panel lives.
     *
     * Addressed by the chip's own caption rather than by the translation it names: the name shown
     * is the display name read out of the `.spb`, not the file name, so keying on it would tie the
     * test to the fixture's metadata.
     */
    private fun ComposeUiTest.openOrderPanel() {
        onNodeWithText("TRANSLATION ORDER").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.panelIsOpen(): Boolean =
        onAllNodesWithContentDescription("Drag to reorder")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    /** The resulting translation order after the tab's most recent settings change. */
    private fun BibleReports.orderAfterChange(): List<String>? =
        settingsAfterChange?.bibleSettings?.translations?.map { it.fileName }

    // ── The panel's contents ────────────────────────────────────────────────────

    @Test
    fun `the chip opens a panel listing every translation in order`() =
        bibleTab(settings = three) { _, _ ->
            openOrderPanel()

            assertTrue(panelIsOpen(), "the chip must open the order panel")
            onNodeWithText("Display order").assertExists("the panel must say what it orders")
            onNodeWithText("Verses stack top to bottom on screen").assertExists()
            // Each translation is listed by the file it comes from, which is what makes two
            // translations with the same display name tellable apart.
            onNodeWithText("test.spb").assertExists()
            onNodeWithText("second.spb").assertExists()
            onNodeWithText("third.spb").assertExists()
        }

    @Test
    fun `every row has a drag grip and a pair of arrows`() =
        bibleTab(settings = three) { _, _ ->
            openOrderPanel()

            assertEquals(3, countByDescription("Drag to reorder"), "one grip per translation")
            assertEquals(3, countByDescription("Move translation up"))
            assertEquals(3, countByDescription("Move translation down"))
        }

    @Test
    fun `the panel explains both ways to reorder`() =
        bibleTab(settings = three) { _, _ ->
            openOrderPanel()

            onNodeWithText("Drag a row or use the arrows to reorder.").assertExists()
        }

    // ── The arrows ──────────────────────────────────────────────────────────────

    @Test
    fun `the down arrow on the first row moves it below the second`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()

            onAllNodesWithContentDescription("Move translation down")[0].performClick()
            waitForIdle()

            assertEquals(
                listOf("second.spb", "test.spb", "third.spb"),
                reports.orderAfterChange(),
                "the navigation bible must hand over to the one it swapped with",
            )
        }

    @Test
    fun `the up arrow on the last row moves it above the second`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()

            onAllNodesWithContentDescription("Move translation up")[2].performClick()
            waitForIdle()

            assertEquals(
                listOf("test.spb", "third.spb", "second.spb"),
                reports.orderAfterChange(),
            )
        }

    @Test
    fun `the up arrow on the first row does nothing`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()
            // The grips prove the panel is open, so a later "nothing happened" is about the arrow
            // and not about the panel having failed to render.
            assertTrue(panelIsOpen())

            onAllNodesWithContentDescription("Move translation up")[0].performClick()
            waitForIdle()

            // Disabled here omits the clickable modifier entirely rather than reporting itself
            // disabled, so "cannot be pressed" is the absence of any effect.
            assertEquals(0, reports.settingsChanges, "there is nothing above the first row")
        }

    @Test
    fun `the down arrow on the last row does nothing`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()
            assertTrue(panelIsOpen())

            onAllNodesWithContentDescription("Move translation down")[2].performClick()
            waitForIdle()

            assertEquals(0, reports.settingsChanges, "there is nothing below the last row")
        }

    @Test
    fun `the arrows in the middle row move it either way`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()

            onAllNodesWithContentDescription("Move translation up")[1].performClick()
            waitForIdle()
            assertEquals(listOf("second.spb", "test.spb", "third.spb"), reports.orderAfterChange())

            // The harness now feeds each change back, as the host does, so the second click starts
            // from the order the first one produced. Row 1 is "test.spb" by this point, and moving
            // it down puts it last.
            onAllNodesWithContentDescription("Move translation down")[1].performClick()
            waitForIdle()
            assertEquals(listOf("second.spb", "third.spb", "test.spb"), reports.orderAfterChange())
        }

    // ── The drag grip ───────────────────────────────────────────────────────────

    @Test
    fun `dragging a row down past the next one reorders it`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()

            // The reorder commits once on drop, by whole row heights travelled — dragging a full
            // row's height down moves it exactly one place.
            dragGrip(row = 0, dy = ROW_HEIGHT_PX)

            assertEquals(
                listOf("second.spb", "test.spb", "third.spb"),
                reports.orderAfterChange(),
                "a one-row drag moves one place",
            )
        }

    @Test
    fun `dragging a row up past the previous one reorders it`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()

            dragGrip(row = 2, dy = -ROW_HEIGHT_PX)

            assertEquals(listOf("test.spb", "third.spb", "second.spb"), reports.orderAfterChange())
        }

    @Test
    fun `a drag too short to cross a row leaves the order alone`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()

            // Under half a row rounds to zero steps: a nudge while reading the list must not
            // silently repoint the navigation bible.
            dragGrip(row = 0, dy = ROW_HEIGHT_PX / 4f)

            assertEquals(0, reports.settingsChanges, "a nudge is not a reorder")
        }

    @Test
    fun `dragging far past the end stops at the last position`() =
        bibleTab(settings = three) { _, reports ->
            openOrderPanel()

            dragGrip(row = 0, dy = ROW_HEIGHT_PX * 10f)

            // Clamped to the list rather than throwing or dropping the translation.
            assertEquals(
                listOf("second.spb", "third.spb", "test.spb"),
                reports.orderAfterChange(),
                "the row lands at the bottom, not off the end",
            )
        }

    private companion object {
        /** `TRANSLATION_ORDER_ROW_HEIGHT` in pixels at the tests' density of 1. */
        const val ROW_HEIGHT_PX = 46f
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun ComposeUiTest.countByDescription(description: String): Int =
        onAllNodesWithContentDescription(description)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .size

    /**
     * Drags the [row]th grip vertically by [dy] pixels and drops it.
     *
     * The press is nudged before the real move for the same reason as the scene canvas's drag
     * helper: `detectDragGestures` only reports movement once touch slop is passed.
     */
    private fun ComposeUiTest.dragGrip(row: Int, dy: Float) {
        onAllNodesWithContentDescription("Drag to reorder")[row].performMouseInput {
            moveTo(Offset(2f, 8f))
            press()
            moveTo(Offset(2f, 8f + dy / 2f))
            moveTo(Offset(2f, 8f + dy))
            release()
        }
        waitForIdle()
    }
}

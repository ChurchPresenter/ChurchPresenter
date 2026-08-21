@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.CompanionSatelliteSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the three placement blocks — Tab, Left sidebar, Right sidebar — that decide where a
 * Companion surface is drawn and how big its grid is.
 *
 * The three are the same composable with different callbacks, and each writes into its own trio of
 * `…Rows` / `…Columns` / `…BitmapSize` fields plus a max-button size. Their captions are identical,
 * so a block that wrote into a neighbour's fields would look completely correct on screen — which is
 * exactly what these tests are for. Every one asserts all three blocks' values, not just the one it
 * touched.
 */
class CompanionSatelliteSettingsTabPlacementTest {

    /**
     * Every placement dimension distinct, so a cross-wired write is unmistakable — and all three
     * placements switched on, because a block hides its four boxes until it is.
     */
    private fun distinctGrid() = connection {
        copy(
            showInTab = true, showInLeftSidebar = true, showInRightSidebar = true,
            tabRows = 11, tabColumns = 12, tabBitmapSize = 13, tabMaxButtonSizeDp = 14,
            leftSidebarRows = 21, leftSidebarColumns = 22, leftSidebarBitmapSize = 23,
            rightSidebarRows = 31, rightSidebarColumns = 32, rightSidebarBitmapSize = 33,
        )
    }

    private fun rowsOf(c: CompanionSatelliteSettings, p: Placement) = when (p) {
        Placement.TAB -> c.tabRows
        Placement.LEFT -> c.leftSidebarRows
        Placement.RIGHT -> c.rightSidebarRows
    }

    private fun columnsOf(c: CompanionSatelliteSettings, p: Placement) = when (p) {
        Placement.TAB -> c.tabColumns
        Placement.LEFT -> c.leftSidebarColumns
        Placement.RIGHT -> c.rightSidebarColumns
    }

    private fun bitmapOf(c: CompanionSatelliteSettings, p: Placement) = when (p) {
        Placement.TAB -> c.tabBitmapSize
        Placement.LEFT -> c.leftSidebarBitmapSize
        Placement.RIGHT -> c.rightSidebarBitmapSize
    }

    private fun shownOf(c: CompanionSatelliteSettings, p: Placement) = when (p) {
        Placement.TAB -> c.showInTab
        Placement.LEFT -> c.showInLeftSidebar
        Placement.RIGHT -> c.showInRightSidebar
    }

    // ── Structure ───────────────────────────────────────────────────────────────────────────────

    /**
     * A placement's four boxes only exist once it is ticked, so an untouched card shows three
     * captions and three checkboxes and nothing else — the grid is revealed, not merely disabled.
     */
    @Test
    fun `a card offers three placements whose boxes appear only when ticked`() {
        satelliteTab { _ ->
            for (placement in Placement.entries) {
                onAllNodesWithText(placement.caption).assertCountEquals(1)
            }
            for (caption in listOf(SatLabel.ROWS, SatLabel.COLUMNS, SatLabel.BITMAP_SIZE, SatLabel.MAX_BUTTON_SIZE)) {
                onAllNodesWithText(caption).assertCountEquals(0)
            }
            satelliteToggles().assertCountEquals(Placement.entries.size + 1)
        }
        satelliteTab(initial = satelliteSettings(distinctGrid())) { _ ->
            for (caption in listOf(SatLabel.ROWS, SatLabel.COLUMNS, SatLabel.BITMAP_SIZE, SatLabel.MAX_BUTTON_SIZE)) {
                onAllNodesWithText(caption).assertCountEquals(Placement.entries.size)
            }
        }
    }

    /** Ticking one placement reveals exactly that block's four boxes. */
    @Test
    fun `ticking a placement reveals its four boxes`() = satelliteTab { _ ->
        onAllNodesWithText(SatLabel.ROWS).assertCountEquals(0)

        placementCheckbox(Placement.LEFT).performScrollTo().performClick()
        waitForIdle()

        onAllNodesWithText(SatLabel.ROWS).assertCountEquals(1)
        onAllNodesWithText(SatLabel.MAX_BUTTON_SIZE).assertCountEquals(1)
    }

    // ── The checkboxes ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `each placement checkbox sets only its own flag`() {
        for (placement in Placement.entries) {
            satelliteTab { get ->
                placementCheckbox(placement).performScrollTo().assertIsOff()

                placementCheckbox(placement).performClick()
                waitForIdle()

                val c = get().onlyConnection()
                assertEquals(true, shownOf(c, placement), "$placement must be switched on")
                for (other in Placement.entries.filter { it != placement }) {
                    assertEquals(false, shownOf(c, other), "$other must be untouched by $placement")
                }
                placementCheckbox(placement).assertIsOn()
            }
        }
    }

    @Test
    fun `a placement checkbox turns off again`() {
        satelliteTab(initial = satelliteSettings(connection { copy(showInTab = true) })) { get ->
            placementCheckbox(Placement.TAB).performScrollTo().assertIsOn()

            placementCheckbox(Placement.TAB).performClick()
            waitForIdle()

            assertEquals(false, get().onlyConnection().showInTab, "switching off must be stored")
            placementCheckbox(Placement.TAB).assertIsOff()
        }
    }

    // ── The grid boxes ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `each placement's rows box writes only its own rows`() {
        for (placement in Placement.entries) {
            satelliteTab(initial = satelliteSettings(distinctGrid())) { get ->
                typeIntoPlacement(placement, SatLabel.ROWS, "7")

                val c = get().onlyConnection()
                assertEquals(7, rowsOf(c, placement), "$placement rows must take the typed value")
                for (other in Placement.entries.filter { it != placement }) {
                    assertEquals(
                        rowsOf(distinctGrid(), other),
                        rowsOf(c, other),
                        "$other rows must be untouched by $placement",
                    )
                }
                assertEquals(
                    columnsOf(distinctGrid(), placement),
                    columnsOf(c, placement),
                    "$placement columns must be untouched by its own rows box",
                )
            }
        }
    }

    @Test
    fun `each placement's columns box writes only its own columns`() {
        for (placement in Placement.entries) {
            satelliteTab(initial = satelliteSettings(distinctGrid())) { get ->
                typeIntoPlacement(placement, SatLabel.COLUMNS, "9")

                val c = get().onlyConnection()
                assertEquals(9, columnsOf(c, placement), "$placement columns must take the typed value")
                for (other in Placement.entries.filter { it != placement }) {
                    assertEquals(columnsOf(distinctGrid(), other), columnsOf(c, other), "$other must be untouched")
                }
            }
        }
    }

    @Test
    fun `each placement's bitmap size writes only its own`() {
        for (placement in Placement.entries) {
            satelliteTab(initial = satelliteSettings(distinctGrid())) { get ->
                typeIntoPlacement(placement, SatLabel.BITMAP_SIZE, "96")

                val c = get().onlyConnection()
                assertEquals(96, bitmapOf(c, placement), "$placement bitmap size must take the typed value")
                for (other in Placement.entries.filter { it != placement }) {
                    assertEquals(bitmapOf(distinctGrid(), other), bitmapOf(c, other), "$other must be untouched")
                }
            }
        }
    }

    /**
     * Rows and columns are floored at one: a grid of zero rows would draw nothing and there would be
     * no way back from it through the UI, so the callback coerces rather than storing what was typed.
     */
    @Test
    fun `rows and columns are floored at one`() {
        satelliteTab(initial = satelliteSettings(distinctGrid())) { get ->
            typeIntoPlacement(Placement.TAB, SatLabel.ROWS, "0")
            assertEquals(1, get().onlyConnection().tabRows, "zero rows must be raised to one")

            typeIntoPlacement(Placement.TAB, SatLabel.COLUMNS, "0")
            assertEquals(1, get().onlyConnection().tabColumns, "zero columns must be raised to one")
        }
    }

    @Test
    fun `a grid box that will not parse leaves the stored value alone`() {
        satelliteTab(initial = satelliteSettings(distinctGrid())) { get ->
            typeIntoPlacement(Placement.LEFT, SatLabel.ROWS, "many")
            assertEquals(21, get().onlyConnection().leftSidebarRows, "nonsense must not be stored")

            typeIntoPlacement(Placement.LEFT, SatLabel.ROWS, "6")
            assertEquals(6, get().onlyConnection().leftSidebarRows, "a real number must still land")
        }
    }

    /** Zero is a legal max button size — it means "unlimited", which the hint says outright. */
    @Test
    fun `a max button size of zero is stored as unlimited`() {
        satelliteTab(initial = satelliteSettings(distinctGrid())) { get ->
            typeIntoPlacement(Placement.TAB, SatLabel.MAX_BUTTON_SIZE, "0")
            assertEquals(
                0,
                get().onlyConnection().tabMaxButtonSizeDp,
                "zero must be stored rather than floored — it is what unlimited looks like",
            )
        }
    }

    /**
     * Each placement caps its button size separately. The three callbacks are written out one after
     * another rather than shared, so this is three different pieces of code that happen to look
     * identical — the shape a copy-paste slip hides in.
     */
    @Test
    fun `each placement's max button size writes only its own`() {
        val maxOf = { c: CompanionSatelliteSettings, p: Placement ->
            when (p) {
                Placement.TAB -> c.tabMaxButtonSizeDp
                Placement.LEFT -> c.leftSidebarMaxButtonSizeDp
                Placement.RIGHT -> c.rightSidebarMaxButtonSizeDp
            }
        }
        for (placement in Placement.entries) {
            satelliteTab(initial = satelliteSettings(distinctGrid())) { get ->
                typeIntoPlacement(placement, SatLabel.MAX_BUTTON_SIZE, "64")

                val c = get().onlyConnection()
                assertEquals(64, maxOf(c, placement), "$placement max size must take the typed value")
                for (other in Placement.entries.filter { it != placement }) {
                    assertEquals(
                        maxOf(distinctGrid(), other),
                        maxOf(c, other),
                        "$other max size must be untouched by $placement",
                    )
                }
            }
        }
    }

    @Test
    fun `the stored grid is rendered back into its boxes`() {
        satelliteTab(initial = satelliteSettings(distinctGrid())) { _ ->
            for (value in listOf("11", "12", "13", "21", "22", "23", "31", "32", "33")) {
                assertSatelliteBoxShows(value, "a grid box")
            }
        }
    }
}

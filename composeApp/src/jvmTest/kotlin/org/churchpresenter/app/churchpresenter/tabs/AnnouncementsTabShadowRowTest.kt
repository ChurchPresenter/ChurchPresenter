@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.input.ImeAction
import org.churchpresenter.settings.AnnouncementsSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shadow row the Announcements toolbar unfolds. Its three handlers had never run, because no
 * test had composed the tab with the shadow already on.
 */
class AnnouncementsTabShadowRowTest {

    private fun shadowed(
        color: String = "#112233",
        size: Int = 140,
        opacity: Int = 63,
    ) = AnnouncementsSettings(shadow = true, shadowColor = color, shadowSize = size, shadowOpacity = opacity)

    /** No `performScrollTo`: the toolbar's shadow row has no scrollable ancestor to scroll within. */
    private fun ComposeUiTest.retype(showing: Int, to: Int) {
        onAllNodes(hasSetTextAction() and hasImeAction(ImeAction.Default) and hasText(showing.toString()))[0]
            .performTextReplacement(to.toString())
        waitForIdle()
    }

    // ── When the row is there at all ──────────────────────────────────────────

    @Test
    fun `the row is folded away while the shadow is off`() {
        announcementsTab(AnnouncementsSettings(shadow = false)) { _, _ ->
            onNodeWithText("SIZE (%)").assertDoesNotExist()
            onNodeWithText("INTENSITY (%)").assertDoesNotExist()
        }
    }

    @Test
    fun `the row is on screen while the shadow is on`() {
        announcementsTab(shadowed()) { _, _ ->
            onNodeWithText("SIZE (%)").assertExists()
            onNodeWithText("INTENSITY (%)").assertExists()
            onNodeWithText("#112233").assertExists("and the shadow's own color field")
        }
    }

    @Test
    fun `each field shows what is stored`() {
        announcementsTab(shadowed(size = 140, opacity = 63)) { _, _ ->
            onNodeWithText("140").assertExists()
            onNodeWithText("63").assertExists()
        }
    }

    // ── What each field writes ────────────────────────────────────────────────

    @Test
    fun `retyping the size writes the shadow size`() {
        announcementsTab(shadowed()) { _, reports ->
            retype(140, 300)
            assertEquals(300, reports.settings?.shadowSize)
        }
    }

    @Test
    fun `retyping the intensity writes the shadow opacity`() {
        announcementsTab(shadowed()) { _, reports ->
            retype(63, 25)
            assertEquals(25, reports.settings?.shadowOpacity)
        }
    }

    @Test
    fun `the size field writes nothing but the size`() {
        announcementsTab(shadowed()) { _, reports ->
            retype(140, 300)
            val stored = reports.settings
            assertEquals(63, stored?.shadowOpacity, "the field beside it must not move")
            assertEquals("#112233", stored?.shadowColor)
        }
    }

    @Test
    fun `a size below the range is not written`() {
        announcementsTab(shadowed()) { _, reports ->
            retype(140, 5)
            assertEquals(null, reports.settings?.shadowSize, "nothing may have been stored at all")
        }
    }

    @Test
    fun `a size above the range is not written`() {
        announcementsTab(shadowed()) { _, reports ->
            retype(140, 900)
            assertEquals(null, reports.settings?.shadowSize)
        }
    }

    @Test
    fun `an intensity above 100 is not written`() {
        announcementsTab(shadowed()) { _, reports ->
            retype(63, 140)
            assertEquals(null, reports.settings?.shadowOpacity)
        }
    }

    @Test
    fun `the two fields are independent`() {
        announcementsTab(shadowed()) { _, reports ->
            retype(140, 300)
            retype(63, 25)
            assertEquals(300, reports.settings?.shadowSize)
            assertEquals(25, reports.settings?.shadowOpacity)
        }
    }
}

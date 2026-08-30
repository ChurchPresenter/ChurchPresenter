@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.QuickBackground
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The tray under the live preview: a live control that writes nothing.
 *
 * What matters here is that a tile reports the entry it stands for and that "back to normal"
 * reports null — the whole override lives on that one nullable value in `main.kt`.
 */
class QuickBackgroundTrayTest {

    private fun entry(id: String, color: String) = QuickBackground(
        id = id,
        background = SongBackground(type = SongBackgroundType.COLOR, color = color),
        lowerThirdBackground = SongBackground(type = SongBackgroundType.COLOR, color = color),
    )

    private fun tray(
        backgrounds: List<QuickBackground>,
        activeId: String? = null,
        expanded: Boolean = true,
        onPick: (QuickBackground?) -> Unit = {},
        onExpandedChange: (Boolean) -> Unit = {},
        block: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme {
                QuickBackgroundTray(
                    backgrounds = backgrounds,
                    activeId = activeId,
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    onPick = onPick,
                )
            }
        }
        block()
    }

    @Test
    fun `an empty tray renders nothing at all`() = tray(emptyList()) {
        assertEquals(
            0,
            onAllNodesWithText("", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size,
            "an operator with no quick backgrounds has nothing to pick",
        )
    }

    @Test
    fun `a configured tray shows its header and its tiles`() =
        tray(listOf(entry("a", "#112233"), entry("b", "#445566"))) {
            onNodeWithTag(QUICK_BACKGROUND_TRAY_TAG).assertIsDisplayed()
            onNodeWithTag(QUICK_BACKGROUND_HEADER_TAG).assertIsDisplayed()
            onNodeWithText("QUICK BACKGROUNDS").assertIsDisplayed()
        }

    @Test
    fun `picking a tile reports the entry it stands for`() {
        var picked: QuickBackground? = null
        val second = entry("b", "#445566")
        tray(listOf(entry("a", "#112233"), second), onPick = { picked = it }) {
            onNodeWithText("2").performClick()
            waitForIdle()
        }
        assertSame(second, picked, "the tile must hand back its own entry, not a copy of it")
    }

    @Test
    fun `back to normal reports nothing picked`() {
        var picked: QuickBackground? = entry("a", "#112233")
        tray(listOf(entry("a", "#112233")), activeId = "a", onPick = { picked = it }) {
            onNodeWithTag(QUICK_BACKGROUND_RESET_TAG).performClick()
            waitForIdle()
        }
        assertNull(picked, "clearing the override is what puts the configured backgrounds back")
    }

    @Test
    fun `the reset control appears only while something is picked`() {
        tray(listOf(entry("a", "#112233"))) {
            assertEquals(
                0,
                onAllNodesWithTag(QUICK_BACKGROUND_RESET_TAG)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                "with nothing overridden there is nothing to undo",
            )
        }
        tray(listOf(entry("a", "#112233")), activeId = "a") {
            onNodeWithTag(QUICK_BACKGROUND_RESET_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `the header reports the tray being shut and opened`() {
        var open: Boolean? = null
        tray(listOf(entry("a", "#112233")), onExpandedChange = { open = it }) {
            onNodeWithTag(QUICK_BACKGROUND_HEADER_TAG).performClick()
            waitForIdle()
        }
        assertEquals(false, open, "an open tray shuts")

        open = null
        tray(listOf(entry("a", "#112233")), expanded = false, onExpandedChange = { open = it }) {
            onNodeWithTag(QUICK_BACKGROUND_HEADER_TAG).performClick()
            waitForIdle()
        }
        assertEquals(true, open, "and a shut one opens")
    }

    @Test
    fun `a shut tray keeps its header and drops its tiles`() =
        tray(listOf(entry("a", "#112233")), expanded = false) {
            onNodeWithTag(QUICK_BACKGROUND_HEADER_TAG).assertIsDisplayed()
            assertEquals(
                0,
                onAllNodesWithText("1").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                "the tiles go with it",
            )
        }

    @Test
    fun `the tray holds no more than its ten slots`() {
        val many = (1..14).map { entry("e$it", "#00000$it") }
        tray(many) {
            assertEquals(
                0,
                onAllNodesWithText("11").fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                "an eleventh slot has no key to reach it and is not shown",
            )
        }
    }
}

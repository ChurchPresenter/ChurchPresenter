package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.BackgroundContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Set as Background in a context menu: every output and each profile, each with Default, Songs and Bible. */
@OptIn(ExperimentalTestApi::class)
class SetAsBackgroundItemsTest {

    private val targets = listOf(
        BackgroundTarget("main", "Screen 1 + NDI Output 1"),
        BackgroundTarget("stage", "Stage Monitor"),
    )

    private fun items(
        startOpen: Boolean = false,
        isCurrent: (String?, BackgroundContent) -> Boolean = { _, _ -> false },
        block: ComposeUiTest.(picked: List<Pair<String?, BackgroundContent>>) -> Unit,
    ) = runComposeUiTest {
        val picked = mutableListOf<Pair<String?, BackgroundContent>>()
        setContent {
            MaterialTheme {
                Column {
                    SetAsBackgroundItems(targets, startOpen = startOpen, isCurrent = isCurrent) { profileId, content ->
                        picked += profileId to content
                    }
                }
            }
        }
        block(picked)
    }

    @Test
    fun `closed, it is one item`() = items {
        onNodeWithText("Set as Background").assertExists()
        assertEquals(0, onAllNodesWithText("All outputs").fetchSemanticsNodes().size)
    }

    @Test
    fun `pressing it opens every output then each profile`() = items {
        onNodeWithText("Set as Background").performClick()
        waitForIdle()
        onNodeWithText("All outputs").assertExists()
        onNodeWithText("Screen 1 + NDI Output 1").assertExists()
        onNodeWithText("Stage Monitor").assertExists()
        assertEquals(3, onAllNodesWithText("Songs").fetchSemanticsNodes().size)

        onNodeWithText("Set as Background").performClick()
        waitForIdle()
        assertEquals(0, onAllNodesWithText("All outputs").fetchSemanticsNodes().size, "pressed again, it closes")
    }

    @Test
    fun `each button says which outputs and which background`() = items(startOpen = true) { picked ->
        onNodeWithTag(setAsBackgroundTag(null, BackgroundContent.DEFAULT)).performClick()
        onNodeWithTag(setAsBackgroundTag("main", BackgroundContent.SONG)).performClick()
        onNodeWithTag(setAsBackgroundTag("stage", BackgroundContent.BIBLE)).performClick()
        assertEquals(
            listOf(
                null to BackgroundContent.DEFAULT,
                "main" to BackgroundContent.SONG,
                "stage" to BackgroundContent.BIBLE,
            ),
            picked,
        )
    }

    @Test
    fun `the background it is already is marked`() =
        items(startOpen = true, isCurrent = { id, content -> id == "main" && content == BackgroundContent.SONG }) {
            fun selected(tag: String) =
                onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
            assertEquals(true, selected(setAsBackgroundTag("main", BackgroundContent.SONG)))
            assertEquals(false, selected(setAsBackgroundTag("main", BackgroundContent.DEFAULT)))
            assertEquals(false, selected(setAsBackgroundTag(null, BackgroundContent.SONG)))
        }

    @Test
    fun `every button has a name`() = items(startOpen = true) {
        val names = onAllNodesWithText("Default").fetchSemanticsNodes().size +
            onAllNodesWithText("Bible").fetchSemanticsNodes().size
        assertTrue(names == 6, "three targets, each with Default and Bible")
    }
}

@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.model.MessageAction
import org.churchpresenter.hosanna.composables.model.MessageIntent
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineMessagesTest {

    @Test
    fun `a banner shows its text and runs its actions`() = runComposeUiTest {
        val pressed = mutableListOf<String>()
        setContent {
            HosannaSurface {
                Banner(
                    "Start a session so people can send questions.", Modifier.width(640.dp),
                    intent = MessageIntent.Warning, title = "No session running",
                    action = MessageAction("Start session") { pressed += "start" },
                    otherActions = listOf(MessageAction("Learn more") { pressed += "learn" }),
                ) { BasicText("Code 4821") }
            }
        }
        onNodeWithText("No session running").assertExists()
        onNodeWithText("Code 4821").assertExists()
        onNodeWithText("Learn more").performClick()
        onNodeWithText("Start session").performClick()
        waitForIdle()
        assertEquals(listOf("learn", "start"), pressed)
    }

    @Test
    fun `only info, success and neutral banners can be dismissed`() = runComposeUiTest {
        var dismissed = 0
        setContent {
            HosannaSurface {
                Column {
                    MessageIntent.entries.forEach { intent ->
                        Banner(
                            intent.name, Modifier.width(640.dp), intent,
                            dismiss = MessageAction("Dismiss") { dismissed++ },
                        )
                    }
                }
            }
        }
        onAllNodesWithContentDescription("Dismiss").assertCountEquals(3)
        onAllNodesWithContentDescription("Dismiss")[0].performClick()
        waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun `actions sit beside the text when wide and under it when narrow`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    Banner("Wide body", Modifier.width(640.dp), action = MessageAction("Wide action") {})
                    Banner("Narrow body", Modifier.width(360.dp), action = MessageAction("Narrow action") {})
                }
            }
        }
        val wideBody = onNodeWithText("Wide body").getBoundsInRoot()
        val wide = onNodeWithText("Wide action").getBoundsInRoot()
        assertTrue(wide.left > wideBody.right)
        val narrowBody = onNodeWithText("Narrow body").getBoundsInRoot()
        val narrow = onNodeWithText("Narrow action").getBoundsInRoot()
        assertTrue(narrow.top >= narrowBody.bottom)
    }

    @Test
    fun `a hidden banner goes away`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var visible by mutableStateOf(true)
        setContent { HosannaSurface { Banner("Saved", intent = MessageIntent.Success, visible = visible) } }
        settle()
        onNodeWithText("Saved").assertExists()
        visible = false
        settle()
        onNodeWithText("Saved").assertDoesNotExist()
    }

    @Test
    fun `errors interrupt screen readers, everything else waits`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    Banner("Upload failed", intent = MessageIntent.Error)
                    InlineMessage("Check the IP address", intent = MessageIntent.Error)
                    Banner("Update ready", intent = MessageIntent.Info)
                    InlineMessage("Saved", intent = MessageIntent.Success)
                }
            }
        }
        fun mode(mode: LiveRegionMode) = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)
        onAllNodes(mode(LiveRegionMode.Assertive)).assertCountEquals(2)
        onAllNodes(mode(LiveRegionMode.Polite)).assertCountEquals(2)
    }

    @Test
    fun `inline messages and hints show their text`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    InlineMessage("The ATEM did not answer on 10.0.0.40.", intent = MessageIntent.Error)
                    Hint("Language names are set in the Songs tab.")
                }
            }
        }
        onNodeWithText("The ATEM did not answer on 10.0.0.40.").assertExists()
        onNodeWithText("Language names are set in the Songs tab.").assertExists()
        onNodeWithContentDescription("Dismiss").assertDoesNotExist()
    }
}

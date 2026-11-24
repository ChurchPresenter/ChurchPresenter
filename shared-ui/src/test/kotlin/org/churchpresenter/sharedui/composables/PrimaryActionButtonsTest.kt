package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every tab's primary actions: [GoLiveButton] and [AddToScheduleButton], observed through their public API. */
@OptIn(ExperimentalTestApi::class)
class PrimaryActionButtonsTest {

    // ── GoLiveButton ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `GoLiveButton shows the tooltip text as its content description`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { }, tooltipText = "Go live")
            }
        }
        onNodeWithContentDescription("Go live").assertExists()
    }

    @Test
    fun `GoLiveButton invokes onClick when clicked`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { clicked = true }, tooltipText = "Go live")
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked)
    }

    @Test
    fun `GoLiveButton is enabled by default`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { }, tooltipText = "Go live")
            }
        }
        onNode(hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a disabled GoLiveButton reports itself disabled and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { clicked = true }, tooltipText = "Go live", enabled = false)
            }
        }
        val button = onNode(hasClickAction())
        button.assertIsNotEnabled()
        button.performClick()
        assertFalse(clicked)
    }

    @Test
    fun `a dimmed GoLiveButton still renders and can still be clicked`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                GoLiveButton(onClick = { clicked = true }, tooltipText = "Go live", dimmed = true)
            }
        }
        onNodeWithContentDescription("Go live").assertExists()
        onNode(hasClickAction()).performClick()
        assertTrue(clicked, "dimmed = true must not stop the button from working")
    }

    // ── AddToScheduleButton ────────────────────────────────────────────────────────────────────

    @Test
    fun `AddToScheduleButton shows the tooltip text as its content description`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AddToScheduleButton(onClick = { }, tooltipText = "Add to schedule")
            }
        }
        onNodeWithContentDescription("Add to schedule").assertExists()
    }

    @Test
    fun `AddToScheduleButton invokes onClick when clicked`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                AddToScheduleButton(onClick = { clicked = true }, tooltipText = "Add to schedule")
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked)
    }

    @Test
    fun `AddToScheduleButton is enabled by default`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AddToScheduleButton(onClick = { }, tooltipText = "Add to schedule")
            }
        }
        onNode(hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a disabled AddToScheduleButton reports itself disabled and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                AddToScheduleButton(onClick = { clicked = true }, tooltipText = "Add to schedule", enabled = false)
            }
        }
        val button = onNode(hasClickAction())
        button.assertIsNotEnabled()
        button.performClick()
        assertFalse(clicked)
    }
}

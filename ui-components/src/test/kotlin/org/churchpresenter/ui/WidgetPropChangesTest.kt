package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.ic_arrow_left
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Widgets whose internal state is keyed on an input, redrawn after that input changes underneath
 * them.
 *
 * Each holds state that has to be rebuilt when the caller hands it a different value — a number
 * field re-seeded from settings that were reloaded, a resize handle whose panel was collapsed.
 * Keeping the stale state instead is the bug the keying exists to prevent, and nothing was changing
 * the key.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetPropChangesTest {

    @Test
    fun `a number field re-seeds when the caller hands it a different starting value`() = runComposeUiTest {
        var reported = 0
        setContent {
            MaterialTheme {
                var start by remember { mutableStateOf(8) }
                Column {
                    TextButton(onClick = { start = 40 }) { Text("reload") }
                    NumberSettingsTextField(
                        label = "Font size",
                        initialText = start,
                        range = 1..200,
                        onValueChange = { reported = it },
                    )
                }
            }
        }

        assertNumberFieldShows(8, "the field as first seeded")

        onNodeWithText("reload").performClick()
        waitForIdle()

        // Keyed on initialText, so a reload replaces what the field holds rather than leaving the
        // operator looking at the old number.
        assertNumberFieldShows(40, "the field after the settings were reloaded")
        assertEquals(0, reported, "re-seeding is not an edit and must not be reported back")
    }

    @Test
    fun `a resize handle stops dragging once its panel is collapsed`() = runComposeUiTest {
        var resized = 0
        setContent {
            MaterialTheme {
                var collapsed by remember { mutableStateOf(false) }
                Column {
                    TextButton(onClick = { collapsed = !collapsed }) { Text("toggle") }
                    PanelResizeHandle(
                        collapsed = collapsed,
                        onResize = { resized++ },
                        onResizeEnd = {},
                        onToggleCollapsed = { collapsed = !collapsed },
                        icon = painterResource(Res.drawable.ic_arrow_left),
                        contentDescription = "Collapse panel",
                    )
                }
            }
        }

        onNodeWithText("toggle").performClick()
        waitForIdle()
        onNodeWithText("toggle").performClick()
        waitForIdle()

        // The gesture detector is keyed on `collapsed`, so collapsing and reopening tears it down
        // and relaunches it. What must survive is the handle itself.
        onNodeWithContentDescription("Collapse panel").assertExists()
        assertEquals(0, resized, "nothing was dragged")
    }
}

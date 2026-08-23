package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The font field drawn without a caption above it.
 *
 * `label` defaults to empty and several call sites take that default — a field already sitting under
 * a section heading does not repeat it. The rest of this suite always passes a label, so the
 * unlabelled arrangement, where the value is the only line in the column, was never drawn.
 */
@OptIn(ExperimentalTestApi::class)
class FontSettingsDropdownNoLabelTest {

    private val installed = listOf("Arial", "Georgia", "Papyrus")

    @Test
    fun `without a label the value is the only line, and still opens and picks`() = runComposeUiTest {
        var committed: String? = null
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("Georgia") }
                Box(Modifier.width(240.dp)) {
                    FontSettingsDropdown(
                        value = value,
                        fonts = installed,
                        onValueChange = { value = it; committed = it },
                    )
                }
            }
        }

        assertEquals(
            listOf("Georgia"),
            renderedText(),
            "the field draws its value and nothing else when it has no caption",
        )

        onNodeWithText("Georgia").performClick()
        waitForIdle()
        // A row merges the family name and what it is shaped like into one node, e.g. "ArialSans".
        assertTrue(
            renderedText().any { it.startsWith("Arial") },
            "it still opens, got ${renderedText()}",
        )

        onNodeWithText("Arial", substring = true).performClick()
        waitForIdle()
        assertEquals("Arial", committed, "and still commits what was picked")
    }
}

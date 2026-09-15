package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two controls the app lends the band generator so its fields are the app's own: the colour
 * field that opens Choose Color, and the font picker over the machine's fonts.
 */
@OptIn(ExperimentalTestApi::class)
class BibleLottieBandHostControlsTest {

    @Test
    fun `the lent colour field carries its caption and the colour, and opens the app's dialog`() = runComposeUiTest {
        setContent { MaterialTheme { hostColorField("Wash", "#112233", { }, Modifier) } }
        onNodeWithText("WASH").assertExists("the caption the generator passed")
        onNodeWithText("#112233").assertExists()
        onNodeWithText("#112233").performClick()
        onNodeWithText("Choose Color", substring = true).assertExists("the app's own dialog, not the generator's")
    }

    @Test
    fun `the lent font picker lists the fonts it was given and hands a pick back`() = runComposeUiTest {
        var chosen by mutableStateOf("Arial")
        setContent {
            MaterialTheme { hostFontPicker(listOf("Arial", "Georgia"), "Font")(chosen, { chosen = it }, Modifier) }
        }
        onNodeWithText("Arial").assertExists()
        onNodeWithText("Arial").performClick()
        onAllNodesWithText("Georgia").onLast().performClick()
        assertEquals("Georgia", chosen)
    }
}

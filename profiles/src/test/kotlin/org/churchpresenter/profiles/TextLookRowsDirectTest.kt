@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

class TextLookRowsDirectTest {

    private val look = TextLook(
        fontType = "Arial", fontSize = 48, autoFit = true, color = "#ffffff", chordColor = "#ffcc00",
        bold = false, italic = false, underline = false, strikethrough = false,
        alignment = Constants.CENTER, transform = "none", letterSpacing = 0, wordSpacing = 0,
        outline = TextOutline(), backdrop = TextBackdrop(), shadow = true, shadowColor = "#000000",
        shadowSize = 100, shadowOpacity = 80,
    )

    @Test
    fun `auto-fit shows its scope only while it is on, and a look without one has no auto-fit row`() =
        runSkikoComposeUiTest(size = Size(900f, 3000f), density = Density(1f)) {
            var current by mutableStateOf(look)
            var tick by mutableIntStateOf(0)
            setContent {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState()).testTag("rows_$tick")) {
                        TextLookRows(
                            look = current,
                            onChange = { current = it },
                            fonts = listOf("Arial", "Georgia"),
                            autoFitScope = { Text("whole song") },
                            leading = { Text("leading row") },
                            extraBasic = { Text("extra basic") },
                            extraAdvanced = { Text("extra advanced") },
                        )
                    }
                }
            }
            listOf("whole song", "leading row", "extra basic", "extra advanced", "Fit across").forEach {
                onNodeWithText(it).assertExists()
            }
            tick++
            waitForIdle()
            onNodeWithText("Auto-fit").performClick()
            waitForIdle()
            assertEquals(false, current.autoFit)
            onNodeWithText("whole song").assertDoesNotExist()
            onNodeWithText("Shadow").performClick()
            waitForIdle()
            assertEquals(false, current.shadow)
            current = current.copy(autoFit = null, chordColor = null)
            waitForIdle()
            onNodeWithText("Auto-fit").assertDoesNotExist()
            onNodeWithText("Fit across").assertDoesNotExist()
        }
}

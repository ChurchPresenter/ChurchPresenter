package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The template row: a dropdown over the lower-thirds folder, and the Generate button beside it. */
@OptIn(ExperimentalTestApi::class)
class LottieBandPickerRowTest {

    @Test
    fun `the folder's templates are offered by name and a pick sets the path`() =
        withLottieFolder("sunday.json" to lottieJson(), "advent.json" to lottieJson()) { folder ->
            runComposeUiTest {
                var path by mutableStateOf("")
                setContent {
                    MaterialTheme {
                        LottieBandPickerRow(
                            path = path, onPathChange = { path = it }, templatesDir = folder, onGenerate = null,
                        )
                    }
                }
                onNodeWithText("No template selected").assertExists("nothing picked reads as no template")
                onNodeWithText("Generate…").assertDoesNotExist()
                onNodeWithText("No template selected").performClick()
                onAllNodesWithText("sunday").onLast().performClick()
                assertEquals(File(folder, "sunday.json").absolutePath, path)
                onNodeWithText("sunday").assertExists("the field now names the pick")
            }
        }

    @Test
    fun `the generate button shows when the host can save, and no template clears the pick`() =
        withLottieFolder("sunday.json" to lottieJson()) { folder ->
            runComposeUiTest {
                var path by mutableStateOf(File(folder, "sunday.json").absolutePath)
                var generated = 0
                setContent {
                    MaterialTheme {
                        LottieBandPickerRow(
                            path = path, onPathChange = { path = it }, templatesDir = folder,
                            onGenerate = { generated++ },
                        )
                    }
                }
                onNodeWithText("Generate…").performClick()
                assertEquals(1, generated)
                onNodeWithText("sunday").performClick()
                onAllNodesWithText("No template selected").onLast().performClick()
                assertEquals("", path)
            }
        }
}

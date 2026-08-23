package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two paths the rest of the suite reaches around rather than through: the composable that hands a
 * picker the installed families, and the dialog a colour field opens.
 *
 * [rememberSystemFonts] is what every font dropdown in the app is fed from, and it was composed by
 * nothing here — the other tests call the blocking [SystemFonts.families] directly. Its whole point
 * is the case that call cannot cover: the first frame, before a cold enumeration has landed.
 */
@OptIn(ExperimentalTestApi::class)
class SystemFontsAndColorFieldPathsTest {

    @Test
    fun `the remembered font list arrives without blocking the first frame`() = runComposeUiTest {
        // Cold: drop the process-wide snapshot so composition takes the enumerate-off-thread path
        // rather than reading a list some earlier test already warmed.
        SystemFonts.reset()

        var seen: List<String> = listOf("not composed")
        setContent {
            MaterialTheme {
                seen = rememberSystemFonts()
                Text("families: ${seen.size}")
            }
        }
        waitForIdle()

        assertEquals(
            SystemFonts.families(),
            seen,
            "once the enumeration lands, the composable holds exactly the process-wide snapshot",
        )
    }

    @Test
    fun `a warm snapshot is handed over on the first frame`() = runComposeUiTest {
        val warm = SystemFonts.families()

        var seen: List<String> = emptyList()
        setContent { MaterialTheme { seen = rememberSystemFonts() } }

        assertEquals(warm, seen, "warm, this must not wait for a frame")
    }

    @Test
    fun `a colour field opens its picker, takes a colour and reports it`() {
        var picked: String? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    ColorPickerField(color = "#FFD54F", onColorChange = { picked = it }, label = "Text")
                }
            }

            onNodeWithText("TEXT").performClick()
            waitForIdle()
            confirmColorDialogWith("#112233")
        }
        assertEquals("#112233", picked, "the field reports what the dialog was confirmed with")
    }

    @Test
    fun `dismissing the picker leaves the colour alone`() {
        var picked: String? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    ColorPickerField(color = "#FFD54F", onColorChange = { picked = it }, label = "Text")
                }
            }

            onNodeWithText("TEXT").performClick()
            waitForIdle()
            assertTrue(
                onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty(),
                "the dialog is open",
            )

            onNodeWithText("Cancel").performClick()
            waitForIdle()
        }
        assertEquals(null, picked, "an abandoned dialog reports nothing")
    }
}

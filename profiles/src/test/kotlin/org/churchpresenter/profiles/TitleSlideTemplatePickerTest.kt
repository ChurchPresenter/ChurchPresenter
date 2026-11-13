package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The title slide's template picker (#1017): the band templates and the Lower Third tab's presets in
 * one list, each tagged with the folder it came from.
 */
@OptIn(ExperimentalTestApi::class)
class TitleSlideTemplatePickerTest {

    private fun withTwoFolders(block: (band: File, lowerThird: File) -> Unit) =
        withLottieFolder("Hymn Band.json" to lottieJson(), "notes.txt" to "x") { band ->
            withLottieFolder("Speaker.json" to lottieJson(), "Advent.JSON" to lottieJson()) { lowerThird ->
                block(band, lowerThird)
            }
        }

    @Test
    fun `both folders are listed together by name, each file tagged with its folder`() = withTwoFolders { band, lt ->
        val listed = titleSlideTemplates(TitleSlideFolders(band, lt))

        assertEquals(listOf("Advent", "Hymn Band", "Speaker"), listed.map { it.name })
        assertEquals(
            listOf(TitleSlideTemplateKind.LOWER_THIRD, TitleSlideTemplateKind.BAND, TitleSlideTemplateKind.LOWER_THIRD),
            listed.map { it.kind },
        )
        assertEquals(File(band, "Hymn Band.json").absolutePath, listed[1].path)
    }

    @Test
    fun `a folder set in both places is listed once, and a missing folder adds nothing`() = withTwoFolders { band, _ ->
        assertEquals(
            listOf(TitleSlideTemplateKind.BAND),
            titleSlideTemplates(TitleSlideFolders(band, band)).map { it.kind },
        )
        assertEquals(listOf("Hymn Band"), titleSlideTemplates(TitleSlideFolders(band, null)).map { it.name })
        assertEquals(emptyList(), titleSlideTemplates(TitleSlideFolders(null, File(band, "gone"))))
    }

    @Test
    fun `the menu tags each template, and picking a preset stores it`() = withTwoFolders { band, lt ->
        runComposeUiTest {
            var path by mutableStateOf("")
            setContent {
                MaterialTheme {
                    TitleSlideTemplatePicker(path, { path = it }, TitleSlideFolders(band, lt))
                }
            }
            onNodeWithText("No template selected").performClick()
            // The folders are listed off the event thread.
            waitUntil { onAllNodesWithText("Speaker").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, onAllNodesWithText("Band").fetchSemanticsNodes().size)
            assertEquals(2, onAllNodesWithText("Lower Third").fetchSemanticsNodes().size)
            onAllNodesWithText("Speaker").onLast().performClick()
            assertEquals(File(lt, "Speaker.json").absolutePath, path)
        }
    }

    @Test
    fun `a file picked from elsewhere is kept in the list, untagged`() = withTwoFolders { band, lt ->
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    TitleSlideTemplatePicker("/elsewhere/Concert.json", {}, TitleSlideFolders(band, lt))
                }
            }
            onNodeWithText("Concert").performClick()
            waitUntil { onAllNodesWithText("Speaker").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(2, onAllNodesWithText("Concert").fetchSemanticsNodes().size, "the field and its menu row")
            assertEquals(1, onAllNodesWithText("Band").fetchSemanticsNodes().size, "no tag for the outside file")
        }
    }
}

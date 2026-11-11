package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.titleSlideLottie
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The title slide's Animation row (#971): a band template from the folder, or any Lottie browsed to. */
@OptIn(ExperimentalTestApi::class)
class TitleSlideLottieRowTest {

    @Test
    fun `a template from the folder is stored for this output alone`() =
        withLottieFolder("hymn.json" to lottieJson()) { folder ->
            runComposeUiTest {
                var song by mutableStateOf(SongSettings())
                setContent {
                    MaterialTheme {
                        TitleSlideLottieRow(
                            song, lowerThird = true, updateSong = { song = it(song) }, templatesDir = folder,
                        )
                    }
                }
                onNodeWithText("No template selected").performClick()
                onAllNodesWithText("hymn").onLast().performClick()
                assertEquals(File(folder, "hymn.json").absolutePath, song.titleSlideLottie(lowerThird = true))
                assertEquals("", song.titleSlideLottie(lowerThird = false), "the full screen keeps its own")
            }
        }

    @Test
    fun `Browse stores the file picked, and a cancelled pick changes nothing`() = runComposeUiTest {
        var song by mutableStateOf(SongSettings())
        var answer: String? = "/elsewhere/concert-title.json"
        setContent {
            MaterialTheme {
                TitleSlideLottieRow(
                    song,
                    lowerThird = false,
                    updateSong = { song = it(song) },
                    templatesDir = null,
                    browse = { answer },
                )
            }
        }
        onNodeWithTag(TITLE_SLIDE_LOTTIE_BROWSE_TAG).performClick()
        waitForIdle()
        assertEquals("/elsewhere/concert-title.json", song.titleSlideLottie(lowerThird = false))
        answer = null
        onNodeWithTag(TITLE_SLIDE_LOTTIE_BROWSE_TAG).performClick()
        waitForIdle()
        assertEquals("/elsewhere/concert-title.json", song.titleSlideLottie(lowerThird = false))
    }

    @Test
    fun `each output names its own path for the change list`() {
        assertEquals("songSettings.layoutExtras.titleSlideLottie", titleSlideLottiePath(lowerThird = false))
        assertEquals("songSettings.layoutExtras.titleSlideLottieLowerThird", titleSlideLottiePath(lowerThird = true))
    }
}

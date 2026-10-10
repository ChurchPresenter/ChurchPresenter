package org.churchpresenter.canvas

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.scene.ClockModes
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SourceContentDefaultsTest {

    @AfterTest
    fun stopTimers() = TimerStateManager.clear()

    @Test
    fun `text, Bible and clock content draw at their natural scale when given none`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TextSourceContent(
                    SceneSource.TextSource(id = "t", name = "T", text = "Welcome"),
                    Modifier.size(200.dp),
                )
                BibleSourceContent(
                    SceneSource.BibleSource(
                        id = "b", name = "B", verseText = "In the beginning", referenceText = "Gen 1:1",
                    ),
                    Modifier.size(200.dp),
                )
                ClockSourceContent(
                    SceneSource.ClockSource(
                        id = "c", name = "C", mode = ClockModes.COUNTDOWN, targetMinute = 2, showHours = false,
                    ),
                    Modifier.size(200.dp),
                )
            }
        }

        onNodeWithText("Welcome").assertExists()
        onNodeWithText("In the beginning", substring = true).assertExists()
        onNodeWithText("02:00").assertExists()
    }

    @Test
    fun `curved text with every default draws its glyphs and no text node`() = runComposeUiTest {
        setContent {
            MaterialTheme { CurvedText("Arched", curve = 30f, style = TextStyle(fontSize = 20.sp)) }
        }
        waitForIdle()

        onNodeWithText("Arched").assertDoesNotExist()
    }
}

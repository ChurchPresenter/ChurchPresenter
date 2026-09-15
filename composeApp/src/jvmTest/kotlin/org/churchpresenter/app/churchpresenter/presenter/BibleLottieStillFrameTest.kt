package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Background tab's stage: a template looping from its hold frame. The loop itself waits on
 * infinite-animation frames, which a test clock never delivers, so the arithmetic is pinned on
 * its own and the composable is checked for what it draws (a frame) and what it does not (a
 * file that is not a template).
 */
@OptIn(ExperimentalTestApi::class)
class BibleLottieStillFrameTest {

    @Test
    fun `the loop starts on the hold, runs on and wraps, and a file with no duration stays put`() {
        val hold = 0.4f
        val duration = 1_000_000_000f
        assertEquals(0.4f, loopedProgress(hold, 0L, duration))
        assertEquals(0.65f, loopedProgress(hold, 250_000_000L, duration), 0.0001f)
        assertEquals(0.1f, loopedProgress(hold, 700_000_000L, duration), 0.0001f, "past the end it wraps")
        assertEquals(0.4f, loopedProgress(hold, 1_000_000_000L, duration), 0.0001f, "a whole loop lands on the hold")
        assertEquals(hold, loopedProgress(hold, 5L, 0f), "nothing to play through")
    }

    @Test
    fun `a template fills its box and a file that is not one draws nothing`() {
        TestSingletons.latchSkikoHostOs()
        val dir = Files.createTempDirectory("still-frame").toFile()
        val template = LottieBandTestSupport.writeTemplate(dir)
        val plain = File(dir, "plain.json")
        plain.writeText("""{"v":"5.7.4","fr":30,"ip":0,"op":30,"w":10,"h":10,"layers":[]}""")
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.size(300.dp, 100.dp).testTag("template")) {
                        BibleLottieStillFrame(template.absolutePath, Modifier.size(300.dp, 100.dp))
                    }
                    Box(Modifier.size(300.dp, 100.dp).testTag("plain")) {
                        BibleLottieStillFrame(plain.absolutePath, Modifier.size(300.dp, 100.dp))
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("template").assertHeightIsEqualTo(100.dp)
            assertEquals(0, onNodeWithTag("plain").fetchSemanticsNode().children.size, "no image for a non-template")
        }
        dir.deleteRecursively()
    }
}

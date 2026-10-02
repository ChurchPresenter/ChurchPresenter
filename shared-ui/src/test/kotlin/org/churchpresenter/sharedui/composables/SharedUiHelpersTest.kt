package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.utils.FfmpegBinary
import org.churchpresenter.sharedui.utils.readCommandOutput
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SharedUiHelpersTest {

    private val java = File(System.getProperty("java.home"), "bin/java").path
    private val welcome =
        ScheduleItem.LabelItem(id = "l", text = "Welcome", textColor = "#FFFFFF", backgroundColor = "#000000")

    @Test
    fun `a top bar card adds its margins around the content`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.testTag("outer")) { Box(Modifier.topBarCard(start = 10.dp, end = 6.dp).size(50.dp)) }
            }
        }
        val width = onNodeWithTag("outer").fetchSemanticsNode().size.width
        assertEquals(((50 + 10 + 6) * density.density).toInt(), width)
    }

    @Test
    fun `a search bar card pads the field inside the card`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                Box(Modifier.testTag("outer")) { Box(Modifier.searchBarCard().size(50.dp)) }
            }
        }
        val width = onNodeWithTag("outer").fetchSemanticsNode().size.width
        assertEquals(((50 + 4 + 4 + 24) * density.density).toInt(), width)
    }

    @Test
    fun `a hover lift follows the pointer on and off`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.size(60.dp).testTag("tile").hoverLift(AppShape(8.dp)))
            }
        }
        onNodeWithTag("tile").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithTag("tile").performMouseInput { moveTo(Offset(-10f, -10f)) }
        waitForIdle()
        onNodeWithTag("tile").assertExists()
    }

    @Test
    fun `going live with nothing listening is a no-op`() = runComposeUiTest {
        var called = false
        setContent {
            LocalWentLive.current(welcome)
            called = true
        }
        waitForIdle()
        assertTrue(called)
    }

    @Test
    fun `going live reaches whoever provides the listener`() = runComposeUiTest {
        val reported = mutableListOf<ScheduleItem>()
        setContent {
            CompositionLocalProvider(LocalWentLive provides { reported += it }) {
                LocalWentLive.current(welcome)
            }
        }
        waitForIdle()
        assertTrue(reported.isNotEmpty())
    }

    @Test
    fun `a command that cannot start reports failure with no output`() {
        val result = readCommandOutput(listOf("/definitely/not/a/command"), timeoutSeconds = 1)
        assertEquals(-1, result.exitCode)
        assertEquals("", result.output)
    }

    @Test
    fun `a command run with a timeout hands back its output`() {
        val result = readCommandOutput(listOf(java, "-version"), timeoutSeconds = 30)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("version"), result.output)
    }

    @Test
    fun `a command run with no timeout is waited for`() {
        val result = readCommandOutput(listOf(java, "-version"), timeoutSeconds = 0)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `rechecking ffmpeg agrees with what it reports afterwards`() {
        val available = FfmpegBinary.recheck()
        assertEquals(available, FfmpegBinary.isAvailable)
        assertTrue(FfmpegBinary.path.isNotBlank())
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.dialogs.MEMORY_MONITOR_WINDOW_HEIGHT
import org.churchpresenter.dialogs.MEMORY_MONITOR_WINDOW_WIDTH
import org.churchpresenter.dialogs.MemoryMonitorWindow
import org.churchpresenter.dialogs.ToolWindowFrame
import org.churchpresenter.dialogs.ToolWindowSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ThemeMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The tool windows opened from the Help menu -- the converter, the Song Library Manager, the
 * lower-third generator, its style editor and the memory monitor: the window each asks for, the tool drawn inside it,
 * and that closing the window runs the tool's own close. The frame draws in place and records what
 * was asked for, as a headless test cannot open a window; the tools themselves are their modules'.
 */
class ToolWindowFramesTest {

    private class Frames {
        val opened = mutableListOf<ToolWindowSpec>()
        var closed = 0
        val frame: ToolWindowFrame = { spec, content ->
            if (opened.none { it.title == spec.title }) opened += spec
            Box(Modifier.size(spec.size.width, spec.size.height)) { content() }
        }
    }

    private fun opens(title: String, size: DpSize, tool: @Composable (Frames) -> Unit) = runComposeUiTest {
        val frames = Frames()
        setContent { tool(frames) }
        waitForIdle()
        val spec = frames.opened.single()
        assertEquals(title, spec.title)
        assertEquals(size, spec.size)
        spec.onClose()
        assertEquals(1, frames.closed)
    }

    @Test
    fun `the converter`() = opens("ChurchPresenter Song and Bible Converter", DpSize(1100.dp, 800.dp)) { f ->
        ConverterWindow(ThemeMode.LIGHT, onClose = { f.closed++ }, frame = f.frame)
    }

    @Test
    fun `the song library manager`() {
        val folder = Files.createTempDirectory("tool-song-library").toFile()
        try {
            opens("Song Library Manager", DpSize(1420.dp, 880.dp)) { f ->
                SongLibraryWindow(ThemeMode.DARK, folder.absolutePath, onClose = { f.closed++ }, frame = f.frame)
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `the lower-third generator`() = opens("Lottie Lower Third Generator", DpSize(1200.dp, 800.dp)) { f ->
        LottieGenWindow(ThemeMode.LIGHT, outputDir = null, onClose = { f.closed++ }, frame = f.frame)
    }

    @Test
    fun `the style editor`() = opens("Animation Style Editor", DpSize(1500.dp, 950.dp)) { f ->
        StyleEditorWindow(ThemeMode.LIGHT, onClose = { f.closed++ }, frame = f.frame)
    }

    @Test
    fun `the memory monitor`() = opens(
        "Memory Monitor",
        DpSize(MEMORY_MONITOR_WINDOW_WIDTH, MEMORY_MONITOR_WINDOW_HEIGHT),
    ) { f ->
        MemoryMonitorWindow(isVisible = true, theme = ThemeMode.DARK, onClose = { f.closed++ }, frame = f.frame)
    }
}

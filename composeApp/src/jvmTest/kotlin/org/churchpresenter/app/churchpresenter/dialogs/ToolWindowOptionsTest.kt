@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.converter.ui.ConverterTab
import org.churchpresenter.converter.ui.SongSources
import org.churchpresenter.dialogs.ToolWindowFrame
import org.churchpresenter.lottiegen.ui.Strings as LottieStrings
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolWindowOptionsTest {

    private lateinit var dir: File
    private val titles = mutableListOf<String>()
    private val frame: ToolWindowFrame = { spec, content ->
        titles += spec.title
        Box { content() }
    }

    @BeforeTest
    fun setUp() {
        TestSingletons.latchSkikoHostOs()
        dir = Files.createTempDirectory("tool-window-options").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `the converter opens on the tab and song source it is given, and follows a theme change`() = runComposeUiTest {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var unrelated by mutableStateOf(0)
        setContent {
            Text("$unrelated")
            ConverterWindow(
                theme = theme,
                initialTab = ConverterTab.SONGS,
                initialSongSource = SongSources.FREEWORSHIP,
                onClose = {},
                frame = frame,
            )
        }
        waitForIdle()
        unrelated++
        waitForIdle()
        theme = ThemeMode.DARK
        waitForIdle()

        assertTrue(titles.isNotEmpty())
        assertTrue(titles.all { it == "ChurchPresenter Song and Bible Converter" })
    }

    @Test
    fun `the song library manager records a new song book as usage`() = runComposeUiTest {
        val before = UsageEvents.unreported()[UsageEvent.SONG_LIBRARY_SONGBOOK_CREATED] ?: 0
        var theme by mutableStateOf(ThemeMode.DARK)
        setContent {
            SongLibraryWindow(
                theme = theme,
                songStorageDirectory = dir.absolutePath,
                typicalSongSeconds = { 240 },
                onClose = {},
                frame = frame,
            )
        }
        waitForIdle()
        theme = ThemeMode.LIGHT
        waitForIdle()

        onAllNodesWithText("All Song Books")[0].performClick()
        waitForIdle()
        onAllNodesWithText("New Song Book…")[0].performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size.let { fields ->
            onAllNodes(hasSetTextAction())[fields - 1].performTextInput("Anthems")
        }
        onAllNodesWithText("Create")[0].performClick()
        waitUntil(timeoutMillis = 5_000) {
            (UsageEvents.unreported()[UsageEvent.SONG_LIBRARY_SONGBOOK_CREATED] ?: 0) > before
        }

        assertTrue(File(dir, "Anthems").isDirectory)
        assertEquals("Song Library Manager", titles.last())
    }

    @Test
    fun `the lower-third generator given an output folder offers to save into it`() = runComposeUiTest {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        setContent {
            LottieGenWindow(
                theme = theme,
                outputDir = dir,
                onClose = {},
                onFileSaved = {},
                canvasWidth = 1920,
                canvasHeight = 1080,
                fontPicker = { value, _, _ -> Text(value) },
                frame = frame,
            )
        }
        waitForIdle()
        theme = ThemeMode.DARK
        waitForIdle()

        assertTrue(onAllNodesWithText(LottieStrings.saveLowerThird).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the style editor follows a theme change`() = runComposeUiTest {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var unrelated by mutableStateOf(0)
        setContent {
            Text("$unrelated")
            StyleEditorWindow(theme = theme, onClose = {}, frame = frame)
        }
        waitForIdle()
        unrelated++
        waitForIdle()
        theme = ThemeMode.DARK
        waitForIdle()

        assertTrue(titles.all { it == "Animation Style Editor" })
    }
}

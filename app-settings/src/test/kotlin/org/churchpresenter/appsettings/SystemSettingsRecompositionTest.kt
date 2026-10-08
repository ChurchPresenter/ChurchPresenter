@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.StreamingSettings
import org.churchpresenter.settings.TabLabelStyle
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The System tab kept on screen while what it shows changes underneath it: every storage folder
 * pointed somewhere new, and the settings handler itself replaced. The folder scans must follow the
 * new folders, and a press after the swap must reach the new handler rather than the old one.
 */
class SystemSettingsRecompositionTest {

    private val root: File = Files.createTempDirectory("system-recompose").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun folder(name: String) = File(root, name).apply { mkdirs() }

    @Test
    fun `new folders and a new handler take effect without leaving the tab`() = runComposeUiTest {
        val songs = folder("songs").also {
            File(it, "Hymns").mkdirs()
            File(it, "Hymns/0001 - Grace.song").writeText("")
        }
        val moved = AppSettings(
            bibleSettings = BibleSettings(storageDirectory = folder("bibles").absolutePath),
            songSettings = SongSettings(storageDirectory = songs.absolutePath),
            pictureSettings = PictureSettings(storageDirectory = folder("pictures").absolutePath),
            streamingSettings = StreamingSettings(lowerThirdFolder = folder("lower").absolutePath),
            presentationStorageDirectory = folder("decks").absolutePath,
            mediaStorageDirectory = folder("media").absolutePath,
            calendarStorageDirectory = folder("calendar").absolutePath,
            tabLabelStyle = TabLabelStyle.TEXT,
        )
        var settings by mutableStateOf(AppSettings(tabLabelStyle = TabLabelStyle.TEXT))
        var first: AppSettings? = null
        var second: AppSettings? = null
        var handler by mutableStateOf<((AppSettings) -> AppSettings) -> Unit>({ first = it(settings) })
        setContent {
            MaterialTheme {
                SystemSettingsTab(settings = settings, onSettingsChange = handler, companionServer = CompanionServer())
            }
        }
        waitForIdle()

        settings = moved
        handler = { second = it(settings) }
        waitForIdle()

        onNode(hasText("Text only") and hasClickAction()).performScrollTo().performClick()
        waitForIdle()
        assertNull(first, "the replaced handler is never called")
        assertEquals(TabLabelStyle.ICONS_AND_TEXT, second?.tabLabelStyle)
        assertEquals(songs.absolutePath, second?.songSettings?.storageDirectory, "built on the new folders")
    }
}

@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.filechooser.FileChooser
import java.nio.file.Files
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Path as NioPath

/** Browse… on the two folders that show a default path, the calendar's and the signage setup's. */
class SystemStorageBrowseTest {
    @AfterTest
    fun tidy() = unmockkAll()

    /** Answers every folder question with [picked]. */
    private class PickingChooser(private val picked: String) : FileChooser() {
        override suspend fun chooseImpl(
            path: NioPath,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean,
        ): List<NioPath> = listOf(Path(picked))

        override suspend fun saveImpl(
            location: NioPath,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): NioPath = Path(picked)
    }

    /** Clicks the [index]th "Browse…", the chooser answering [picked], and hands back the settings. */
    private fun ComposeUiTest.browse(index: Int, picked: String): AppSettings {
        mockkObject(FileChooser.Companion)
        every { FileChooser.platformInstance } returns PickingChooser(picked)
        var current by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                SystemSettingsTab(settings = current, onSettingsChange = { transform -> current = transform(current) })
            }
        }
        onAllNodesWithText("Browse...")[index].performScrollTo().performClick()
        waitUntil { onAllNodesWithText(picked).fetchSemanticsNodes().isNotEmpty() }
        return current
    }

    @Test
    fun `browsing the calendar picker stores the chosen folder`() = runComposeUiTest {
        val picked = "/picked/calendar"
        assertEquals(picked, browse(CALENDAR, picked).calendarStorageDirectory)
    }

    @Test
    fun `browsing the signage picker stores the chosen folder`() = runComposeUiTest {
        val picked = "/picked/signage"
        val settings = browse(SIGNAGE, picked)
        assertEquals(picked, settings.signageStorageDirectory)
        assertEquals("", settings.calendarStorageDirectory)
    }

    @Test
    fun `a chosen folder is shown even where the default is pinned`() = runComposeUiTest {
        val calendar = Files.createTempDirectory("chosen-calendar").toFile()
        val signage = Files.createTempDirectory("chosen-signage").toFile()
        try {
            val settings = AppSettings(
                calendarStorageDirectory = calendar.absolutePath,
                signageStorageDirectory = signage.absolutePath,
            )
            setContent {
                CompositionLocalProvider(LocalDefaultCalendarFolder provides "/pinned") {
                    MaterialTheme { SystemSettingsTab(settings = settings, onSettingsChange = {}) }
                }
            }
            onAllNodesWithText(calendar.absolutePath).assertCountEquals(1)
            onAllNodesWithText(signage.absolutePath).assertCountEquals(1)
            onAllNodesWithText("/pinned", substring = true).assertCountEquals(0)
        } finally {
            calendar.deleteRecursively()
            signage.deleteRecursively()
        }
    }

    private companion object {
        const val CALENDAR = 7
        const val SIGNAGE = 8
    }
}

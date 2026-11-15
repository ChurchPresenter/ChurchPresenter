@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.profiles.FileManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ResourceDirectory
import org.churchpresenter.settings.resourceDirectories
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdditionalResourceDirectoriesTest {
    @Test
    fun `switching a calendar default preserves the previous directory and removal only unlinks it`() {
        val root = Files.createTempDirectory("additional-directory-ui").toFile()
        val first = root.resolve("first").apply { mkdirs() }
        val second = root.resolve("second").apply { mkdirs() }
        var settings by mutableStateOf(AppSettings(
            calendarStorageDirectory = first.absolutePath,
            additionalResourceDirectories = mapOf(ResourceDirectory.CALENDAR to listOf(second.absolutePath)),
        ))
        try {
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        AdditionalResourceDirectories(settings, ResourceDirectory.CALENDAR, FileManager(),
                            { transform -> settings = transform(settings) }, settings.calendarStorageDirectory)
                    }
                }
                onNodeWithText("Use as default").performClick()
                assertEquals(listOf(second.absolutePath, first.absolutePath),
                    settings.resourceDirectories(ResourceDirectory.CALENDAR))
                onNodeWithText("Remove directory").performClick()
                assertEquals(listOf(second.absolutePath), settings.resourceDirectories(ResourceDirectory.CALENDAR))
                assertTrue(first.isDirectory)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}

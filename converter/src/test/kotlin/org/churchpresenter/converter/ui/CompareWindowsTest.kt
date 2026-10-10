@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.converter.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import kotlin.test.Test

class CompareWindowsTest {

    private fun song(file: File, vararg sections: Pair<String, String>): File = file.apply {
        parentFile.mkdirs()
        writeText(
            "[Primary]\ntitle: Grace\n" + sections.joinToString("") { (label, text) -> "\n[$label]\n$text\n" },
            Charsets.UTF_8,
        )
    }

    @Test
    fun `two duplicates open side by side, showing what each is missing and how their lines differ`() =
        withTempDir("compare-dupes") { dir ->
            song(File(dir, "A/g.song"), "Verse 1" to "Amazing grace how sweet", "Chorus" to "Praise him")
            song(File(dir, "B/g.song"), "Verse 1" to "Amazing grace how sweet the sound")
            runComposeUiTest {
                setConverterContent(FakePickers(directory = dir)) { DuplicateFinderTab() }
                click(Strings.selectFolder)
                click(Strings.scanForDuplicates)
                awaitShowing(Strings.scanAgain)

                onNodeWithContentDescription("Compare").performClick()
                awaitInWindow(Strings.left)
                awaitInWindow(Strings.right)
                awaitInWindow(Strings.missingPrefix("Chorus"))
                awaitInWindow("Praise him")
                awaitInWindow("Amazing grace how sweet the sound")
                awaitInWindow("A/g.song")
                awaitInWindow("B/g.song")
            }
        }

    @Test
    fun `files that would rename to the same name open side by side`() = withTempDir("compare-rename") { dir ->
        song(File(dir, "01 - Grace.song"), "Verse 1" to "first copy")
        song(File(dir, "02 - Grace.song"), "Verse 1" to "second copy")
        val folder = dir.name
        runComposeUiTest {
            setConverterContent(FakePickers(directory = dir)) { BulkRenameTab() }
            click(Strings.selectFolder)
            click(Strings.preview)

            onAllNodesWithContentDescription("Compare").onFirst().performClick()
            awaitInWindow(Strings.left)
            awaitInWindow("first copy")
            awaitInWindow("second copy")
            awaitInWindow("$folder/01 - Grace.song")
            awaitInWindow("$folder/02 - Grace.song")
            awaitInWindow(Strings.markForDeletion)
        }
    }
}

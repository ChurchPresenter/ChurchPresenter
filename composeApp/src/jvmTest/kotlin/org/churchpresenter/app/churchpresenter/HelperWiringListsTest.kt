@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.helper.intent.KnownOutput
import org.churchpresenter.helper.intent.KnownProfile
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the app tells Wick about the operator's own outputs, profiles, screens and Bible. */
class HelperWiringListsTest {

    private val projection = ProjectionSettings(
        screenAssignments = listOf(ScreenAssignment(activeProfileId = "main"), ScreenAssignment(screenName = "Stage")),
        browserSourceOutputs = listOf(ScreenAssignment(browserSourceName = "Web", activeProfileId = "lt")),
        ndiOutputs = listOf(ScreenAssignment(activeProfileId = "lt")),
        omtOutputs = listOf(ScreenAssignment()),
        outputProfiles = listOf(
            OutputProfile(id = "main", name = "Main"),
            OutputProfile(id = "stage", name = "Stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
        ),
    )

    @Test
    fun `every output is listed by its card, in the Projection page's order, with its profile`() = runComposeUiTest {
        var outputs = emptyList<KnownOutput>()
        setContent { outputs = helperOutputs(projection) }
        waitForIdle()
        assertEquals(
            listOf("screen" to 0, "screen" to 1, "browser" to 0, "ndi" to 0, "omt" to 0),
            outputs.map { it.kind to it.index },
        )
        assertEquals(listOf("main", null, "lt", "lt", null), outputs.map { it.profileId })
        assertTrue(outputs.all { it.label.isNotBlank() })
        assertEquals("Web", outputs[2].label)
    }

    @Test
    fun `profiles are listed by their names, a stage monitor marked`() {
        assertEquals(
            listOf(KnownProfile("main", "Main"), KnownProfile("stage", "Stage", stageMonitor = true)),
            helperProfiles(projection),
        )
    }

    @Test
    fun `with no Bible loaded there are no books, and with no display there are no screens`() {
        assertEquals(emptyList(), helperBibleBooks(null))
        // Headless: detecting the screens fails, which Wick reads as none.
        assertEquals(emptyList(), helperScreens(AppSettings(projectionSettings = projection)))
    }

    @Test
    fun `a loaded Bible's books are listed by its own name and the standard English one`() {
        val dir = createTempDirectory("wick-bible").toFile()
        try {
            val content = SpbFixture.buildContent(
                title = "Синодальный",
                books = listOf(SpbFixture.Book(1, "Бытие", 1), SpbFixture.Book(43, "John", 1)),
                verses = listOf(SpbFixture.Verse(1, 1, 1, "В начале"), SpbFixture.Verse(43, 1, 1, "In the beginning")),
            )
            val bible = SpbFixture.loadedBible(dir, content)
            // The standard English name is lower case; Wick matches names without regard to case.
            assertEquals(listOf(listOf("Бытие", "genesis"), listOf("John", "john")), helperBibleBooks(bible))
        } finally {
            dir.deleteRecursively()
        }
    }
}

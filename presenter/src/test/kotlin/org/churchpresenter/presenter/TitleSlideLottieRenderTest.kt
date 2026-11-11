@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.withTitleSlideLottie
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A title slide with a Lottie of its own (#971) plays it in place of the title slide's own text --
 * full screen and on a lower third -- and falls back to that text when the file is gone or the
 * section is not a title slide.
 */
class TitleSlideLottieRenderTest {

    private val dir = Files.createTempDirectory("title-slide-lottie").toFile()
    private val template = LottieBandTestSupport.writeTemplate(
        dir,
        name = "title.json",
        cfg = BibleLottieGenConfig(canvasW = 960, canvasH = 540),
    )
    private val title = LyricSection(
        type = Constants.SECTION_TYPE_TITLE_SLIDE,
        title = "Amazing Grace",
        songNumber = 427,
        author = "John Newton",
        lines = listOf("427 – Amazing Grace"),
    )

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun present(
        section: LyricSection,
        path: String,
        isLowerThird: Boolean = false,
        block: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        val settings = AppSettings(songSettings = SongSettings().withTitleSlideLottie(isLowerThird, path))
        setContent {
            MaterialTheme {
                Box(Modifier.size(960.dp, 540.dp)) {
                    SongPresenter(
                        lyricSection = section,
                        appSettings = settings,
                        isLowerThird = isLowerThird,
                        allLyricSections = listOf(section),
                        displaySectionIndex = 0,
                    )
                }
            }
        }
        block()
    }

    private fun ComposeUiTest.shows(text: String): Boolean =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `full screen, the template replaces the title slide's own text`() = present(title, template.path) {
        waitUntil("the template loaded and took the text's place") { !shows("John Newton") }
    }

    @Test
    fun `on a lower third, the template replaces the band`() = present(title, template.path, isLowerThird = true) {
        waitUntil("the template loaded and took the band's place") { !shows("John Newton") }
    }

    @Test
    fun `a file that is gone draws the title slide as it always was`() =
        present(title, File(dir, "gone.json").path) {
            waitForIdle()
            assertTrue(shows("John Newton"))
        }

    @Test
    fun `a section that is not a title slide is drawn as it always was`() {
        val verse = LyricSection(type = "verse", title = "Amazing Grace", lines = listOf("Amazing grace how sweet"))
        present(verse, template.path) {
            waitForIdle()
            assertTrue(shows("Amazing grace how sweet"))
        }
    }
}

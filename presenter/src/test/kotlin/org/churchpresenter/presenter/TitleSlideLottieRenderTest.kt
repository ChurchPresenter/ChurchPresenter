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

    /** A Lower Third preset (#1017): the Lower Third generator's `Name`/`Info`/`Detail` text layers. */
    private val preset = File(dir, "preset.json").apply {
        val layers = listOf("Name", "Info", "Detail").mapIndexed { i, name ->
            """{"ty": 5, "nm": "$name", "ind": ${i + 1}, "ip": 0, "op": 90, "st": 0, "sr": 1,
                "ks": {"p": {"a": 0, "k": [100, ${200 + i * 60}, 0]}, "a": {"a": 0, "k": [0, 0, 0]},
                       "s": {"a": 0, "k": [100, 100, 100]}, "r": {"a": 0, "k": 0}, "o": {"a": 0, "k": 100}},
                "t": {"d": {"k": [{"t": 0, "s": {"t": "$name", "f": "Arial", "s": 40, "fc": [1, 1, 1], "j": 0}}]},
                      "p": {}, "m": {"a": {"a": 0, "k": [0, 0]}}, "a": []}}"""
        }
        writeText(
            """{"v": "5.7.4", "fr": 30, "ip": 0, "op": 90, "w": 960, "h": 540, "layers": [${layers.joinToString(",")}],
                "fonts": {"list": [{"fName": "Arial", "fFamily": "Arial", "fStyle": "Regular", "ascent": 72}]}}""",
        )
    }

    @Test
    fun `a Lower Third preset plays the title slide on a full screen and over a whole lower third`() {
        present(title, preset.path) {
            waitUntil("the preset took the title slide's place") { !shows("John Newton") }
        }
        present(title, preset.path, isLowerThird = true) {
            waitUntil("the preset took the band's place") { !shows("John Newton") }
        }
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

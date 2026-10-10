@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.SongStyleTarget
import org.churchpresenter.presenter.titleSlideOffset
import org.churchpresenter.presenter.titleSlideOffsetTag
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongElementOptionsDirectTest {

    private class Harness {
        var doc by mutableStateOf(AppSettings())
        var element by mutableStateOf(SongStyleElement.AUTHOR)
        var titleSlide by mutableStateOf(true)
        var language by mutableStateOf(SongStyleLanguage.PRIMARY)
        val modes = mutableListOf<String>()
    }

    private fun options(body: SkikoComposeUiTest.(Harness) -> Unit) =
        runSkikoComposeUiTest(size = Size(1400f, 900f), density = Density(1f)) {
            val h = Harness()
            setContent {
                MaterialTheme {
                    Column(Modifier.testTag("options")) {
                        SongElementOptions(
                            settings = h.doc,
                            onSettingsChange = { t -> h.doc = t(h.doc) },
                            element = h.element,
                            target = SongStyleTarget.FULL_SCREEN,
                            language = h.language,
                            titleSlideView = h.titleSlide,
                            outputMode = Constants.SONG_LANG_PRIMARY,
                            onOutputModeChange = { h.modes += it },
                        )
                    }
                }
            }
            waitForIdle()
            body(h)
        }

    @Test
    fun `a title slide credit is placed by its own switch, with both offsets once it is on`() = options { h ->
        assertNull(h.doc.songSettings.titleSlideOffset(SongStyleElement.AUTHOR, SongStyleTarget.FULL_SCREEN))
        onNodeWithTag("${titleSlideOffsetTag(SongStyleElement.AUTHOR)}_enabled").performClick()
        waitForIdle()
        assertNotNull(h.doc.songSettings.titleSlideOffset(SongStyleElement.AUTHOR, SongStyleTarget.FULL_SCREEN))
        assertTrue(onAllNodesWithText("X %", substring = true).fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag("${titleSlideOffsetTag(SongStyleElement.AUTHOR)}_enabled").performClick()
        waitForIdle()
        assertNull(h.doc.songSettings.titleSlideOffset(SongStyleElement.AUTHOR, SongStyleTarget.FULL_SCREEN))
    }

    @Test
    fun `the language scope writes this output's own mode, not every output's`() = options { h ->
        h.titleSlide = false
        h.element = SongStyleElement.LYRICS
        waitForIdle()
        val all = onAllNodesWithText("All")
        all[all.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
        assertEquals(listOf(Constants.SONG_LANG_BOTH), h.modes)
        assertEquals(AppSettings().projectionSettings, h.doc.projectionSettings)
    }

    @Test
    fun `a translation's own panel carries no chunk or language scope`() = options { h ->
        h.titleSlide = false
        h.language = SongStyleLanguage.SECONDARY
        waitForIdle()
        assertTrue(onAllNodesWithText("1 Line").fetchSemanticsNodes().isEmpty())
        h.language = SongStyleLanguage.PRIMARY
        waitForIdle()
        assertTrue(onAllNodesWithText("1 Line").fetchSemanticsNodes().isNotEmpty())
    }
}

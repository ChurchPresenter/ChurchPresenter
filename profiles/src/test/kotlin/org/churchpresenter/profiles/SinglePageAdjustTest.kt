@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.TextBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private typealias BoxMap = Map<String, TextBox>

class SinglePageAdjustTest {

    private val on = TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 50f, heightPercent = 50f)
    private val moved = on.copy(xPercent = 25f)
    private val boxes = mapOf("TEXT" to on, "OFF" to on.copy(enabled = false))

    private val songTargets = SongTargets(
        element = Adjustable(CustomizeElement.SONG_LYRICS) {},
        slideElement = Adjustable(SongStyleElement.TITLE) {},
        language = Adjustable(null) {},
    )

    private fun modelFor(
        pane: CustomizePane,
        start: AppSettings,
        element: CustomizeElement? = null,
    ): Pair<AdjustModel?, () -> AppSettings> {
        var draft = start
        var model: AdjustModel? = null
        runComposeUiTest {
            setContent {
                model = adjustModelFor(
                    pane = pane,
                    draft = start,
                    profile = OutputProfile(),
                    element = Adjustable(element) {},
                    onSettingsChange = { t -> draft = t(draft) },
                    translation = Adjustable(0) {},
                    songTargets = songTargets,
                )
            }
            waitForIdle()
        }
        return model to { draft }
    }

    private val pages: List<Pair<CustomizePane, Pair<(AppSettings) -> AppSettings, (AppSettings) -> BoxMap>>> =
        listOf(
            CustomizePane.CAPTIONS to Pair(
                { s -> s.copy(sttSettings = s.sttSettings.copy(textBoxes = boxes)) }, { s -> s.sttSettings.textBoxes },
            ),
            CustomizePane.SUBTITLES to Pair(
                { s -> s.copy(mediaSettings = s.mediaSettings.copy(textBoxes = boxes)) },
                { s -> s.mediaSettings.textBoxes },
            ),
            CustomizePane.QA to Pair(
                { s -> s.copy(qaSettings = s.qaSettings.copy(textBoxes = boxes)) }, { s -> s.qaSettings.textBoxes },
            ),
            CustomizePane.DICTIONARY to Pair(
                { s -> s.copy(dictionarySettings = s.dictionarySettings.copy(textBoxes = boxes)) },
                { s -> s.dictionarySettings.textBoxes },
            ),
            CustomizePane.STAGE_MONITOR to Pair(
                { s -> s.copy(stageMonitorSettings = s.stageMonitorSettings.copy(textBoxes = boxes)) },
                { s -> s.stageMonitorSettings.textBoxes },
            ),
        )

    @Test
    fun `a single-form page offers only its switched-on boxes, and dragging one writes it back`() {
        pages.forEach { (pane, access) ->
            val (withBoxes, read) = access
            val (model, draft) = modelFor(pane, withBoxes(AppSettings()))
            val adjust = assertNotNull(model, "$pane")
            assertTrue(adjust.boxesOnly, "$pane")
            val handle = assertNotNull(adjust.boxes).handles.single()
            assertEquals("TEXT", handle.key, "$pane")

            handle.onChange(moved)

            assertEquals(moved, read(draft())["TEXT"], "$pane")
        }
    }

    @Test
    fun `a single-form page with no box on has nothing to drag`() {
        pages.forEach { (pane, _) ->
            val adjust = assertNotNull(modelFor(pane, AppSettings()).first, "$pane")
            assertNull(adjust.boxes, "$pane")
        }
    }

    @Test
    fun `the Bible page adjusts its text until a row points elsewhere, and other pages have no handles`() {
        assertNotNull(modelFor(CustomizePane.BIBLE, AppSettings()).first)
        assertNotNull(modelFor(CustomizePane.SONGS, AppSettings()).first)
        assertNull(modelFor(CustomizePane.BACKGROUND, AppSettings()).first)
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.TextBox
import kotlin.test.Test
import kotlin.test.assertEquals

class AdjustModelRecompositionTest {

    private val box = TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 50f, heightPercent = 50f)
    private val boxes = mapOf("TEXT" to box)
    private val moved = box.copy(xPercent = 20f)

    private val songTargets = SongTargets(
        element = Adjustable(CustomizeElement.SONG_LYRICS) {},
        slideElement = Adjustable(SongStyleElement.TITLE) {},
        language = Adjustable(null) {},
    )

    private val pages: Map<CustomizePane, Pair<(AppSettings) -> AppSettings, (AppSettings) -> Map<String, TextBox>>> =
        mapOf(
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
    fun `a single-form page's box writes through whichever callback it was last handed`() {
        pages.forEach { (pane, access) ->
            val (withBoxes, read) = access
            var first = withBoxes(AppSettings())
            var second = withBoxes(AppSettings())
            val toFirst: ((AppSettings) -> AppSettings) -> Unit = { t -> first = t(first) }
            val toSecond: ((AppSettings) -> AppSettings) -> Unit = { t -> second = t(second) }
            var model: AdjustModel? = null
            runComposeUiTest {
                var tick by mutableIntStateOf(0)
                var callback by mutableStateOf(toFirst)
                setContent {
                    if (tick >= 0) {
                        model = adjustModelFor(
                            pane = pane,
                            draft = withBoxes(AppSettings()),
                            profile = OutputProfile(),
                            element = Adjustable(null) {},
                            onSettingsChange = callback,
                            translation = Adjustable(0) {},
                            songTargets = songTargets,
                        )
                    }
                }
                waitForIdle()
                tick++
                waitForIdle()
                model!!.boxes!!.handles.single().onChange(moved)
                callback = toSecond
                waitForIdle()
                model!!.boxes!!.handles.single().onChange(moved)
            }
            assertEquals(moved, read(first).getValue("TEXT"), "$pane: the first callback, recomposed unchanged")
            assertEquals(moved, read(second).getValue("TEXT"), "$pane: then the one that replaced it")
        }
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * The preview beside the Customize controls, and which categories a given output is offered.
 *
 * The Stage Monitor category has no preview column at all: `CustomizeBodyLayout` returns early for
 * it and draws `StageMonitorSettingsTab` over the whole body instead.
 */
class ProjectionCustomizeStagePanelTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun output(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(BibleTranslationSettings(fileName = "kjv.spb")),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    /**
     * The rail only carries the Stage Monitor category on an output that is one; every other shape
     * gets Bible, Songs, Dictionary and Background instead.
     */
    private fun stageMonitorOutput() = output(Constants.DISPLAY_MODE_STAGE_MONITOR)

    // ── The stage monitor has nothing to show ─────────────────────────────────

    @Test
    fun `the stage monitor draws no preview column at all`() {
        projectionTab(stageMonitorOutput()) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR, override = false)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `the stage monitor draws its own settings tab over the whole body`() {
        projectionTab(stageMonitorOutput()) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR, override = false)
            onNodeWithText("Screen Layout").assertExists()
        }
    }

    @Test
    fun `the stage monitor offers no element chips`() {
        projectionTab(stageMonitorOutput()) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR, override = false)
            for (element in CustomizeElement.entries) {
                onNodeWithTag(elementChipTag(element.name)).assertDoesNotExist()
            }
        }
    }

    // ── Every other category draws one ────────────────────────────────────────

    @Test
    fun `the Bible draws a stage`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    @Test
    fun `Songs draws a stage`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    @Test
    fun `the Dictionary draws a stage`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.DICTIONARY, override = false)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    @Test
    fun `the Background draws a stage`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, override = false)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    @Test
    fun `a band draws a stage for every category too`() {
        for (pane in listOf(CustomizePane.BIBLE, CustomizePane.SONGS, CustomizePane.BACKGROUND)) {
            projectionTab(output(band)) { _ ->
                openCustomizePane(pane, override = false)
                onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
            }
        }
    }

    // ── Moving between categories ─────────────────────────────────────────────

    @Test
    fun `moving from a drawn category to the stage monitor takes the stage away`() {
        projectionTab(stageMonitorOutput()) { _ ->
            openCustomizePane(CustomizePane.DICTIONARY, override = false)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()

            onNodeWithTag(railTag(CustomizePane.STAGE_MONITOR.name)).performClick()
            waitForIdle()
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `moving back off the stage monitor brings a stage back`() {
        projectionTab(stageMonitorOutput()) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR, override = false)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertDoesNotExist()

            onNodeWithTag(railTag(CustomizePane.DICTIONARY.name)).performClick()
            waitForIdle()
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    @Test
    fun `the stage survives chipping between elements`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS, override = false)
            for (element in customizeElements(CustomizePane.SONGS)) {
                openElement(element)
                onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
            }
        }
    }

    @Test
    fun `the stage survives the override being switched on`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    @Test
    fun `the stage monitor's settings tab survives its override being switched on`() {
        projectionTab(stageMonitorOutput()) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR)
            onNodeWithText("Screen Layout").assertExists()
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `an ordinary output's four categories are all reachable from the rail`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            for (pane in listOf(
                CustomizePane.BIBLE,
                CustomizePane.SONGS,
                CustomizePane.DICTIONARY,
                CustomizePane.BACKGROUND,
            )) {
                onNodeWithTag(railTag(pane.name)).performClick()
                waitForIdle()
                onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
            }
        }
    }

    @Test
    fun `a stage monitor is offered only the two categories it can draw`() {
        projectionTab(stageMonitorOutput()) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR, override = false)
            onNodeWithTag(railTag(CustomizePane.DICTIONARY.name)).assertExists()
            onNodeWithTag(railTag(CustomizePane.BIBLE.name)).assertDoesNotExist()
            onNodeWithTag(railTag(CustomizePane.SONGS.name)).assertDoesNotExist()
            onNodeWithTag(railTag(CustomizePane.BACKGROUND.name)).assertDoesNotExist()
        }
    }

    @Test
    fun `an ordinary output is not offered the stage monitor category`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, override = false)
            onNodeWithTag(railTag(CustomizePane.STAGE_MONITOR.name)).assertDoesNotExist()
        }
    }
}

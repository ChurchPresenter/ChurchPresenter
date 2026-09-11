@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Background pane's wash rows, on the output whose shape actually has an area above a band. */
class ProjectionCustomizeAboveBandTest {

    private companion object {
        const val CAPTION = "Above The Band"
        // ColorPickerField uppercases the label it is handed; CustomizeRow does not.
        const val FILL_COLOR = "FILL COLOR"
        const val FILL_OPACITY = "Fill Opacity"
    }

    /**
     * The band is pinned to Transparent so the wash's segments can be addressed by order.
     *
     * A band on Color renders the word three times in one column — its own segment, its colour
     * field's label, and the wash's segment — and which of the three is which is not something a
     * test should be guessing. On Transparent the band contributes one node and the wash the other.
     */
    private fun output(mode: String = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL) = AppSettings(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = BackgroundConfig(
                backgroundType = Constants.BACKGROUND_TRANSPARENT,
            ),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun AppSettings.storedBand() = assertNotNull(
        projectionSettings.screenAssignments[0].backgroundOverride,
        "the output must have its own Backgrounds",
    ).bibleLowerThirdBackground

    @Test
    fun `a band's surface offers the wash`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText(CAPTION).assertExists()
        }
    }

    @Test
    fun `a full screen's surface does not`() {
        projectionTab(output(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText(CAPTION).assertDoesNotExist()
        }
    }

    @Test
    fun `the wash starts deferring, with nothing of its own to set`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            onNodeWithText(FILL_COLOR).assertDoesNotExist()
            onNodeWithText(FILL_OPACITY).assertDoesNotExist()
        }
    }

    @Test
    fun `choosing Color reveals the colour and opacity rows and stores the type`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            chooseSegment(TypeLabel.COLOR, nth = 1)
            onNodeWithText(FILL_COLOR).assertExists()
            onNodeWithText(FILL_OPACITY).assertExists()
            assertEquals(Constants.BACKGROUND_COLOR, get().storedBand().aboveBandType)
        }
    }

    @Test
    fun `the wash does not disturb the band it sits above`() {
        projectionTab(output()) { get ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE)
            val before = get().storedBand().backgroundType
            chooseSegment(TypeLabel.COLOR, nth = 1)
            assertEquals(before, get().storedBand().backgroundType)
        }
    }

    @Test
    fun `the Background chips name the lower-third surfaces they write`() {
        projectionTab(output()) { _ ->
            openCustomizePane(CustomizePane.BACKGROUND, CustomizeElement.BACKGROUND_BIBLE, override = false)
            assertTrue(
                onAllNodesWithText("Bible · Lower Third").fetchSemanticsNodes().isNotEmpty(),
                "the chip must say which of the pair it edits",
            )
        }
    }
}

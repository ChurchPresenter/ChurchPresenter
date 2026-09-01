@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every element of every category, drawn on both output shapes.
 *
 * The panes are one set of controls standing for many stored profiles, and which profile a control
 * reads is an `if (lowerThird)` at every single field. Walking the chips on a full screen and again
 * on a lower third is what proves each of those pairs resolves — and that no element's pane throws
 * on a stack, a shape or a background surface it has not been shown before.
 */
class ProjectionCustomizeElementsTest {

    private fun output(mode: String) = AppSettings(
        bibleSettings = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb"),
                BibleTranslationSettings(fileName = "niv.spb"),
            ),
        ),
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(displayMode = mode)),
        ),
    )

    private fun ComposeUiTest.openPane(pane: CustomizePane) {
        gridButton(Grid.customize(0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(railTag(pane.name)).performClick()
        waitForIdle()
    }

    /** Clicks every chip of [pane] in turn and asserts each one's pane composed. */
    private fun ComposeUiTest.walkElements(pane: CustomizePane) {
        val elements = customizeElements(pane)
        assertTrue(elements.isNotEmpty(), "$pane must offer elements")
        for (element in elements) {
            onNodeWithTag(elementChipTag(element.name)).performClick()
            waitForIdle()
            onNodeWithTag(CUSTOMIZE_ELEMENT_ROW_TAG).assertExists()
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    private fun walk(pane: CustomizePane, mode: String) = projectionTab(output(mode)) { _ ->
        openPane(pane)
        walkElements(pane)
    }

    @Test
    fun `every Bible element draws on a full screen`() =
        walk(CustomizePane.BIBLE, Constants.DISPLAY_MODE_FULLSCREEN)

    @Test
    fun `every Bible element draws on a lower third`() =
        walk(CustomizePane.BIBLE, Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)

    @Test
    fun `every Song element draws on a full screen`() =
        walk(CustomizePane.SONGS, Constants.DISPLAY_MODE_FULLSCREEN)

    @Test
    fun `every Song element draws on a lower third`() =
        walk(CustomizePane.SONGS, Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)

    @Test
    fun `every Dictionary element draws on a full screen`() =
        walk(CustomizePane.DICTIONARY, Constants.DISPLAY_MODE_FULLSCREEN)

    @Test
    fun `every Dictionary element draws on a stage monitor`() =
        walk(CustomizePane.DICTIONARY, Constants.DISPLAY_MODE_STAGE_MONITOR)

    @Test
    fun `every Background surface draws on a full screen`() =
        walk(CustomizePane.BACKGROUND, Constants.DISPLAY_MODE_FULLSCREEN)

    @Test
    fun `every Background surface draws on a lower third`() =
        walk(CustomizePane.BACKGROUND, Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)

    @Test
    fun `the second translation is styled through every Bible element`() {
        projectionTab(output(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openPane(CustomizePane.BIBLE)
            onNodeWithTag(translationChipTag(1)).performClick()
            waitForIdle()
            walkElements(CustomizePane.BIBLE)
        }
    }

    // ── The element model itself ────────────────────────────────────────────────────────────────

    @Test
    fun `the stage monitor chips nothing, and every other category chips something`() {
        assertEquals(emptyList(), customizeElements(CustomizePane.STAGE_MONITOR))
        for (pane in CustomizePane.entries - CustomizePane.STAGE_MONITOR) {
            assertTrue(customizeElements(pane).isNotEmpty(), "$pane must offer elements")
        }
    }

    @Test
    fun `a Background chip names the surface and the output's shape picks which one it writes`() {
        val pairs = listOf(
            CustomizeElement.BACKGROUND_DEFAULT to (BackgroundScope.DEFAULT to BackgroundScope.DEFAULT_LOWER_THIRD),
            CustomizeElement.BACKGROUND_BIBLE to (BackgroundScope.BIBLE to BackgroundScope.BIBLE_LOWER_THIRD),
            CustomizeElement.BACKGROUND_SONG to (BackgroundScope.SONG to BackgroundScope.SONG_LOWER_THIRD),
        )
        for ((element, scopes) in pairs) {
            assertEquals(scopes.first, element.backgroundScope(lowerThird = false))
            assertEquals(scopes.second, element.backgroundScope(lowerThird = true))
        }
    }

    @Test
    fun `a vertical band is still offered the Lower Third display mode`() {
        assertEquals(
            Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
            shownDisplayMode(Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL),
        )
        assertEquals(
            Constants.DISPLAY_MODE_FULLSCREEN,
            shownDisplayMode(Constants.DISPLAY_MODE_FULLSCREEN),
        )
    }

    @Test
    fun `picking Lower Third on a vertical band leaves it vertical`() {
        assertEquals(
            Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL,
            pickedDisplayMode(
                Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL,
            ),
        )
        assertEquals(
            Constants.DISPLAY_MODE_FULLSCREEN,
            pickedDisplayMode(Constants.DISPLAY_MODE_FULLSCREEN, Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL),
        )
    }
}

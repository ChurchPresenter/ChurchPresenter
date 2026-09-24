@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every chip of every pane draws, on every shape a profile can be.
 *
 * Breadth rather than depth: the suites beside this one prove what each control *writes*, and this
 * proves the pane it lives in composes at all. A pane that throws on one shape -- a band reading a
 * full-screen-only field, a stage monitor offered a chip it has no settings for -- fails here
 * rather than in whichever of those suites happened to open it.
 *
 * Ported from `ProjectionCustomizeElementsTest`.
 */
class ProfilesCustomizeElementsTest {

    private val shapes = listOf(
        Constants.DISPLAY_MODE_FULLSCREEN,
        Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
    )

    /**
     * Two translations, both song languages and the look-ahead on, so nothing is hidden for want of
     * a second one -- or because its content is switched off, which takes its chip away.
     */
    private fun doc(mode: String) = profileDocument(
        mode = mode,
        profile = OutputProfile(
            songMode = Constants.SONG_LANG_BOTH,
            bibleMode = Constants.SONG_LANG_BOTH,
            songLookAhead = true,
        ),
        bible = BibleSettings(
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb"),
                BibleTranslationSettings(fileName = "niv.spb"),
            ),
        ),
        song = SongSettings(titleSlideEnabled = true),
    )

    @Test
    fun `every Bible chip draws on both shapes`() {
        for (mode in shapes) {
            profilesTab(doc(mode)) { _ ->
                for (element in customizeElements(CustomizePane.BIBLE)) {
                    openCustomizePane(CustomizePane.BIBLE, element)
                    onNodeWithTag(elementChipTag(element.name)).assertExists()
                }
            }
        }
    }

    @Test
    fun `every Song chip draws on both shapes`() {
        for (mode in shapes) {
            profilesTab(doc(mode)) { _ ->
                for (element in customizeElements(CustomizePane.SONGS)) {
                    openCustomizePane(CustomizePane.SONGS, element)
                    onNodeWithTag(elementChipTag(element.name)).assertExists()
                }
            }
        }
    }

    @Test
    fun `every Background chip draws on both shapes`() {
        for (mode in shapes) {
            profilesTab(doc(mode)) { _ ->
                for (element in customizeElements(CustomizePane.BACKGROUND)) {
                    openCustomizePane(CustomizePane.BACKGROUND, element)
                    onNodeWithTag(elementChipTag(element.name)).assertExists()
                }
            }
        }
    }

    @Test
    fun `every Background chip draws once taken over as well`() {
        // Taking a surface over swaps a dimmed, untouchable column for a live one, which is a
        // different composition -- so both halves are rendered here.
        for (mode in shapes) {
            profilesTab(doc(mode)) { _ ->
                for (element in customizeElements(CustomizePane.BACKGROUND)) {
                    openBackgroundSurface(element)
                    onNodeWithTag(elementChipTag(element.name)).assertExists()
                }
            }
        }
    }

    @Test
    fun `the stage monitor pane draws`() {
        profilesTab(doc(Constants.DISPLAY_MODE_STAGE_MONITOR)) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR)
            onNodeWithTag(railTag(CustomizePane.STAGE_MONITOR.name)).assertExists()
        }
    }

    @Test
    fun `a stage monitor has no element chips at all`() {
        // Its pane is a zone layout picker rather than a set of styled elements.
        assertTrue(customizeElements(CustomizePane.STAGE_MONITOR).isEmpty())
    }

    @Test
    fun `each translation of the stack can be styled in turn`() {
        profilesTab(doc(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            // One chip per entry of the ordered stack: an earlier pane read the first translation
            // and wrote every edit to all of them.
            onNodeWithTag(translationChipTag(0)).assertExists()
            onNodeWithTag(translationChipTag(1)).assertExists()
        }
    }

    @Test
    fun `picking a second translation redraws the pane against it`() {
        profilesTab(doc(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            onNodeWithTag(translationChipTag(1)).performClick()
            waitForIdle()

            onNodeWithTag(CUSTOMIZE_TRANSLATION_ROW_TAG).assertExists()
        }
    }
}

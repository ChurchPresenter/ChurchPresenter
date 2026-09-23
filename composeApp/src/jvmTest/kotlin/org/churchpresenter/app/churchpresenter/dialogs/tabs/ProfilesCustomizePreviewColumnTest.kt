@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The column down the right of the editor: the picture itself, the resolution it is drawn at, and
 * the sample text it stands in for.
 *
 * The slot selector is session state rather than a stored setting -- it is a question about this
 * operator's checking, not about the profile's look -- so it is asserted by what redraws rather
 * than by what is written. The resolution is stored, because a simulated output has no monitor to
 * take its size from.
 */
class ProfilesCustomizePreviewColumnTest {

    private fun doc(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(
        mode = mode,
        profile = OutputProfile(songMode = Constants.SONG_LANG_BOTH),
        bible = BibleSettings(translations = listOf(BibleTranslationSettings(fileName = "kjv.spb"))),
        song = SongSettings(titleSlideEnabled = true),
    )

    @Test
    fun `the preview column is drawn beside the styling panes`() {
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            onNodeWithText("PREVIEW").assertExists()
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }

    @Test
    fun `the three sample lengths are on offer`() {
        profilesTab(doc()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            onNodeWithText("Short").assertExists()
            onNodeWithText("Medium").assertExists()
            onNodeWithText("Long").assertExists()
        }
    }

    @Test
    fun `picking a sample length redraws the picture without storing anything`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            val before = get()

            onNodeWithText("Long").performClick()
            waitForIdle()

            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
            assertEquals(before, get(), "which sample is shown is not part of the document")
        }
    }

    @Test
    fun `the stage monitor draws its own preview and gets no column`() {
        profilesTab(doc(Constants.DISPLAY_MODE_STAGE_MONITOR)) { _ ->
            openCustomizePane(CustomizePane.STAGE_MONITOR)
            // It draws a zone layout of its own inside the pane, so the shared column is absent.
            onNodeWithText("PREVIEW").assertDoesNotExist()
        }
    }

    @Test
    fun `the preview resolution is a stored property of the profile`() {
        profilesTab(doc()) { get ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)

            // Whatever it opens on, it is the profile's own and survives a pane change.
            val before = get().profile().previewWidth
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertEquals(before, get().profile().previewWidth)
        }
    }

    @Test
    fun `the picture is drawn for every pane`() {
        profilesTab(doc()) { _ ->
            for (pane in listOf(CustomizePane.BIBLE, CustomizePane.SONGS, CustomizePane.BACKGROUND)) {
                openCustomizePane(pane, customizeElements(pane).first())
                onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
            }
        }
    }

    @Test
    fun `a band draws its picture too`() {
        profilesTab(doc(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            onNodeWithTag(CUSTOMIZE_STAGE_TAG).assertExists()
        }
    }
}

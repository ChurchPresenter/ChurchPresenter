package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The strip under the settings preview: the three sample lengths, and the button that throws the
 * sample at the real outputs.
 *
 * The button is the interesting one. It borrows what is live, pushes a sample styled with the
 * *unsaved* settings, and has to give the screen back -- and specifically has to give it back when
 * the dialog closes, which from this tab's point of view is its own disposal. So the last test here
 * drops the tab rather than clicking the button off, because that is the path an operator takes
 * every time they press OK.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsPreviewOnScreenTest {

    /** One full-screen output, so there is somewhere for the preview to go. */
    private val withAnOutput = AppSettings(
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(
                    targetDisplay = 0,
                    targetBoundsW = 1920,
                    targetBoundsH = 1080,
                    displayMode = Constants.DISPLAY_MODE_FULLSCREEN,
                ),
            ),
        ),
    )

    private fun liveManager() = PresenterManager().apply {
        setLyricSection(LyricSection(title = "The operator's song", lines = listOf("their line")))
        setPresentingMode(Presenting.LYRICS)
    }

    /** The first line of each slot, which is all a line-mode output ever draws. */
    private val firstLineOf = mapOf(
        "Short" to "How sweet the sound",
        "Medium" to "Amazing grace! How sweet the sound",
        "Long" to "Through many dangers",
    )

    @Test
    fun `the sample selector changes what the preview draws`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var current by remember { mutableStateOf(AppSettings()) }
                SongSettingsTab(current, { transform -> current = transform(current) })
            }
        }

        // The medium sample is two lines; the long one carries a line neither of the others has.
        onNodeWithText("I once was lost", substring = true).assertDoesNotExist()
        onNodeWithText("Long").performClick()
        onNodeWithText("I once was lost", substring = true).assertExists()
        onNodeWithText("Short").performClick()
        onNodeWithText("I once was lost", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the samples differ by their first line, so line mode sees the selector too`() = runComposeUiTest {
        // Line mode draws the first line of a section and nothing else, and it is the lower third's
        // default. Three samples that differed only in how many lines they carried were byte
        // identical there, so the selector appeared to do nothing at all on that output.
        val lineMode = AppSettings(
            songSettings = SongSettings(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE),
        )
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var current by remember { mutableStateOf(lineMode) }
                SongSettingsTab(current, { transform -> current = transform(current) })
            }
        }

        firstLineOf.forEach { (slot, line) ->
            onNodeWithText(slot).performClick()
            onNodeWithText(line, substring = true)
                .assertExists("the $slot sample has to put its own line on a line-mode slide")
        }
    }

    @Test
    fun `no two song samples share a first line`() {
        // The property the test above checks one output at a time. Stated here as well because it is
        // the rule the three builders have to keep, not a fact about any one display mode.
        assertEquals(
            firstLineOf.values.toSet().size,
            firstLineOf.size,
            "each slot needs a first line of its own, and of visibly different length",
        )
    }

    @Test
    fun `the on-screen button is off when every output is switched off`() = runComposeUiTest {
        val noOutputs = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE)),
            ),
        )
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var current by remember { mutableStateOf(noOutputs) }
                SongSettingsTab(current, { transform -> current = transform(current) }, PresenterManager())
            }
        }

        onNodeWithTag(PREVIEW_ON_SCREEN_TAG).assertIsNotEnabled()
    }

    @Test
    fun `turning it on puts the sample and the draft settings on the outputs`() = runComposeUiTest {
        val pm = liveManager()
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var current by remember { mutableStateOf(withAnOutput) }
                SongSettingsTab(current, { transform -> current = transform(current) }, pm)
            }
        }

        onNodeWithTag(PREVIEW_ON_SCREEN_TAG).performClick()
        waitForIdle()

        assertEquals(Presenting.LYRICS, pm.presentingMode.value)
        assertTrue(pm.showPresenterWindow.value, "the output window has to be showing to preview on it")
        assertEquals(
            "Amazing grace! How sweet the sound",
            pm.lyricSection.value.lines.first(),
            "the sample the panel is drawing is the sample the screen gets",
        )
        assertNotNull(pm.previewSettingsOverride.value, "and it is styled with the settings being edited")
    }

    @Test
    fun `the preview switches reach the outputs, not just the panel`() = runComposeUiTest {
        // Both are properties of the *output*: a screen draws the band because its assignment says
        // lower third, and a look-ahead line because its assignment says so. The switches above the
        // preview describe a picture, so without translating them the button drew a full-screen
        // slide with no look-ahead however the tab was set -- which is to say it ignored everything
        // the tab asked for except the styling.
        //
        // Chords used to be a third switch here. Only a stage monitor draws a chart now, so the
        // Song tab no longer offers one and the preview never asks for it.
        val pm = liveManager()
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var current by remember { mutableStateOf(withAnOutput) }
                SongSettingsTab(current, { transform -> current = transform(current) }, pm)
            }
        }

        onNodeWithText("Lower Third").performClick()
        onNodeWithText("Look ahead").performClick()
        onNodeWithTag(PREVIEW_ON_SCREEN_TAG).performClick()
        waitForIdle()

        val previewed = assertNotNull(pm.previewSettingsOverride.value)
        val output = previewed.projectionSettings.screenAssignments.single()
        assertTrue(output.isLowerThird, "the live output has to be showing the band the tab is styling")
        assertTrue(output.songLookAhead, "and the look-ahead line the tab is previewing")
        assertEquals(
            Constants.DISPLAY_MODE_FULLSCREEN,
            withAnOutput.projectionSettings.screenAssignments.single().displayMode,
            "and the saved settings are untouched -- the override is what changed",
        )
    }

    @Test
    fun `the Bible tab leaves the song switches alone`() = runComposeUiTest {
        // It has neither, and turning them off on a screen configured for songs would be a setting
        // this tab has no business having an opinion about.
        val pm = PresenterManager()
        val withSongExtras = withAnOutput.copy(
            projectionSettings = withAnOutput.projectionSettings.copy(
                screenAssignments = withAnOutput.projectionSettings.screenAssignments
                    .map { it.copy(songLookAhead = true, showChords = true) },
            ),
            bibleSettings = BibleSettings().withTranslations(
                listOf(BibleTranslationSettings(fileName = "kjv.spb")),
            ),
        )
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var current by remember { mutableStateOf(withSongExtras) }
                BibleSettingsTab(current, { transform -> current = transform(current) }, pm)
            }
        }

        onNodeWithTag(PREVIEW_ON_SCREEN_TAG).performClick()
        waitForIdle()

        val output = assertNotNull(pm.previewSettingsOverride.value)
            .projectionSettings.screenAssignments.single()
        assertTrue(output.songLookAhead)
        assertTrue(output.showChords)
    }

    @Test
    fun `turning it off hands the operator's own content back`() = runComposeUiTest {
        val pm = liveManager()
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var current by remember { mutableStateOf(withAnOutput) }
                SongSettingsTab(current, { transform -> current = transform(current) }, pm)
            }
        }

        onNodeWithTag(PREVIEW_ON_SCREEN_TAG).performClick()
        waitForIdle()
        onNodeWithTag(PREVIEW_ON_SCREEN_TAG).performClick()
        waitForIdle()

        assertEquals("their line", pm.lyricSection.value.lines.single())
        assertEquals(Presenting.LYRICS, pm.presentingMode.value)
        assertNull(pm.previewSettingsOverride.value)
    }

    @Test
    fun `closing the dialog hands it back too`() = runComposeUiTest {
        val pm = liveManager()
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var open by remember { mutableStateOf(true) }
                var current by remember { mutableStateOf(withAnOutput) }
                if (open) {
                    SongSettingsTab(current, { transform -> current = transform(current) }, pm)
                }
                // Standing in for OK/Cancel/the window's X, all of which drop this subtree.
                androidx.compose.material3.TextButton(onClick = { open = false }) {
                    androidx.compose.material3.Text("Close the dialog")
                }
            }
        }

        onNodeWithTag(PREVIEW_ON_SCREEN_TAG).performClick()
        waitForIdle()
        assertEquals("Amazing grace! How sweet the sound", pm.lyricSection.value.lines.first())

        onNodeWithText("Close the dialog").performClick()
        waitForIdle()

        assertEquals("their line", pm.lyricSection.value.lines.single(), "the screen goes back on its own")
        assertNull(pm.previewSettingsOverride.value)
    }
}

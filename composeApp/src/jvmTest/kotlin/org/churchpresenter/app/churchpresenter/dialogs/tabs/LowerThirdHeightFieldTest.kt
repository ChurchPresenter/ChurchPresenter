package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A surface tall enough to lay the whole rail out at once, for the two order assertions.
 *
 * `assertAboveTextMargins` compares two nodes' `boundsInRoot`, and a node the rail has not laid out
 * reports `Rect(0, 0, 0, 0)` -- which is above everything, so the assertion stops describing the
 * order and starts describing the viewport. `runComposeUiTest`'s default surface is smaller than
 * this rail and `Modifier.size` on the content cannot grow it, so the surface itself is set instead.
 * The Bible rail outgrew the default when its Miscellaneous section gained the split-threshold
 * slider; the height here is given room rather than trimmed to the current content.
 */
private val RAIL_SURFACE = Size(1400f, 1800f)

/**
 * The band height, in the two tabs it moved to.
 *
 * It used to be one number on `ProjectionSettings`, edited below the table on the Screen Assignment
 * card. Only the Bible and song presenters ever read it, and the card it sat on governs none of the
 * three output kinds exclusively — so an operator sending a lower third over NDI or a Browser Source
 * could see the band on air and find nothing anywhere that moved it.
 *
 * Two values now, one per content type, so scripture can sit in a shallow band and lyrics in a
 * deeper one — each in its own tab's left rail, directly above that tab's text margins, and in the
 * same place on both. These assert the controls are there and write only their own, which is the
 * part a rendering test would miss.
 */
@OptIn(ExperimentalTestApi::class)
class LowerThirdHeightFieldTest {

    private class Harness {
        var current = AppSettings()
    }

    private fun ComposeUiTest.songTab(initial: AppSettings = AppSettings()): Harness {
        val harness = Harness().apply { current = initial }
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var settings by remember { mutableStateOf(initial) }
                SongSettingsTab(
                    settings = settings,
                    onSettingsChange = { transform ->
                        settings = transform(settings)
                        harness.current = settings
                    },
                )
            }
        }
        return harness
    }

    private fun ComposeUiTest.bibleTab(initial: AppSettings = AppSettings()): Harness {
        val harness = Harness().apply { current = initial }
        setContent {
            Box(Modifier.size(1400.dp, 900.dp)) {
                var settings by remember { mutableStateOf(initial) }
                BibleSettingsTab(
                    settings = settings,
                    onSettingsChange = { transform ->
                        settings = transform(settings)
                        harness.current = settings
                    },
                )
            }
        }
        return harness
    }

    // ── In the rail, on both tabs ───────────────────────────────────────────────────────────────

    @Test
    fun `the song rail offers the field`() = runComposeUiTest {
        songTab()

        onNodeWithTag(LOWER_THIRD_HEIGHT_TAG).assertExists()
    }

    @Test
    fun `the bible rail offers it even with no translation configured`() = runComposeUiTest {
        // The rail is where a tab keeps what belongs to the slide as a whole, so an empty stack must
        // not take the control away with it.
        bibleTab()

        onNodeWithTag(LOWER_THIRD_HEIGHT_TAG).assertExists()
    }

    /** Asserts the field sits above the tab's text margins, wherever this harness put it. */
    private fun ComposeUiTest.assertAboveTextMargins(tab: String) {
        val band = onNodeWithTag(LOWER_THIRD_HEIGHT_TAG).fetchSemanticsNode().boundsInRoot
        val margins = onNodeWithText("Text Margins", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            band.bottom <= margins.top,
            "on the $tab tab the band height belongs above the margins, not below " +
                "(band=$band margins=$margins)",
        )
    }

    // It briefly sat in a different spot on each — a row of its own in the Song pane, another under
    // the Bible target switch — which made two tabs meant to read alike read differently. Asserted
    // per tab rather than by comparing the two, because they are not laid out identically in every
    // other respect; what has to hold is that each puts it in the same place *relative to its own
    // margins*, which is what an operator moving between the tabs actually sees.

    @Test
    fun `the song tab puts it above its text margins`() =
        runSkikoComposeUiTest(size = RAIL_SURFACE, density = Density(1f)) {
            songTab()

            assertAboveTextMargins("Song")
        }

    @Test
    fun `the bible tab puts it above its text margins`() =
        runSkikoComposeUiTest(size = RAIL_SURFACE, density = Density(1f)) {
            bibleTab()

            assertAboveTextMargins("Bible")
        }

    // ── Writing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the song field stores a new percentage`() = runComposeUiTest {
        val harness = songTab()

        retypeNumberField(showing = 33, to = 45)

        assertEquals(45, harness.current.songSettings.lowerThirdHeightPercent)
        assertEquals(
            33,
            harness.current.bibleSettings.lowerThirdHeightPercent,
            "and leaves the Bible's alone -- the whole point of there being two",
        )
    }

    @Test
    fun `the bible field stores a new percentage`() = runComposeUiTest {
        val harness = bibleTab()

        retypeNumberField(showing = 33, to = 25)

        assertEquals(25, harness.current.bibleSettings.lowerThirdHeightPercent)
        assertEquals(33, harness.current.songSettings.lowerThirdHeightPercent, "and songs keep theirs")
    }

    @Test
    fun `a percentage outside the range never reaches the settings`() = runComposeUiTest {
        val harness = songTab()

        retypeNumberField(showing = 33, to = 90)

        assertEquals(
            33,
            harness.current.songSettings.lowerThirdHeightPercent,
            "90 is past the 60 ceiling and must not be stored",
        )
    }

    @Test
    fun `a stored percentage is what a fresh render shows`() = runComposeUiTest {
        // NumberSettingsTextField echoes what you type into its own state, so reading the field back
        // straight after typing would look right even if nothing were stored.
        val saved = AppSettings().let { it.copy(songSettings = it.songSettings.copy(lowerThirdHeightPercent = 47)) }
        songTab(saved)

        assertNumberFieldShows(47, "the band height field")
    }

    /**
     * The field's increment/decrement arrows are laid out at a size a click can reach.
     *
     * Inherited from the Projection tab along with the field itself: these arrows once collapsed to
     * zero pixels wide on every tab in the app — a defect in the shared `NumberSettingsTextField` —
     * and the band-height field was the one drawn number field the Projection tab had to guard it
     * with. It is drawn here, so the guard comes too.
     *
     * It earned its keep immediately. The field first went onto the pane row that holds the chunk
     * and language controls, which is full at that width; the arrows were squeezed to zero pixels
     * there. Nothing else would have caught it — the box still drew, still stored what you typed,
     * and only the arrows beside it were dead.
     */
    @Test
    fun `the field's stepper arrows can be clicked`() = runComposeUiTest {
        val harness = songTab()

        val before = harness.current.songSettings.lowerThirdHeightPercent
        // Scoped to this field's own container: the tab draws a stepper per margin too, and the
        // first arrow in tree order belongs to one of those.
        onNode(hasContentDescription("Increment") and hasAnyAncestor(hasTestTag(LOWER_THIRD_HEIGHT_TAG)))
            .performClick()
        waitForIdle()

        assertEquals(before + 1, harness.current.songSettings.lowerThirdHeightPercent)
    }
}

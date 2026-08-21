@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the Screen Layout preview — the picture of the stage monitor that tells an operator where
 * each content type will land.
 *
 * The preview is pure derivation: it groups the routing map by zone and joins the names. That makes
 * it the one part of the tab where a routing bug is *visible* rather than merely stored, so these
 * tests set routing from a fixture and assert what the cells read, then change routing by clicking
 * and assert the cells follow.
 *
 * Cells are read as **non-clickable** text: a routing dropdown merges its caption and its value into
 * one node, so "Bible, Songs" is unambiguous but a bare zone name would also match a dropdown.
 */
class StageMonitorSettingsTabPreviewTest {

    /** The text of every non-clickable node, which is what the preview is built from. */
    private fun ComposeUiTest.previewTexts(): List<String> =
        onAllNodes(!hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }

    // ── Out of the box ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the preview cells name the content routed to each zone`() = stageMonitorTab { _ ->
        val texts = previewTexts()
        assertTrue("Bible, Songs" in texts, "Top-Left starts with Bible and Songs, was $texts")
        assertTrue("Next" in texts, "Top-Right starts with Next")
        assertTrue("Announcements" in texts, "Bottom-Left starts with Announcements")
        assertTrue("Clock" in texts, "Bottom-Center starts with Clock")
    }

    @Test
    fun `the full-screen row lists everything routed there`() = stageMonitorTab { _ ->
        val fullScreen = StageMonitorContentType.entries
            .filter { it !in setOf(
                StageMonitorContentType.BIBLE, StageMonitorContentType.SONGS,
                StageMonitorContentType.NEXT, StageMonitorContentType.CLOCK,
                StageMonitorContentType.ANNOUNCEMENT_TEXT,
            ) }
            .joinToString(", ") { ContentLabel.previewOf(it) }
        onAllNodesWithText(fullScreen).assertCountEquals(1)
    }

    /**
     * An empty zone reads as an em dash rather than as blank space. Out of the box that is
     * Bottom-Right (no content) and the None row (nothing switched off).
     */
    @Test
    fun `an empty zone and an empty None row both read as a dash`() = stageMonitorTab { _ ->
        assertEquals(2, previewTexts().count { it == "—" }, "Bottom-Right and the None row start empty")
    }

    // ── Following the routing ───────────────────────────────────────────────────────────────────

    @Test
    fun `routing a content type moves its name into the new zone's cell`() = stageMonitorTab { _ ->
        assertTrue("Bible, Songs" in previewTexts(), "Bible and Songs start together")

        chooseRouting(ContentLabel.of(StageMonitorContentType.SONGS), ZoneLabel.BOTTOM_RIGHT)

        val texts = previewTexts()
        assertTrue("Bible" in texts, "Top-Left must be left with Bible alone, was $texts")
        assertTrue("Songs" in texts, "and Bottom-Right must now name Songs")
        assertTrue("Bible, Songs" !in texts, "the old pairing must be gone")
    }

    @Test
    fun `switching a content type off moves it into the None row`() = stageMonitorTab { get ->
        chooseRouting(ContentLabel.of(StageMonitorContentType.CLOCK), ZoneLabel.NONE)
        assertEquals(
            StageMonitorZone.NONE,
            get().stageMonitorSettings.zoneFor(StageMonitorContentType.CLOCK),
            "the pick must be stored",
        )

        val texts = previewTexts()
        assertTrue("Clock" in texts, "the None row must now name Clock, was $texts")
        assertEquals(
            2,
            texts.count { it == "—" },
            "Bottom-Center is now empty and Bottom-Right still is, but None no longer is",
        )
    }

    /**
     * Next is the only thing in Top-Right, so moving it away leaves that cell empty and the dash
     * count one higher.
     *
     * It is moved into **Top-Left**, not Bottom-Right, on purpose: Bottom-Right is itself empty, so
     * that move would empty one cell and fill another and leave the count at two — an assertion that
     * would hold just as well if the click had done nothing at all. Every assertion below changes.
     */
    @Test
    fun `emptying a zone makes its cell read as a dash`() = stageMonitorTab { _ ->
        val before = previewTexts()
        assertEquals(2, before.count { it == "—" }, "Bottom-Right and the None row start empty")
        assertTrue("Next" in before, "Next starts alone in Top-Right")
        assertTrue("Bible, Songs" in before, "and Top-Left starts with the other two")

        chooseRouting(ContentLabel.of(StageMonitorContentType.NEXT), ZoneLabel.TOP_LEFT)

        val after = previewTexts()
        assertTrue("Bible, Songs, Next" in after, "Next must join Top-Left, was $after")
        assertTrue("Next" !in after, "and must no longer stand alone in Top-Right")
        assertEquals(3, after.count { it == "—" }, "Top-Right is now empty as well")
    }

    /**
     * The full-screen row has its own empty state, and out of the box it can never be seen: ten
     * content types default there. Emptying it is the only way to reach that branch, and it is a
     * real configuration — a stage monitor laid out entirely in quadrants, with nothing taking the
     * whole screen.
     */
    @Test
    fun `an empty full-screen row reads as a dash`() {
        val nothingFullScreen = stageSettings {
            copy(
                contentZones = contentZones + StageMonitorContentType.entries
                    .filter { zoneFor(it) == StageMonitorZone.FULL_SCREEN }
                    .associateWith { StageMonitorZone.BOTTOM_RIGHT },
            )
        }
        stageMonitorTab(initial = nothingFullScreen) { get ->
            assertTrue(
                StageMonitorContentType.entries.none {
                    get().stageMonitorSettings.zoneFor(it) == StageMonitorZone.FULL_SCREEN
                },
                "fixture: nothing may be routed full screen",
            )
            // Bottom-Right now holds everything, so the only dashes left are the full-screen row and
            // the None row — one more than out of the box, where Bottom-Right was the empty one.
            assertEquals(2, previewTexts().count { it == "—" }, "the full-screen row must read as a dash")
            assertTrue(
                previewTexts().any { it.startsWith("Presentation, ") },
                "and Bottom-Right must now name what used to be full screen",
            )
        }
    }

    // ── Metronome ───────────────────────────────────────────────────────────────────────────────

    /**
     * The preview restates the metronome anchor in words. The dot it also draws is a bare `Box` with
     * no semantics and a flashing alpha, so this row is the only part of the metronome preview that
     * can be asserted without asserting on timing.
     */
    @Test
    fun `the preview names the metronome anchor`() {
        stageMonitorTab(initial = stageSettings { copy(metronomePosition = MetronomePosition.MIDDLE_RIGHT) }) { _ ->
            // Twice: once as the dropdown's value, once as the preview row's.
            onAllNodes(hasText(MetronomeLabel.MIDDLE_RIGHT)).assertCountEquals(2)
            onAllNodes(hasText(MetronomeLabel.MIDDLE_RIGHT) and !hasClickAction()).assertCountEquals(1)
        }
    }

    @Test
    fun `choosing a metronome anchor updates the preview row`() = stageMonitorTab { _ ->
        assertEquals(2, previewTexts().count { it == MetronomeLabel.NONE }, "the row and its caption both read None")

        chooseRouting(ContentLabel.METRONOME, MetronomeLabel.CENTER)

        assertTrue(MetronomeLabel.CENTER in previewTexts(), "the preview row must follow the pick")
    }
}

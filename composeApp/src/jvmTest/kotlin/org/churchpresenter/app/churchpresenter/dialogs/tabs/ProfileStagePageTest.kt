package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorLayout
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.hasCustomZoneSizes
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.withZoneWidth
import org.churchpresenter.settings.zoneWidthPercent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Stage layout page of a stage-monitor profile: zones and arrangement over the to-scale
 * diagram, what goes where, each zone's text -- picked on the strip -- and the fades.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileStagePageTest {

    private fun doc(stage: StageMonitorSettings = StageMonitorSettings()) = profileDocument(
        mode = Constants.DISPLAY_MODE_STAGE_MONITOR,
        profile = OutputProfile(stageMonitorSettings = stage),
    )

    private fun AppSettings.stage() = profile().stageMonitorSettings

    private fun SkikoComposeUiTest.open() = openCustomizePane(CustomizePane.STAGE_MONITOR)

    /** Picks [item] from the open dropdown menu -- the menu is drawn last. */
    private fun SkikoComposeUiTest.pickFromMenu(item: String) {
        val items = onAllNodes(hasTextExactly(item) and hasClickAction())
        items[items.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    @Test
    fun `the zone count and the arrangement pick the layout`() = profilesTab(doc()) { get ->
        open()
        tap(stageZoneCountTag(3))
        assertEquals(3, get().stage().layout.slots.size)
        segment("Three rows").performScrollTo().performClick()
        waitForIdle()
        assertEquals(StageMonitorLayout.THREE_ROWS, get().stage().layout)
        // Picking the count in force again changes nothing.
        tap(stageZoneCountTag(3))
        assertEquals(StageMonitorLayout.THREE_ROWS, get().stage().layout)
    }

    @Test
    fun `each kind of content is put in a zone, on the full screen or hidden`() = profilesTab(doc()) { get ->
        open()
        onNodeWithTag(stageContentTag(StageMonitorContentType.BIBLE)).performScrollTo().performClick()
        waitForIdle()
        pickFromMenu("Zone 2")
        assertEquals(StageMonitorZone.B, get().stage().zoneFor(StageMonitorContentType.BIBLE))
        onNodeWithTag(stageContentTag(StageMonitorContentType.CLOCK)).performScrollTo().performClick()
        waitForIdle()
        pickFromMenu("None")
        assertEquals(StageMonitorZone.NONE, get().stage().zoneFor(StageMonitorContentType.CLOCK))
        onNodeWithTag(stageContentTag(StageMonitorContentType.WEB)).performScrollTo().performClick()
        waitForIdle()
        pickFromMenu("Full Screen")
        assertEquals(StageMonitorZone.FULL_SCREEN, get().stage().zoneFor(StageMonitorContentType.WEB))
    }

    @Test
    fun `the metronome is placed from its own row`() = profilesTab(doc()) { get ->
        open()
        inRow("Metronome Position", hasClickAction()).performClick()
        waitForIdle()
        val label = onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertTrue(label > 0)
        pickFromMenu(onAllNodes(hasTextExactly("None")).let { "None" })
        assertEquals(MetronomePosition.NONE, get().stage().metronomePosition)
    }

    @Test
    fun `content stranded outside the layout is said out loud`() = profilesTab(
        doc(
            StageMonitorSettings(
                layout = StageMonitorLayout.TOP_BOTTOM,
                contentZones = StageMonitorSettings.defaultContentZones() +
                    (StageMonitorContentType.BIBLE to StageMonitorZone.E),
            ),
        ),
    ) { _ ->
        open()
        // Bible and the two other kinds the defaults put in zones this layout does not draw.
        onNodeWithText("Not shown on this layout: Bible", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `zone sizes are evened out, and reset from the group's caption`() = profilesTab(
        doc(StageMonitorSettings().withZoneWidth(StageMonitorStyleZone.A, 70f)),
    ) { get ->
        open()
        assertTrue(get().stage().hasCustomZoneSizes())
        onAllNodes(hasTextExactly("This row") and hasClickAction())[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals(get().stage().zoneWidthPercent(StageMonitorStyleZone.A),
                get().stage().zoneWidthPercent(StageMonitorStyleZone.B))
        onAllNodes(hasTextExactly("Everything") and hasClickAction())[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals(get().stage().zoneWidthPercent(StageMonitorStyleZone.C),
                get().stage().zoneWidthPercent(StageMonitorStyleZone.D))
        onAllNodes(hasTextExactly("Reset") and hasClickAction())[0].performScrollTo().performClick()
        waitForIdle()
        assertFalse(get().stage().hasCustomZoneSizes())
    }

    @Test
    fun `the Text group styles the zone picked on its strip`() = profilesTab(doc()) { get ->
        open()
        onNodeWithTag(stageZoneTag(StageMonitorStyleZone.B)).performScrollTo().performClick()
        waitForIdle()
        val before = get().stage().styleFor(StageMonitorStyleZone.B)
        stepUp("Size", times = 2)
        segment("Right").performScrollTo().performClick()
        waitForIdle()
        segment("Middle", nth = 0).performScrollTo().performClick()
        waitForIdle()
        inRow("Style", hasClickAction(), 0).performClick()
        waitForIdle()
        toggleCheckbox("Shadow")
        val after = get().stage().styleFor(StageMonitorStyleZone.B)
        assertEquals(before.fontSize + 4, after.fontSize,
                StageMonitorStyleZone.entries.joinToString { z -> "$z=${get().stage().styleFor(z).fontSize}" })
        assertEquals(Constants.RIGHT, after.horizontalAlignment)
        assertEquals(Constants.MIDDLE, after.verticalAlignment)
        assertEquals(!before.bold, after.bold)
        assertEquals(!before.shadow, after.shadow)
        assertEquals(get().stage().styleFor(StageMonitorStyleZone.A),
                StageMonitorSettings().styleFor(StageMonitorStyleZone.A))
    }

    @Test
    fun `colours, outline and highlight are set per zone, and the full screen has no chord colour`() =
        profilesTab(doc()) { get ->
            open()
            tap(stageZoneTag(StageMonitorStyleZone.A))
            val a = get().stage().styleFor(StageMonitorStyleZone.A)
            recolor(a.bgColor, "#123456")
            assertEquals("#123456", get().stage().styleFor(StageMonitorStyleZone.A).bgColor)
            onNodeWithContentDescription("Outline").performScrollTo().performClick()
            waitForIdle()
            assertTrue(get().stage().styleFor(StageMonitorStyleZone.A).outline != a.outline)
            onNodeWithContentDescription("Text backing").performScrollTo().performClick()
            waitForIdle()
            assertTrue(get().stage().styleFor(StageMonitorStyleZone.A).backdrop != a.backdrop)
            onNodeWithText("Chord color").assertExists()
            tap(stageZoneTag(StageMonitorStyleZone.FULL_SCREEN))
            assertEquals(0, onAllNodes(hasTextExactly("Chord color")).fetchSemanticsNodes().size)
        }

    @Test
    fun `the fades are the Transition group's`() = profilesTab(doc()) { get ->
        open()
        toggleCheckbox("Fade in")
        assertFalse(get().stage().fadeIn)
        stepUp("Duration")
        assertTrue(get().stage().transitionDuration > StageMonitorSettings().transitionDuration)
    }

    @Test
    fun `a zone's italic and underline are its own, and the fades out and across are the profile's`() =
        profilesTab(doc()) { get ->
            open()
            tap(stageZoneTag(StageMonitorStyleZone.C))
            val before = get().stage().styleFor(StageMonitorStyleZone.C)
            inRow("Style", hasClickAction(), 1).performScrollTo().performClick()
            waitForIdle()
            inRow("Style", hasClickAction(), 2).performScrollTo().performClick()
            waitForIdle()
            toggleCheckbox("Fade out")
            toggleCheckbox("Crossfade between items")

            val after = get().stage().styleFor(StageMonitorStyleZone.C)
            assertEquals(!before.italic, after.italic)
            assertEquals(!before.underline, after.underline)
            assertEquals(!StageMonitorSettings().fadeOut, get().stage().fadeOut)
            assertEquals(!StageMonitorSettings().crossfade, get().stage().crossfade)
            val untouched = StageMonitorSettings().styleFor(StageMonitorStyleZone.B)
            assertEquals(untouched, get().stage().styleFor(StageMonitorStyleZone.B))
        }
}

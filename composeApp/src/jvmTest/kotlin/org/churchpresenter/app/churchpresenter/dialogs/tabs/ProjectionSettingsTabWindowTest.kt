@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a presenter window sits: the four inset fields laid out around a picture of a screen.
 *
 * One control repeated four times, so what is worth pinning is which of the four each writes. A
 * pair crossed over here moves the window the wrong way by exactly the amount asked for, which
 * reads as the setting being ignored rather than as its being wired backwards.
 *
 * ## The audio card is not covered, and cannot be from here
 *
 * The Output Device dropdown beside these fields sits behind `vlcDetected`, which is
 * `isVlcAvailable` -- a property of the machine running the suite, not a parameter. A test of it
 * passes on a developer's machine with VLC installed and fails on CI, which has none: two written
 * that way did exactly that. The tab already takes [ProjectionSettingsTab]'s `detectScreens`,
 * `ndiStatus` and `ffmpegProbe` as parameters so a headless test can hand it a machine rather than
 * reading the one it is on; VLC availability is the one probe left that has no such seam. Giving it
 * one would cover the "System Default" branch, which is the branch that clears a device id left
 * behind by a machine that no longer has that device -- worth doing, but it is a change to
 * production code and belongs in its own piece of work.
 */
class ProjectionSettingsTabWindowTest {

    private fun insets() = AppSettings(
        projectionSettings = ProjectionSettings(
            // Four distinct values, none of them the 32 they all default to: each field is found by
            // the number it shows, so a shared value would make them indistinguishable.
            windowTop = 11,
            windowLeft = 22,
            windowRight = 33,
            windowBottom = 44,
            outputProfiles = withProfiles().projectionSettings.outputProfiles,
        ),
    )

    @Test
    fun `the four inset fields each write their own edge`() {
        projectionTab(insets()) { get ->
            retypeNumberField(11, 100)
            assertEquals(100, get().projectionSettings.windowTop)

            retypeNumberField(22, 200)
            assertEquals(200, get().projectionSettings.windowLeft)

            retypeNumberField(33, 300)
            assertEquals(300, get().projectionSettings.windowRight)

            retypeNumberField(44, 400)
            assertEquals(400, get().projectionSettings.windowBottom)

            // And all four together, to catch a later field having overwritten an earlier one.
            val proj = get().projectionSettings
            assertEquals(
                listOf(100, 200, 300, 400),
                listOf(proj.windowTop, proj.windowLeft, proj.windowRight, proj.windowBottom),
            )
        }
    }

    @Test
    fun `an inset the operator has not touched keeps its stored value`() {
        projectionTab(insets()) { get ->
            retypeNumberField(11, 99)

            val proj = get().projectionSettings
            assertEquals(22, proj.windowLeft)
            assertEquals(33, proj.windowRight)
            assertEquals(44, proj.windowBottom)
        }
    }
}

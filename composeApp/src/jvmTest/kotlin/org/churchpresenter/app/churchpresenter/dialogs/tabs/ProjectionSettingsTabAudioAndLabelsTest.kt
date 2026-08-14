@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assume
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ProjectionSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the parts of the tab the grid and browser-source suites leave alone: the audio card, the
 * custom VLC path, and the captions inside the Content Outputs dialog.
 *
 * The audio device list comes from VLC and so differs from machine to machine. Nothing here asserts
 * a particular device — only the entry every machine has ("System Default"), and the fallback the
 * tab uses when the stored device id matches nothing currently plugged in, which is the branch that
 * actually matters when a USB interface is unplugged between services.
 *
 * Where VLC is missing altogether — a CI runner — the tab composes **no device row at all**, only a
 * message saying so. That branch is asserted by
 * [the audio card offers devices only where VLC is installed]; the four tests that drive the
 * dropdown itself have nothing to drive there and declare an [Assume] on VLC so they are reported
 * as skipped rather than quietly passing. The VLC-path row below the card is composed either way and
 * is tested unconditionally.
 */
class ProjectionSettingsTabAudioAndLabelsTest {

    private companion object {
        /**
         * The two lines the card shows in place of the device row when VLC is not installed —
         * which, together, are the whole of that layout: it composes no icon and no button.
         *
         * The second line has a variant, `media_vlc_load_failed`, shown when VLC was found but
         * would not load. Reaching it means writing `vlcUnavailableReason`, a public `var` on a
         * singleton, which would leak into every later test in the JVM — so it is left uncovered
         * rather than reached that way.
         */
        const val VLC_REQUIRED = "VLC media player is required for media playback"
        const val VLC_INSTALL = "Please install VLC from videolan.org and restart the application"
        const val VLC_NEEDED = "no audio device row is composed without VLC installed"
    }

    private fun settingsWith(change: ProjectionSettings.() -> ProjectionSettings): AppSettings =
        AppSettings().let { it.copy(projectionSettings = it.projectionSettings.change()) }

    // ── Audio output ────────────────────────────────────────────────────────────────────────────

    /** Whether the device row exists at all is the one thing the audio card decides on its own. */
    @Test
    fun `the audio card offers devices only where VLC is installed`() = projectionTab { _ ->
        if (isVlcAvailable) {
            onNodeWithText("System Default").assertExists("with VLC the device dropdown is composed")
            onNodeWithText(VLC_REQUIRED).assertDoesNotExist() // and the install prompt is not
        } else {
            onNodeWithText(VLC_REQUIRED).assertExists("without VLC the card says so instead")
            onNodeWithText(VLC_INSTALL).assertExists("and tells the operator what to do about it")
            onNodeWithText("System Default").assertDoesNotExist() // and no device dropdown at all
        }
        onNodeWithText("Custom VLC path").assertExists("the path row is below the card either way")
    }

    @Test
    fun `the audio device dropdown starts on the system default`() {
        Assume.assumeTrue(VLC_NEEDED, isVlcAvailable)
        projectionTab { get ->
            assertEquals("", get().projectionSettings.audioOutputDeviceId, "no device chosen out of the box")
            onNodeWithText("System Default").assertExists("so the dropdown reads System Default")
        }
    }

    @Test
    fun `the audio device dropdown offers the system default`() {
        Assume.assumeTrue(VLC_NEEDED, isVlcAvailable)
        projectionTab { _ ->
            onNodeWithText("System Default").performScrollTo().performClick()
            waitForIdle()
            // The closed button and the menu's own entry — the machine's real devices join them, and
            // which those are is not asserted here because it differs per machine.
            onAllNodesWithText("System Default").assertCountEquals(2)
        }
    }

    /**
     * Starts from a **stored** device on purpose. Out of the box the id is already empty, so picking
     * the default and asserting an empty id would hold whether or not the click did anything — the
     * assertion has to have somewhere to move from to mean anything.
     */
    @Test
    fun `picking the system default clears any stored device`() {
        Assume.assumeTrue(VLC_NEEDED, isVlcAvailable)
        projectionTab(initial = settingsWith { copy(audioOutputDeviceId = "some-stored-interface") }) { get ->
            assertEquals(
                "some-stored-interface",
                get().projectionSettings.audioOutputDeviceId,
                "fixture: a device must be stored before the click, or clearing it proves nothing",
            )
            onNodeWithText("System Default").performScrollTo().performClick()
            waitForIdle()
            onAllNodesWithText("System Default")[1].performClick()
            waitForIdle()
            assertEquals("", get().projectionSettings.audioOutputDeviceId, "the default stores an empty id")
            onNodeWithText("System Default").assertExists()
        }
    }

    /**
     * A device that is no longer present — a USB interface unplugged since the last service — must
     * not leave the dropdown blank; it falls back to naming the system default.
     */
    @Test
    fun `a stored device that is no longer present falls back to the system default`() {
        Assume.assumeTrue(VLC_NEEDED, isVlcAvailable)
        projectionTab(initial = settingsWith { copy(audioOutputDeviceId = "usb-interface-that-is-gone") }) { get ->
            onNodeWithText("System Default").assertExists("an unknown device must not render blank")
            assertEquals(
                "usb-interface-that-is-gone",
                get().projectionSettings.audioOutputDeviceId,
                "but the stored id itself is left alone, so the device works again when replugged",
            )
        }
    }

    // ── Custom VLC path ─────────────────────────────────────────────────────────────────────────

    /**
     * The path box is **read-only** — its `onValueChange` is empty and it publishes no set-text
     * action — so it cannot be typed into. The only way to change it is the Browse button, which
     * opens a native directory chooser and so is never clicked here. What is testable is that the
     * box shows the right thing, which is the part an operator reads.
     */
    @Test
    fun `the VLC path box shows the stored path`() {
        projectionTab(initial = settingsWith { copy(vlcPath = "/opt/custom-vlc/lib") }) { _ ->
            onNodeWithText("Custom VLC path").assertExists("the row must be captioned")
            onNodeWithText("/opt/custom-vlc/lib").assertExists("and show the configured installation")
        }
    }

    @Test
    fun `a stored path replaces the auto-detected one`() {
        // With nothing stored the box shows whatever VLC this machine has, which differs per
        // machine; what is asserted is that a stored path takes precedence over it.
        projectionTab(initial = settingsWith { copy(vlcPath = "/opt/only-this-one/lib") }) { get ->
            onNodeWithText("/opt/only-this-one/lib").assertExists()
            assertEquals(
                "/opt/only-this-one/lib",
                get().projectionSettings.vlcPath,
                "and the stored value is what is shown",
            )
        }
    }

    @Test
    fun `the path box cannot be typed into`() {
        projectionTab(initial = settingsWith { copy(vlcPath = "/opt/custom-vlc/lib") }) { _ ->
            onAllNodes(hasSetTextAction() and hasText("/opt/custom-vlc/lib"))
                .assertCountEquals(0)
        }
    }

    // ── Content Outputs dialog captions ─────────────────────────────────────────────────────────

    @Test
    fun `the content outputs dialog groups its toggles under captions`() = projectionTab { _ ->
        gridButton(Grid.contentOutputs(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("QUICK SELECT").assertExists("the Select All / Clear All pair is captioned")
        onNodeWithText("CONTENT").assertExists("as is the content group")
        onNodeWithText("BACKGROUNDS").assertExists("and the background group")
        onNodeWithText("THIS OUTPUT SHOWS").assertExists("and the preview")
    }

    @Test
    fun `the dialog's preview names both language modes`() = projectionTab { _ ->
        gridButton(Grid.contentOutputs(row = 0)).performScrollTo().performClick()
        waitForIdle()
        // "Bible" appears twice now — the preview chip and the checkbox label in the dialog — so
        // the chip is counted rather than matched uniquely. It carries a count instead of a mode
        // name, and with a single translation configured there is nothing to count.
        onAllNodesWithText("Bible").assertCountEquals(2)
        onNodeWithText("Songs · Both").assertExists("and the Songs mode")
    }

    @Test
    fun `the Bible output can be switched off from the dialog`() = projectionTab { get ->
        // The Bible is a checkbox now, not a mode dropdown: per-translation ticks appear beside it
        // once more than one translation is configured. The on/off control lives on the master row
        // inside the translation-picker dropdown, not on the collapsed trigger, so it must be
        // opened first.
        gridButton(Grid.contentOutputs(row = 0)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithContentDescription("Bible Translations").performClick()
        waitForIdle()
        onAllNodes(isToggleable())[0].performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            org.churchpresenter.app.churchpresenter.utils.Constants.SONG_LANG_OFF,
            get().projectionSettings.screenAssignments[0].bibleMode,
            "unticking Bible must switch the output off",
        )
        // The preview chip is gone (Bible is off), leaving the trigger's own label plus the master
        // row's label inside the still-open dropdown -- toggling it does not dismiss the menu.
        onAllNodesWithText("Bible").assertCountEquals(2)
    }

    @Test
    fun `the Songs language dropdown offers every mode`() = projectionTab { _ ->
        gridButton(Grid.contentOutputs(row = 0)).performScrollTo().performClick()
        waitForIdle()
        // Songs keeps its dropdown, and is now the only one in the dialog.
        onAllNodesWithText("Both")[0].performClick()
        waitForIdle()
        for (option in listOf("Off", "Language 1", "Language 2")) {
            onNodeWithText(option).assertExists("the Songs dropdown must offer $option")
        }
    }

    // ── Remove confirmation ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the remove confirmation is titled`() {
        val withOutput = settingsWith { copy(browserSourceOutputs = listOf(ScreenAssignment())) }
        projectionTab(initial = withOutput) { _ ->
            onNodeWithText("Remove").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Confirm Delete").assertExists("the confirmation must be titled")
            onNodeWithText("Are you sure you want to remove Browser Source 1?").assertExists()
            onNodeWithText("Cancel").assertExists()
        }
    }

    // ── Stepper arrows ──────────────────────────────────────────────────────────────────────────

    /**
     * Every stepper field publishes increment/decrement arrows — one per stepper field: lower-third
     * height plus four window offsets — and each is laid out at a size a click can reach.
     *
     * These arrows used to collapse to zero pixels wide on every tab in the app — a defect in the
     * shared `NumberSettingsTextField`, which this test pinned as present-but-unusable while it
     * stood. Now that it is fixed, the same test guards the fix.
     */
    @Test
    fun `the stepper arrows are laid out where they can be clicked`() = projectionTab { _ ->
        assertStepperArrowsUsable(expected = 5)
    }
}

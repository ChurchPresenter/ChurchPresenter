@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.VlcAudioDevice
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.assertEquals

/**
 * Harness and node locators for the `ProjectionSettingsTab` suites.
 *
 * Ported from `ProjectionSettingsTabTestSupport`. The tab itself is unchanged in how it is driven --
 * it still takes [detectScreens], the NDI status and the ffmpeg probe as parameters so a headless
 * test can hand it a machine rather than reading the one it runs on -- but each assignment row is
 * shorter now: the per-row display mode and content-outputs button moved onto the Profiles tab, and
 * a **profile picker** took their place. See [Grid].
 *
 * The tab renders two quite different layouts depending on the screen list, and both matter:
 *
 *  * With **no external display** it falls back to a simulated "Dev Window" row and offers a
 *    stepper to simulate several outputs -- the single-monitor development case.
 *  * With **external displays** it renders one "Screen N" row each, with that display's resolution
 *    in its target dropdown.
 *
 * The assignment grid publishes no tags and repeats the same controls per row, so those are
 * addressed by ordinal through [gridButton].
 *
 * Never clicked: the "Browse" button next to the VLC path opens a **native** file chooser, which
 * would block the run.
 */
@OptIn(ExperimentalTestApi::class)
internal fun projectionTab(
    initial: AppSettings = withProfiles(),
    screens: List<DetectedScreen> = twoExternalScreens(),
    /**
     * Pinned, never read from the machine: on the live `NdiManager` the NDI card would render
     * differently depending on whether the machine running the suite has an NDI Runtime installed.
     */
    ndiStatus: NdiRuntimeStatus = NdiRuntimeStatus.NotInstalled,
    /** Pinned to the state a packaged app is in, for the same reason. */
    ffmpegStatus: FfmpegStatus = PINNED_FFMPEG,
    /**
     * Whether the Audio Output card believes VLC is installed, and what it lists when it is.
     *
     * Pinned rather than read, like everything above. The default is "VLC present, no devices",
     * which is the one state that draws the same picture on a developer's machine and on CI: the
     * card renders its dropdown, and the dropdown holds nothing but the system default.
     */
    vlcInstalled: Boolean = true,
    audioDevices: List<VlcAudioDevice> = emptyList(),
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    val server = CompanionServer()
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            ProjectionSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
                companionServer = server,
                detectScreens = { screens },
                ndiStatus = { ndiStatus },
                ndiReceiverCount = { 0 },
                ffmpegProbe = { ffmpegStatus },
                vlcProbe = { vlcInstalled },
                audioDeviceProbe = { audioDevices },
            )
        }
    }
    awaitAudioDevices()
    block { current }
}

/** A document carrying [names] as profiles, which the per-output picker lists. */
internal fun withProfiles(vararg names: String = arrayOf("Main", "Foyer")): AppSettings = AppSettings(
    projectionSettings = ProjectionSettings(
        outputProfiles = names.mapIndexed { i, n -> OutputProfile(id = "p$i", name = n) },
    ),
)

/** The ffmpeg every packaged app has: present, and the copy that shipped with it. */
internal val PINNED_FFMPEG = FfmpegStatus(available = true, path = "/app/ffmpeg", bundled = true)

/** The label beside the dropdown. Composed only once the device probe has answered. */
private const val AUDIO_DEVICE_LABEL = "Output device"

/** What the card draws instead when it was told VLC is absent. Composed synchronously. */
private const val VLC_REQUIRED = "VLC media player is required for media playback"

/**
 * Waits for the Audio Output card to settle, whichever of its two states it is headed for.
 *
 * The card runs its device probe on `Dispatchers.IO`, which is why `waitForIdle` does not cover it.
 * Exactly one of these two ever appears -- the dropdown's label once the probe answers, or the
 * "VLC required" message -- so waiting for either is a positive signal in both states and never
 * ends by the timeout expiring.
 *
 * It used to short-circuit on `isVlcAvailable`, which read the machine running the suite. That is
 * the bug this whole seam exists to remove, so the host's VLC is no longer consulted here either.
 */
internal fun ComposeUiTest.awaitAudioDevices() {
    waitUntil {
        onAllNodesWithText(AUDIO_DEVICE_LABEL).fetchSemanticsNodes(false).isNotEmpty() ||
            onAllNodesWithText(VLC_REQUIRED).fetchSemanticsNodes(false).isNotEmpty()
    }
}

// ── Screen fixtures ─────────────────────────────────────────────────────────────────────────────

/** A laptop on its own: one primary display and nothing to present on. */
internal fun noExternalScreens(): List<DetectedScreen> =
    listOf(DetectedScreen(index = 0, isPrimary = true, boundsX = 0, boundsY = 0, boundsW = 1920, boundsH = 1080))

/** The usual booth setup: a primary display plus two projectors of different resolutions. */
internal fun twoExternalScreens(): List<DetectedScreen> = listOf(
    DetectedScreen(index = 0, isPrimary = true, boundsX = 0, boundsY = 0, boundsW = 1920, boundsH = 1080),
    DetectedScreen(index = 1, isPrimary = false, boundsX = 1920, boundsY = 0, boundsW = 1280, boundsH = 720),
    DetectedScreen(index = 2, isPrimary = false, boundsX = 3200, boundsY = 0, boundsW = 3840, boundsH = 2160),
)

/** One external display, for tests that only need a single assignment row. */
internal fun oneExternalScreen(): List<DetectedScreen> = listOf(
    DetectedScreen(index = 0, isPrimary = true, boundsX = 0, boundsY = 0, boundsW = 1920, boundsH = 1080),
    DetectedScreen(index = 1, isPrimary = false, boundsX = 1920, boundsY = 0, boundsW = 1280, boundsH = 720),
)

// ── Grid layout ─────────────────────────────────────────────────────────────────────────────────

/**
 * Where each control sits among the tab's labelled buttons, in composition order: the Identify
 * button, then three per assignment row, then the buttons below the grid.
 *
 * Three, where the Projection tab used to have five. The display-mode dropdown and the
 * "N of M enabled" content-outputs button are gone -- both are properties of a *profile* now -- and
 * the profile picker that replaced them is one control, so a row reads: target display, key output,
 * profile.
 */
internal object Grid {
    const val IDENTIFY = 0
    const val CONTROLS_PER_ROW = 3

    /**
     * A dev fallback row carries one more: the resolution picker, between the key output and the
     * profile. A simulated window has no monitor to take its size from, so the operator sets it.
     */
    const val DEV_CONTROLS_PER_ROW = 4

    fun controlsPerRow(devFallback: Boolean) = if (devFallback) DEV_CONTROLS_PER_ROW else CONTROLS_PER_ROW

    /** How many labelled buttons the grid itself contributes for [rows] rows. */
    fun gridButtonCount(rows: Int, devFallback: Boolean = false) = 1 + rows * controlsPerRow(devFallback)

    /** The target-display dropdown for assignment row [row]. */
    fun targetDisplay(row: Int, devFallback: Boolean = false) = 1 + row * controlsPerRow(devFallback)

    /** The key-output dropdown for assignment row [row]. */
    fun keyOutput(row: Int, devFallback: Boolean = false) = 2 + row * controlsPerRow(devFallback)

    /** The resolution picker for dev fallback row [row]. Absent on a row driving a real display. */
    fun resolution(row: Int) = 3 + row * DEV_CONTROLS_PER_ROW

    /** The output-profile picker for assignment row [row]. */
    fun profile(row: Int, devFallback: Boolean = false) =
        (if (devFallback) 4 else 3) + row * controlsPerRow(devFallback)

    /**
     * The first button *after* the grid, given [rows] assignment rows.
     *
     * That is the Browser Source card's "Add Output", not the grid's -- the assignment grid has no
     * add button of its own, because its rows come from the displays the machine reports.
     */
    fun addBrowserSourceOutput(rows: Int, devFallback: Boolean = false) =
        1 + rows * controlsPerRow(devFallback)
}

/**
 * Every button on the tab that carries a label. Excludes the stepper arrows, which publish a content
 * description instead of text.
 */
private val labelledButton =
    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

internal fun ComposeUiTest.gridButtons(): SemanticsNodeInteractionCollection {
    // The audio device dropdown is one of these buttons and arrives asynchronously, so counting
    // before it lands is off by one.
    awaitAudioDevices()
    return onAllNodes(labelledButton)
}

/** One labelled button, addressed through [Grid]. */
internal fun ComposeUiTest.gridButton(ordinal: Int): SemanticsNodeInteraction = gridButtons()[ordinal]

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Opens the dropdown at [ordinal] and picks [option] from its menu.
 *
 * The open menu's item and any closed dropdown already showing [option] are indistinguishable, so
 * the fixture must leave [option] displayed nowhere else -- asserted here rather than assumed.
 */
internal fun ComposeUiTest.chooseFromDropdown(ordinal: Int, option: String) {
    val alreadyShowing = onAllNodes(hasClickAction() and hasTextExactly(option))
        .fetchSemanticsNodes(atLeastOneRootRequired = false).size
    assertEquals(
        0,
        alreadyShowing,
        "fixture error: \"$option\" is already displayed by $alreadyShowing control(s), so the menu " +
            "item cannot be told apart from them",
    )
    gridButton(ordinal).performScrollTo().performClick()
    waitForIdle()
    onNode(hasClickAction() and hasTextExactly(option)).performClick()
    waitForIdle()
}

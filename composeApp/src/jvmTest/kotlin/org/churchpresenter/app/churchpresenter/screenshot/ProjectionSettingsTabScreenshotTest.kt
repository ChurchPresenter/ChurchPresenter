@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.screenKey
import org.churchpresenter.app.churchpresenter.dialogs.tabs.DetectedScreen
import org.churchpresenter.app.churchpresenter.dialogs.tabs.Grid
import org.churchpresenter.app.churchpresenter.dialogs.tabs.ProjectionSettingsTab
import org.churchpresenter.app.churchpresenter.dialogs.tabs.TranslationPickerTags
import org.churchpresenter.app.churchpresenter.dialogs.tabs.awaitAudioDevices
import org.churchpresenter.app.churchpresenter.dialogs.tabs.gridButton
import org.churchpresenter.app.churchpresenter.dialogs.tabs.noExternalScreens
import org.churchpresenter.app.churchpresenter.dialogs.tabs.oneExternalScreen
import org.churchpresenter.app.churchpresenter.dialogs.tabs.threeTranslations
import org.churchpresenter.app.churchpresenter.dialogs.tabs.twoExternalScreens
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * The Projection tab of the settings dialog, and the Content Outputs dialog it opens, in both themes.
 *
 * **What hardware is attached is the axis of the tab itself.** The row list is built from the
 * displays the machine reports, so the tab an operator meets is a different tab on a booth machine
 * with two projectors, on a laptop with one, and on a single-monitor development machine — where it
 * falls back to a simulated "Dev Window" row plus a stepper for how many to simulate. The tab takes
 * that lookup as a parameter, so each of those is shot from a fixed screen list rather than from
 * whatever the recording machine happens to have plugged in.
 *
 * **Content Outputs is the other half.** Each output row carries an "N of M enabled" button opening a
 * modal that gates every content type for that one output — Bible (with a translation picker of its
 * own), Songs, the eight content toggles, the four background layers — beside a live monitor preview
 * of what the output would actually show. It is per-output state, not a tab section, so it is shot
 * through the row that opens it.
 *
 * Never shot, each for a reason it cannot be worked around here:
 *
 *  - **A DeckLink target.** The device list comes from `DeckLinkManager`, which enumerates real
 *    hardware through a native library; with no card fitted there is no such option to choose, and
 *    the states that hang off one — the I/O-conflict border, Web disabled on a DeckLink output —
 *    cannot be reached at all.
 *  - **The browser-source overlay URLs.** They are the running server's address, which is a LAN IP
 *    and an ephemeral port. The server is not started here, so those rows draw without them.
 */
class ProjectionSettingsTabScreenshotTest {

    // ── NDI outputs ─────────────────────────────────────────────────────────────────────────────

    /**
     * What almost every operator meets first: the NDI Runtime is a separate free download and this
     * app ships none of it, so the card is a paragraph and a link. It has to read as "not yet",
     * not as a fault.
     */
    @Test
    fun `ndi with no runtime installed`() =
        shoot("ndi_not_installed", drive = { scrollTo("registered trademark", substring = true) })

    /** With a runtime: the version, the path override, and the Add Output button. */
    @Test
    fun `ndi with a runtime and no outputs`() = shoot(
        "ndi_runtime_ready",
        ndiStatus = READY_RUNTIME,
        drive = { scrollTo("registered trademark", substring = true) },
    )

    /** One output, in the alpha mode a new one defaults to, with nobody watching yet. */
    @Test
    fun `an ndi output with no receivers`() = shoot(
        "ndi_output_alpha",
        settings = settings(ProjectionSettings(ndiOutputs = listOf(ScreenAssignment()))),
        ndiStatus = READY_RUNTIME,
        drive = { scrollTo("registered trademark", substring = true) },
    )

    /** Named, in fill+key, at 4K/60, with receivers connected — the fully configured row. */
    @Test
    fun `a named ndi output in fill and key with receivers`() = shoot(
        "ndi_output_fill_key",
        settings = settings(
            ProjectionSettings(
                ndiOutputs = listOf(
                    ScreenAssignment(
                        ndiName = "Lyrics",
                        ndiMode = Constants.NDI_MODE_FILL_AND_KEY,
                        ndiWidth = 3840,
                        ndiHeight = 2160,
                        ndiFps = 60,
                        displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                    ),
                ),
            ),
        ),
        ndiStatus = READY_RUNTIME,
        ndiReceivers = 3,
        drive = { scrollTo("registered trademark", substring = true) },
    )

    /** Switched off: the row stays, dimmed, exactly as a disabled browser source does. */
    @Test
    fun `a disabled ndi output`() = shoot(
        "ndi_output_disabled",
        settings = settings(ProjectionSettings(ndiOutputs = listOf(ScreenAssignment(ndiEnabled = false)))),
        ndiStatus = READY_RUNTIME,
        drive = { scrollTo("registered trademark", substring = true) },
    )

    /** A runtime that is there but will not load — one of only two states here that is a fault. */
    @Test
    fun `ndi with a runtime that will not load`() = shoot(
        "ndi_runtime_load_failed",
        ndiStatus = NdiRuntimeStatus.LoadFailed("/usr/local/lib/libndi.dylib"),
        drive = { scrollTo("registered trademark", substring = true) },
    )

    /** The other: a processor without SSE4.2, which NDI cannot run on at all. */
    @Test
    fun `ndi on an unsupported processor`() = shoot(
        "ndi_unsupported_cpu",
        ndiStatus = NdiRuntimeStatus.UnsupportedCpu,
        drive = { scrollTo("registered trademark", substring = true) },
    )

    // ── The tab as the attached hardware makes it ───────────────────────────────────────────────

    /** The booth case: two projectors, both resolved to a row of their own. */
    @Test
    fun `as it opens, with two displays`() = shoot("defaults")

    @Test
    fun `with a single display`() = shoot("single_display", screens = oneExternalScreen())

    /**
     * A single-monitor machine: no display to present on, so the dev fallback row stands in for the
     * windowed output the app opens instead, and a stepper appears to simulate more of them.
     */
    @Test
    fun `the dev window fallback`() = shoot("dev_window", screens = noExternalScreens())

    /** The stepper turned up: four simulated outputs, four rows to configure. */
    @Test
    fun `several simulated outputs`() = shoot(
        "dev_windows_simulated",
        screens = noExternalScreens(),
        settings = settings(ProjectionSettings(devWindowCount = 4)),
    )

    /** Three outputs, each in a different display mode, the first driving a key signal as well. */
    @Test
    fun `outputs configured differently`() = shoot("outputs_configured", screens = BOOTH, settings = configured())

    /**
     * Renamed monitors: the operator's own names in the row and in the target buttons beside them.
     *
     * Both displays are named here and the second is the one to read — its name is stored against
     * the monitor's geometry, so it is what the Display button shows too, where an unnamed display
     * would read "D2 (3840x2160)".
     */
    @Test
    fun `named screens`() = shoot(
        "screens_named",
        settings = settings(
            ProjectionSettings(
                screenAssignments = listOf(
                    ScreenAssignment().on(twoExternalScreens()[1]),
                    ScreenAssignment().on(twoExternalScreens()[2]),
                ),
            )
                .withScreenName(screenKey(1920, 0, 1280, 720), "Sanctuary Left")
                .withScreenName(screenKey(3200, 0, 3840, 2160), "Foyer TV"),
        ),
    )

    /** The dev-fallback row named: no monitor to key it to, so the name sits on the slot itself. */
    @Test
    fun `named dev window`() = shoot(
        "dev_window_named",
        screens = noExternalScreens(),
        settings = settings(
            ProjectionSettings(screenAssignments = listOf(ScreenAssignment(screenName = "Rehearsal"))),
        ),
    )

    // ── The dropdowns a row opens ───────────────────────────────────────────────────────────────

    /** Where an output goes: None, then every non-primary display with its resolution and origin. */
    @Test
    fun `the target display menu`() = shoot("display_menu", rootIndex = 1) {
        gridButton(Grid.targetDisplay(0)).performScrollTo().performClick()
        waitForIdle()
    }

    /** The key signal's own target — the same list, chosen independently of the fill. */
    @Test
    fun `the key output menu`() = shoot("key_menu", rootIndex = 1) {
        gridButton(Grid.keyOutput(0)).performScrollTo().performClick()
        waitForIdle()
    }

    /** The four ways an output can draw: full screen, either lower third, or the stage monitor. */
    @Test
    fun `the display mode menu`() = shoot("display_mode_menu", rootIndex = 1) {
        gridButton(Grid.displayMode(0)).performScrollTo().performClick()
        waitForIdle()
    }

    // ── Browser source outputs ──────────────────────────────────────────────────────────────────
    // The tab fits in one viewport until an output is added, so `defaults` already carries this
    // card empty — and the Audio Output card with it. Neither is shot on its own; the only thing
    // really below the fold is the foot of the Window Position card.

    /** One added: enabled, at the defaults it arrives with. */
    @Test
    fun `a browser source output`() = shoot(
        "browser_source_enabled",
        settings = settings(ProjectionSettings(browserSourceOutputs = listOf(ScreenAssignment()))),
    )

    /** Switched off: the row stays, dimmed, so an inactive output is obvious at a glance. */
    @Test
    fun `a disabled browser source output`() = shoot(
        "browser_source_disabled",
        settings = settings(
            ProjectionSettings(browserSourceOutputs = listOf(ScreenAssignment(browserSourceEnabled = false)))
        ),
    )

    /** Two, the second at 4K/60 as a lower third and demanding the server's API key. */
    @Test
    fun `two browser source outputs`() = shoot(
        "browser_sources_two",
        settings = settings(
            ProjectionSettings(
                browserSourceOutputs = listOf(
                    ScreenAssignment(),
                    ScreenAssignment(
                        displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                        browserSourceWidth = 3840,
                        browserSourceHeight = 2160,
                        browserSourceFps = 60,
                        browserSourceApiKeyRequired = true,
                    ),
                )
            )
        ),
    )

    /** Renamed: the operator's own names sit where the numbers were, on the row and everywhere
     *  else the output is named. */
    @Test
    fun `named browser source outputs`() = shoot(
        "browser_sources_named",
        settings = settings(
            ProjectionSettings(
                browserSourceOutputs = listOf(
                    ScreenAssignment(browserSourceName = "Audience"),
                    ScreenAssignment(browserSourceName = "Stage"),
                )
            )
        ),
    )

    /** The frame size an overlay renders at. */
    @Test
    fun `the browser source resolution menu`() = shoot(
        "browser_source_resolution_menu",
        settings = settings(ProjectionSettings(browserSourceOutputs = listOf(ScreenAssignment()))),
        rootIndex = 1,
    ) {
        onAllNodesWithText(DEFAULT_RESOLUTION)[0].performClick()
        waitForIdle()
    }

    /** How fast it is allowed to push them. */
    @Test
    fun `the browser source frame rate menu`() = shoot(
        "browser_source_fps_menu",
        settings = settings(ProjectionSettings(browserSourceOutputs = listOf(ScreenAssignment()))),
        rootIndex = 1,
    ) {
        onAllNodesWithText(DEFAULT_FPS)[0].performClick()
        waitForIdle()
    }

    // Not shot: a browser source's own display-mode menu. It is the same four options as the grid's,
    // already in `display_mode_menu` — only the control it hangs under differs.

    // ── The foot of the tab ─────────────────────────────────────────────────────────────────────

    /**
     * The window inset fields around their screen mock, scrolled to the very bottom.
     *
     * Off the defaults so each of the four insets is distinguishable — they are all 32 otherwise,
     * and an image of four identical numbers says nothing about which field is which.
     */
    @Test
    fun `the window position card`() = shoot(
        "window_position",
        settings = settings(
            ProjectionSettings(windowTop = 0, windowLeft = 120, windowRight = 60, windowBottom = 240)
        ),
    ) { scrollTo(POSITION_HELP, substring = true) }

    // ── Content Outputs ─────────────────────────────────────────────────────────────────────────

    /** As it opens on a fresh output: everything on, and the preview listing all of it. */
    @Test
    fun `content outputs, everything enabled`() = contentOutputs("content_outputs_all")

    /**
     * Trimmed down: Bible off, Songs on one language only, half the content toggles cleared.
     *
     * Song look-ahead is on here, which no other image has: it is off by default, and off again the
     * moment Songs is — its cell greys out with it, which is what `content_outputs_cleared` shows.
     */
    @Test
    fun `content outputs, partly enabled`() = contentOutputs(
        "content_outputs_partial",
        assignment = ScreenAssignment(
            bibleMode = Constants.SONG_LANG_OFF,
            songMode = Constants.SONG_LANG_PRIMARY,
            songLookAhead = true,
            showPictures = false,
            showMedia = false,
            showQA = false,
            showSTT = false,
            showDictionary = false,
            showBibleBackground = false,
        ),
    )

    /** Nothing at all: the preview says so rather than drawing an empty list. */
    @Test
    fun `content outputs, nothing enabled`() = contentOutputs("content_outputs_cleared", assignment = nothingEnabled())

    // Not shot: the dialog scrolled down. Its column is allowed 520dp and the whole of it — Quick
    // Select, Bible and Songs, the ten content toggles, the four background layers — draws inside
    // that, so there is nothing under the fold to scroll to; `content_outputs_all` is the whole
    // dialog.

    /** Which translations of the stack this output carries, picked one by one. */
    @Test
    fun `the translation picker open`() = contentOutputs(
        "content_outputs_translations",
        assignment = ScreenAssignment(bibleTranslations = listOf(0, 2)),
        rootIndex = 2,
    ) {
        onNodeWithTag(TranslationPickerTags.TRIGGER).performClick()
        waitForIdle()
    }

    /** Songs, which is a language mode rather than a switch. */
    @Test
    fun `the song language menu open`() = contentOutputs("content_outputs_songs_menu", rootIndex = 2) {
        onAllNodesWithText(BOTH)[0].performClick()
        waitForIdle()
    }

    /**
     * The same dialog opened from a browser-source output instead of a screen.
     *
     * It is titled for that output rather than a screen, and the Web toggle means something else
     * there — an overlay snapshots the page rather than embedding it — so it carries a different
     * explanation.
     */
    @Test
    fun `content outputs for a browser source`() = shoot(
        "content_outputs_browser_source",
        settings = settings(ProjectionSettings(browserSourceOutputs = listOf(ScreenAssignment()))),
        rootIndex = 1,
    ) {
        // Third "N of M enabled" button on the tab: one per screen row, then the browser source's.
        onAllNodesWithText(ENABLED_COUNT, substring = true)[2].performClick()
        waitForIdle()
    }

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /** Opens the Content Outputs dialog on the first screen row and shoots it. */
    private fun contentOutputs(
        name: String,
        assignment: ScreenAssignment = ScreenAssignment(),
        rootIndex: Int = 1,
        drive: ComposeUiTest.() -> Unit = {},
    ) = shoot(
        name,
        settings = settings(ProjectionSettings(screenAssignments = listOf(assignment.on(DISPLAY_1)))),
        rootIndex = rootIndex,
    ) {
        gridButton(Grid.contentOutputs(0)).performScrollTo().performClick()
        waitForIdle()
        drive()
    }

    private fun ComposeUiTest.scrollTo(label: String, substring: Boolean = false) {
        onAllNodesWithText(label, substring = substring)[0].performScrollTo()
        waitForIdle()
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        settings: AppSettings = settings(),
        screens: List<DetectedScreen> = twoExternalScreens(),
        rootIndex: Int = 0,
        // Pinned, never read from the machine: whether NDI is installed here would otherwise decide
        // what the card draws, the way canvasTab/source_camera used to enumerate real cameras.
        ndiStatus: NdiRuntimeStatus = NdiRuntimeStatus.NotInstalled,
        ndiReceivers: Int = 0,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            val server = CompanionServer()
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            var current by remember { mutableStateOf(settings) }
                            ProjectionSettingsTab(
                                settings = current,
                                onSettingsChange = { transform -> current = transform(current) },
                                companionServer = server,
                                detectScreens = { screens },
                                ndiStatus = { ndiStatus },
                                ndiReceiverCount = { ndiReceivers },
                            )
                        }
                    }
                }
            }
            waitForIdle()
            // The audio card's device list comes back off Dispatchers.IO, which waitForIdle does
            // not cover — without this the capture can catch its spinner instead of the dropdown.
            awaitAudioDevices()
            drive()
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /**
     * Settings carrying [projection] and a three-translation Bible stack.
     *
     * The stack is there for the Content Outputs translation picker, which lists one row per
     * configured translation and is empty without it. No storage directory is set, so nothing is
     * read off disk and each row names itself by its file stem.
     */
    private fun settings(projection: ProjectionSettings = ProjectionSettings()): AppSettings =
        threeTranslations(projection)

    /** An assignment pointed at [screen], the way the tab itself resolves one. */
    private fun ScreenAssignment.on(screen: DetectedScreen) = copy(
        targetDisplay = screen.index,
        targetBoundsX = screen.boundsX,
        targetBoundsY = screen.boundsY,
        targetBoundsW = screen.boundsW,
        targetBoundsH = screen.boundsH,
    )

    /** Every toggle off and both language modes off — the state the preview calls out as blank. */
    private fun nothingEnabled() = ScreenAssignment(
        bibleMode = Constants.SONG_LANG_OFF,
        songMode = Constants.SONG_LANG_OFF,
        showPictures = false,
        showMedia = false,
        showStreaming = false,
        showAnnouncements = false,
        showWebsite = false,
        showQA = false,
        showSTT = false,
        showDictionary = false,
        showCanvas = false,
        showFullscreenBackground = false,
        showLowerThirdBackground = false,
        showBibleBackground = false,
        showSongsBackground = false,
    )

    /**
     * Three outputs that do not look alike: a lower third driving a key signal off the third
     * display, a stage monitor, and a full-screen output showing nothing but songs.
     */
    private fun configured() = settings(
        ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(
                    displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                    keyTargetDisplay = DISPLAY_3.index,
                    keyTargetBoundsX = DISPLAY_3.boundsX,
                    keyTargetBoundsY = DISPLAY_3.boundsY,
                    keyTargetBoundsW = DISPLAY_3.boundsW,
                    keyTargetBoundsH = DISPLAY_3.boundsH,
                ).on(DISPLAY_1),
                ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR).on(DISPLAY_2),
                ScreenAssignment(
                    bibleMode = Constants.SONG_LANG_OFF,
                    songMode = Constants.SONG_LANG_PRIMARY,
                    showPictures = false,
                    showMedia = false,
                    showStreaming = false,
                    showAnnouncements = false,
                    showWebsite = false,
                    showQA = false,
                    showSTT = false,
                    showDictionary = false,
                    showCanvas = false,
                ).on(DISPLAY_3),
            )
        )
    )

    private companion object {
        const val SECTION = "projectionSettingsTab"

        /** A fixed "installed" runtime, so the version on screen is a constant and not the host's. */
        val READY_RUNTIME = NdiRuntimeStatus.Ready("6.1.1", "/usr/local/lib/libndi.dylib")

        val DISPLAY_1 = twoExternalScreens()[1]
        val DISPLAY_2 = twoExternalScreens()[2]
        val DISPLAY_3 = DetectedScreen(
            index = 3,
            isPrimary = false,
            boundsX = 7040,
            boundsY = 0,
            boundsW = 1920,
            boundsH = 1080,
        )

        /** A primary display and three projectors — three assignment rows. */
        val BOOTH = twoExternalScreens() + DISPLAY_3

        /** The last thing on the tab, and so the scroll target for its foot. */
        const val POSITION_HELP = "Position values represent"

        /** The Songs cell's current mode, inside the Content Outputs dialog. */
        const val BOTH = "Both"
        const val ENABLED_COUNT = "enabled"

        const val DEFAULT_RESOLUTION = "1920×1080"
        const val DEFAULT_FPS = "30"
    }
}

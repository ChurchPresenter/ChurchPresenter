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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import kotlin.test.assertEquals

/**
 * Harness and node locators shared by the `ProjectionSettingsTab` test classes.
 *
 * This tab configures where the app's output windows go, and until recently none of it could be
 * tested at all: it enumerated displays through `GraphicsEnvironment` while composing, which throws
 * `HeadlessException` under the suite's headless JVM. It now takes that one call as the
 * [detectScreens] parameter, defaulting to the real AWT lookup, so tests can hand it a stand-in list
 * and drive everything built on top — slot allocation, the target and key-output menus, the
 * assignment grid, the content-outputs dialog. Only [detectScreensFromAwt] itself stays uncovered.
 *
 * The tab renders two quite different layouts depending on that list, and both matter:
 *
 *  * With **no external display** it falls back to a simulated "Dev Window" row and offers a
 *    stepper to simulate several outputs — the single-monitor development case.
 *  * With **external displays** it renders one "Screen N" row each, with that display's resolution
 *    in its target dropdown.
 *
 * The assignment grid publishes no tags and repeats the same four controls per row, so those are
 * addressed by ordinal through [gridButton]; `ProjectionSettingsTabStructureTest` pins the layout
 * those ordinals assume. Everything else is found by the text it displays.
 *
 * Never clicked: the "Browse" button next to the VLC path opens a **native** file chooser, which
 * would block the run.
 */
@OptIn(ExperimentalTestApi::class)
internal fun projectionTab(
    initial: AppSettings = AppSettings(),
    screens: List<DetectedScreen> = twoExternalScreens(),
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
            )
        }
    }
    awaitAudioDevices()
    block { current }
}

/** The audio device entry every machine has, and so the signal that VLC's probe has come back. */
internal const val SYSTEM_DEFAULT_DEVICE = "System Default"

/**
 * Waits for the audio device dropdown to appear.
 *
 * The card asks VLC for its device list on `Dispatchers.IO` — which is why the tab no longer stalls
 * composition on it, and also why `waitForIdle` does not cover it. This waits on the dropdown
 * itself: a positive signal that lands as soon as the probe returns, never on a timeout. Where VLC
 * is absent the card composes a message instead and there is nothing to wait for.
 */
internal fun ComposeUiTest.awaitAudioDevices() {
    if (!isVlcAvailable) return
    waitUntil { onAllNodesWithText(SYSTEM_DEFAULT_DEVICE).fetchSemanticsNodes(false).isNotEmpty() }
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

// ── Bible stack fixtures ────────────────────────────────────────────────────────────────────────

/**
 * Settings carrying a stack of [names]. `withTranslations` keeps the legacy primary/secondary names
 * in step, so the stack is the shape a real install would have rather than one assembled field by
 * field.
 *
 * No `storageDirectory` is set, so `Bible.readTranslationSummary` finds nothing to read and each
 * row falls back to naming itself by its file stem — which is also what an install whose .spb
 * carries no `##Title:` header does.
 */
internal fun withBibleStack(vararg names: String, projection: ProjectionSettings = ProjectionSettings()): AppSettings =
    AppSettings(
        bibleSettings = BibleSettings().withTranslations(names.map { BibleTranslationSettings(fileName = it) }),
        projectionSettings = projection,
    )

/** Three translations: enough for the per-output translation picker to list rows at all. */
internal fun threeTranslations(projection: ProjectionSettings = ProjectionSettings()): AppSettings =
    withBibleStack("ENG_KJV.spb", "RUS_SYN.spb", "DEU_LUT.spb", projection = projection)

// ── Translation picker locators ─────────────────────────────────────────────────────────────────

/**
 * The collapsed trigger that opens the translation picker.
 *
 * Addressed by tag: every caption this segment can show is derived from the current selection, and
 * a cleared trigger reads "None" — which is also what an unassigned target-display dropdown reads.
 */
internal fun ComposeUiTest.translationTrigger(): SemanticsNodeInteraction =
    onNodeWithTag(TranslationPickerTags.TRIGGER)

/** The open picker's master on/off row, which publishes a tri-state toggle. */
internal fun ComposeUiTest.translationMaster(): SemanticsNodeInteraction =
    onNodeWithTag(TranslationPickerTags.MASTER)

/** The picker row for stack position [index]. */
internal fun ComposeUiTest.translationRow(index: Int): SemanticsNodeInteraction =
    onNodeWithTag(TranslationPickerTags.row(index))

// ── Grid layout ─────────────────────────────────────────────────────────────────────────────────

/**
 * Where each control sits among the tab's labelled buttons, in composition order: the Identify
 * button, then four per assignment row, then the buttons below the grid.
 */
internal object Grid {
    const val IDENTIFY = 0
    const val CONTROLS_PER_ROW = 4

    /** The target-display dropdown for assignment row [row]. */
    fun targetDisplay(row: Int) = 1 + row * CONTROLS_PER_ROW

    /** The key-output dropdown for assignment row [row]. */
    fun keyOutput(row: Int) = 2 + row * CONTROLS_PER_ROW

    /** The display-mode dropdown for assignment row [row]. */
    fun displayMode(row: Int) = 3 + row * CONTROLS_PER_ROW

    /** The "N of M enabled" content-outputs button for assignment row [row]. */
    fun contentOutputs(row: Int) = 4 + row * CONTROLS_PER_ROW

    /** The first button after the grid, given [rows] assignment rows: "Add Output". */
    fun addOutput(rows: Int) = 1 + rows * CONTROLS_PER_ROW

    /**
     * How many labelled buttons follow the grid.
     *
     * "Add Output" and the VLC-path "Browse" button are always there. The audio-device dropdown
     * between them is not: the tab composes the whole device row only when VLC is present, and
     * shows an "install VLC" message in its place otherwise — the state on a CI runner.
     */
    val trailing: Int get() = if (isVlcAvailable) 3 else 2
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
    // before it lands is off by one. Waiting here covers the suites that compose the tab directly
    // rather than through [projectionTab]; it returns immediately once the probe has come back.
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
 * the fixture must leave [option] displayed nowhere else — asserted here rather than assumed.
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

// `numberFields`, `retypeNumberField` and `assertNumberFieldShows` are shared with the other
// settings-tab suites and live in `SongSettingsTabTestSupport.kt`; they drive the same
// `NumberSettingsTextField` composable and are reused here rather than duplicated.

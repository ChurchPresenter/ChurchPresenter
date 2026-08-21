@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import kotlin.test.assertEquals

/**
 * Harness and node locators shared by the `CompanionSatelliteSettingsTab` test classes.
 *
 * The tab is a **list of connection cards**, each carrying eight text boxes of its own plus three
 * placement blocks — Tab, Left sidebar, Right sidebar — that are four more boxes and a checkbox
 * apiece. One card is therefore twenty boxes that look identical in the tree, and a second connection
 * doubles that.
 *
 * Locating is by **caption**: every box sits in a `SettingRow` whose label is to its left, so
 * [boxFor] finds a box by the caption beside it, and [placementBox] narrows that to one of the three
 * placement blocks by the band of the screen it occupies. Nothing is addressed by bare ordinal except
 * the cards themselves, which are counted rather than named.
 *
 * Known gaps — what these tests do not reach, and why:
 *
 *  * **Connect / Disconnect** only render when a `CompanionSatelliteViewModel` is supplied, and
 *    pressing them opens a real socket to a Companion instance. `CompanionSatelliteSettingsTabViewModelTest`
 *    supplies a view model to prove the buttons appear and are wired; it does not press Connect.
 */
@OptIn(ExperimentalTestApi::class)
internal fun satelliteTab(
    initial: AppSettings = AppSettings(),
    viewModel: org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel? = null,
    block: ComposeUiTest.(get: () -> AppSettings) -> Unit,
) = runComposeUiTest {
    var current = initial
    setContent {
        MaterialTheme {
            var state by remember { mutableStateOf(current) }
            CompanionSatelliteSettingsTab(
                settings = state,
                onSettingsChange = { transform -> state = transform(state); current = state },
                viewModel = viewModel,
            )
        }
    }
    block { current }
}

/** Settings holding exactly the given connections. */
internal fun satelliteSettings(vararg connections: CompanionSatelliteSettings): AppSettings =
    AppSettings().copy(companionSatelliteConnections = connections.toList())

/** One connection, [change] applied to the defaults. */
internal fun connection(change: CompanionSatelliteSettings.() -> CompanionSatelliteSettings = { this }) =
    CompanionSatelliteSettings().change()

/** The tab's single connection, for the common one-card case. */
internal fun AppSettings.onlyConnection(): CompanionSatelliteSettings =
    companionSatelliteConnections.single()

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object SatLabel {
    const val NAME = "Name"
    const val HOST = "Host"
    const val TAB_DEVICE_ID = "Tab device ID"
    const val LEFT_DEVICE_ID = "Left sidebar device ID"
    const val RIGHT_DEVICE_ID = "Right sidebar device ID"
    const val PRODUCT_NAME = "Product name"
    const val RECONNECT_DELAY = "Reconnect delay (ms)"
    // The placement grid boxes caption themselves, and SettingsTextField uppercases those labels —
    // unlike the connection-level rows above, whose captions come from SettingRow as written.
    const val ROWS = "ROWS"
    const val COLUMNS = "COLUMNS"
    const val BITMAP_SIZE = "BITMAP SIZE (PX)"
    const val MAX_BUTTON_SIZE = "MAX BUTTON SIZE (DP)"
    const val AUTOCONNECT = "Connect automatically on launch"
    const val ADD = "+ Add Connection"
    const val REMOVE = "Remove Connection"
    const val CONNECT = "Connect"
    const val DISCONNECT = "Disconnect"
    const val CONNECTING = "Connecting…"
    const val DISCONNECTED = "Disconnected"
    const val SHOW_IN_TAB = "Tab"
    const val LEFT_SIDEBAR = "Left sidebar"
    const val RIGHT_SIDEBAR = "Right sidebar"
}

/** The three placement blocks, in the order the card lays them out. */
internal enum class Placement(val caption: String) {
    TAB(SatLabel.SHOW_IN_TAB),
    LEFT(SatLabel.LEFT_SIDEBAR),
    RIGHT(SatLabel.RIGHT_SIDEBAR),
}

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

internal fun ComposeUiTest.satelliteBoxes(): SemanticsNodeInteractionCollection = onAllNodes(hasSetTextAction())

internal fun ComposeUiTest.satelliteToggles(): SemanticsNodeInteractionCollection = onAllNodes(isToggleable())

/** Every card's Remove button; there is one per connection. */
internal fun ComposeUiTest.removeButtons(): SemanticsNodeInteractionCollection =
    onAllNodes(hasText(SatLabel.REMOVE))

/**
 * The box captioned [caption], counting from the top.
 *
 * `SettingRow` puts its label to the left of the control on the same row, so a box is identified by
 * the caption it sits beside. [ordinal] picks between cards when more than one connection is on
 * screen: the cards are laid out top to bottom, so ordinal 0 is the first card's.
 */
internal fun ComposeUiTest.boxFor(caption: String, ordinal: Int = 0): SemanticsNodeInteraction {
    val captions = onAllNodes(hasText(caption) and !hasSetTextAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .sortedWith(compareBy({ it.top }, { it.left }))
    check(ordinal in captions.indices) {
        "wanted the box beside caption #$ordinal \"$caption\" but only ${captions.size} exist"
    }
    val caption0 = captions[ordinal]
    val box = satelliteBoxes().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter {
            it.boundsInRoot.left >= caption0.right &&
                it.boundsInRoot.top < caption0.bottom &&
                it.boundsInRoot.bottom > caption0.top
        }
        .minByOrNull { it.boundsInRoot.left }
        ?: error("no box beside \"$caption\" #$ordinal")
    return onAllNodes(SemanticsMatcher("bounds == ${box.boundsInRoot}") { it.boundsInRoot == box.boundsInRoot })[0]
}

/**
 * A box inside one of the three placement blocks.
 *
 * These are laid out differently from the connection rows above: the caption sits **above** its box
 * rather than to its left, because the label belongs to the field itself rather than to a
 * `SettingRow`. All three blocks caption their boxes identically, so the caption alone is ambiguous;
 * the blocks run down the card in [Placement] order, which is what the ordinal resolves.
 *
 * The boxes only exist while the placement is ticked — the block hides them behind `if (checked)` —
 * so a fixture must switch the placement on before this can find anything.
 */
internal fun ComposeUiTest.placementBox(
    placement: Placement,
    caption: String,
    card: Int = 0,
): SemanticsNodeInteraction {
    val ordinal = card * Placement.entries.size + placement.ordinal
    val captions = onAllNodes(hasText(caption) and !hasSetTextAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .sortedWith(compareBy({ it.top }, { it.left }))
    check(ordinal in captions.indices) {
        "wanted \"$caption\" #$ordinal but only ${captions.size} are on screen — is the placement switched on?"
    }
    val caption0 = captions[ordinal]
    val box = satelliteBoxes().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .filter { it.boundsInRoot.top >= caption0.top && kotlin.math.abs(it.boundsInRoot.left - caption0.left) < 4f }
        .minByOrNull { it.boundsInRoot.top }
        ?: error("no box under \"$caption\" #$ordinal")
    return onAllNodes(SemanticsMatcher("bounds == ${box.boundsInRoot}") { it.boundsInRoot == box.boundsInRoot })[0]
}

/** The checkbox that turns [placement] on, for the [card]-th connection. */
internal fun ComposeUiTest.placementCheckbox(placement: Placement, card: Int = 0): SemanticsNodeInteraction =
    satelliteToggles()[card * (Placement.entries.size + 1) + placement.ordinal]

/** The auto-connect switch, which follows the three checkboxes in each card. */
internal fun ComposeUiTest.autoConnectSwitch(card: Int = 0): SemanticsNodeInteraction =
    satelliteToggles()[card * (Placement.entries.size + 1) + Placement.entries.size]

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

internal fun ComposeUiTest.typeInto(caption: String, text: String, ordinal: Int = 0) {
    boxFor(caption, ordinal).performTextReplacement(text)
    waitForIdle()
}

internal fun ComposeUiTest.typeIntoPlacement(
    placement: Placement,
    caption: String,
    text: String,
    card: Int = 0,
) {
    placementBox(placement, caption, card).performTextReplacement(text)
    waitForIdle()
}

/** Asserts some box on the tab is displaying [value]. */
internal fun ComposeUiTest.assertSatelliteBoxShows(value: String, what: String) {
    val shown = satelliteBoxes().fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
    assertEquals(true, value in shown, "$what must display \"$value\" — boxes show $shown")
}

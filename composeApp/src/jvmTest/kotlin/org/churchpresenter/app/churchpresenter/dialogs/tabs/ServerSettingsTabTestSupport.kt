@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ServerSettings
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.assertEquals

/**
 * Harness and node locators shared by the `ServerSettingsTab` test classes.
 *
 * Two things make this tab different from the other settings tabs, and both shape everything here.
 *
 * **It owns real collaborators.** `RemoteClientManager` reads and writes
 * `~/.churchpresenter/remote_clients.json` *in its field initialiser*, so every test gets its own
 * `user.home` ([withIsolatedHome]) and constructs the manager inside it — otherwise tests would read
 * the developer's real allow/block lists and leak state into each other. `CompanionServer` binds a
 * real socket.
 *
 * **Half the tab is gated on the server actually running.** The Restart button, the Server URL row,
 * the QR button and the lower-third trigger URLs only compose when `isRunning` is true, and that
 * flag is owned by the server rather than by the settings — no fixture can fake it. Starting one
 * costs ~600 ms and stopping it ~1 s, far past the per-test budget, so `ServerSettingsTabRunningTest`
 * starts **one** server for the whole class and every other class uses a stopped one. That is why
 * the running-state tests never click the enable switch or Restart: those would stop the shared
 * server out from under the rest of the class.
 *
 * Known gaps — what these tests do not reach, and why:
 *
 *  * **The QR dialog** is a `DialogWindow` — a real AWT window — which throws `HeadlessException`
 *    under the suite's headless JVM. The button that opens it is asserted; it is never clicked.
 *  * **The Copy buttons** put text on `java.awt.Toolkit`'s system clipboard. Reading it back would
 *    assert on the developer's clipboard, and writing to it from a test is a side effect on the
 *    machine, so the buttons' presence and enablement are asserted rather than their effect.
 *  * **The enable switch and Restart** genuinely start and stop a server. They are driven once each,
 *    in `ServerSettingsTabLifecycleTest`, against a server of their own — not the shared one.
 */

/**
 * Forces the two `user.home`-latching singletons to resolve against the **real** home, once, before
 * any swap below installs a temporary one.
 *
 * This is not defensive: the tab calls `companionServer.updateFileUploadEnabled(...)` from a
 * `LaunchedEffect` on every composition, and that logs — so without this the very first test here
 * would latch `InstanceLinkLogger`'s log directory to a temporary home, which is then deleted, and
 * every later test in the JVM that expects to find that log would fail. `InstanceLinkLoggerTest` is
 * exactly such a test, and it did.
 *
 * `CrashReporter` resolves its paths in field initialisers, so touching the object is enough.
 * `InstanceLinkLogger` resolves its directory in a `by lazy`, which class initialisation does *not*
 * run — only an actual `log` call does, which is why one is made here. It appends a single line to
 * the real log; `InstanceLinkLoggerTest` measures deltas and filters by event name, so a stray line
 * under an event no one queries is harmless.
 */
private val homeLatchingSingletonsPrewarmed: Boolean by lazy {
    CrashReporter.hashCode()
    InstanceLinkLogger.log(InstanceLinkLogSide.PRIMARY, "unit_server_tab_prewarm")
    true
}

/**
 * Runs [block] with `user.home` pointed at a fresh temporary directory, restoring it afterwards
 * whatever happens. Anything the tab persists lands there and is deleted with it.
 */
internal fun <T> withIsolatedHome(block: (home: File) -> T): T {
    check(homeLatchingSingletonsPrewarmed)
    val home = Files.createTempDirectory("cp-server-tab").toFile()
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.absolutePath)
    return try {
        block(home)
    } finally {
        System.setProperty("user.home", previous)
        home.deleteRecursively()
    }
}

/** A free port, for the one class that needs a real server. */
internal fun freeServerPort(): Int = ServerSocket(0).use { it.localPort }

/**
 * Renders the tab over an isolated home with [server] and a freshly loaded [RemoteClientManager],
 * which [seedClients] may populate before the first composition.
 */
@OptIn(ExperimentalTestApi::class)
internal fun serverTab(
    initial: AppSettings = AppSettings(),
    server: CompanionServer? = null,
    seedClients: RemoteClientManager.() -> Unit = {},
    block: ComposeUiTest.(get: () -> AppSettings, clients: RemoteClientManager) -> Unit,
) = withIsolatedHome {
    val companion = server ?: CompanionServer()
    val clients = RemoteClientManager().apply(seedClients)
    var current = initial
    runComposeUiTest {
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                ServerSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                    companionServer = companion,
                    remoteClientManager = clients,
                )
            }
        }
        awaitFolderScan()
        // The trigger-URL card only renders while the server is up; with it stopped there are no
        // rows to wait for and waiting would simply time out.
        if (companion.isRunning.value) awaitLowerThirdRows(current)
        block({ current }, clients)
    }
}

/** Settings whose server section is [change] applied to the defaults. */
internal fun serverSettings(change: ServerSettings.() -> ServerSettings): AppSettings =
    AppSettings().let { it.copy(serverSettings = it.serverSettings.change()) }

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object ServerLabel {
    const val SECTION_SERVER = "Companion Server"
    const val SECTION_CLIENTS = "Remote Clients"
    const val SECTION_TRIGGERS = "Lower third triggers"
    const val ENABLE = "Enable Server"
    const val RUNNING = "Running"
    const val STOPPED = "Stopped"
    const val PORT = "Port"
    const val HOST = "Host Override"
    const val SERVER_URL = "Server URL"
    const val API_KEY_PROTECTION = "API Key Protection"
    const val API_KEY = "API Key"
    const val ALLOW_UPLOAD = "Allow File Upload"
    const val MAX_UPLOAD = "Max media upload (MB)"
    const val ALLOWED = "✓  Allowed Clients"
    const val BLOCKED = "⛔  Blocked Clients"
    const val NO_TRIGGERS_SERVER_OFF = "Start the server to get trigger URLs"
    const val RESTART = "Restart"
    const val QR = "QR"
    const val REMOVE = "Remove"
    const val SET_LABEL = "Set friendly name"
    const val NO_ALLOWED = "No permanently allowed clients."
    const val NO_BLOCKED = "No permanently blocked clients."
    const val NO_LOWER_THIRDS = "No lower thirds found in the configured folder"
}

// ── Locators ────────────────────────────────────────────────────────────────────────────────────

/**
 * The tab's `Switch`es, in composition order: Enable Server, API Key Protection, Allow File Upload.
 * They carry no label of their own — `SettingRow` and the enable row put the caption in a sibling
 * `Text` — so they are addressed by ordinal, which `ServerSettingsTabStructureTest` pins.
 */
internal object Switch {
    const val ENABLE = 0
    const val API_KEY = 1
    const val FILE_UPLOAD = 2
    const val COUNT = 3
}

internal fun ComposeUiTest.serverSwitches(): SemanticsNodeInteractionCollection =
    onAllNodes(
        SemanticsMatcher.expectValue(
            SemanticsProperties.Role,
            Role.Switch,
        ),
    )

internal fun ComposeUiTest.serverSwitch(ordinal: Int): SemanticsNodeInteraction = serverSwitches()[ordinal]

/** Every editable text field on the tab — port, host, API key, max upload and the read-only URL. */
internal fun ComposeUiTest.serverTextFields(): SemanticsNodeInteractionCollection =
    onAllNodes(hasSetTextAction())

/** The field currently displaying [value], whatever kind it is. */
internal fun ComposeUiTest.fieldShowing(value: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasText(value))

/** A labelled button, e.g. `serverButton(ServerLabel.RESTART)`. */
internal fun ComposeUiTest.serverButton(label: String): SemanticsNodeInteraction =
    onNode(hasClickAction() and hasText(label))

// ── Actions ─────────────────────────────────────────────────────────────────────────────────────

/** Replaces the text of the field currently showing [showing] with [to]. */
internal fun ComposeUiTest.retypeField(showing: String, to: String) {
    fieldShowing(showing).performTextReplacement(to)
    waitForIdle()
}

/**
 * Asserts some text field on the tab is displaying [value].
 *
 * Deliberately does **not** go through [serverTextFields]: a field that is disabled (the port and
 * host while the server runs) or read-only (the Server URL) publishes no set-text action at all, so
 * matching on that action would miss exactly the fields the running-state tests care about. The
 * displayed text is what is searched instead.
 */
internal fun ComposeUiTest.assertFieldShows(value: String, what: String) {
    val shown = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }
    assertEquals(true, value in shown, "$what must display \"$value\" — fields show $shown")
}

/** Any text box displaying [value], whether or not it is editable. */
internal fun ComposeUiTest.boxShowing(value: String): SemanticsNodeInteraction =
    onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasText(value))

/** How many times [text] is rendered. */
internal fun ComposeUiTest.countOf(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

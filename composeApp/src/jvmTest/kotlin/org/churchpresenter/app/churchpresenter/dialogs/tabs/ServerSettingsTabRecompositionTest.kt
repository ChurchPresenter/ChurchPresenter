@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.RemoteClientManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ServerSettings
import org.churchpresenter.companionserver.CompanionServer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the tab from its **input** rather than from its controls: the settings object is replaced
 * from outside and the rendered tab must follow.
 *
 * This is the direction the behaviour tests cannot cover. The port, host and API key boxes each keep
 * a `remember(...)`-keyed copy of their setting, so a box showing what was typed proves only that it
 * echoed a keystroke. Here nothing is typed — every assertion is about a value the tab was handed,
 * which is what says those `remember` keys actually re-seed when the settings change underneath.
 *
 * It also covers the path a settings import or an Instance Link update takes, and the client rows'
 * own recomposition: `ClientRow` takes eight parameters and is rebuilt whenever the list or a label
 * changes.
 */
class ServerSettingsTabRecompositionTest {

    /** Renders the tab over a settings object [block] can swap out, inside an isolated home. */
    private fun rerenderable(
        initial: AppSettings = AppSettings(),
        seedClients: RemoteClientManager.() -> Unit = {},
        block: ComposeUiTest.(
            set: (ServerSettings.() -> ServerSettings) -> Unit,
            clients: RemoteClientManager,
        ) -> Unit,
    ) = withIsolatedHome {
        val server = CompanionServer()
        val clients = RemoteClientManager().apply(seedClients)
        runComposeUiTest {
            var state by mutableStateOf(initial)
            setContent {
                MaterialTheme {
                    ServerSettingsTab(
                        settings = state,
                        onSettingsChange = { transform -> state = transform(state) },
                        companionServer = server,
                        remoteClientManager = clients,
                    )
                }
            }
            block({ change ->
                state = state.copy(serverSettings = state.serverSettings.change())
                waitForIdle()
            }, clients)
        }
    }

    /**
     * Seeds clients on purpose: with empty lists no `ClientRow` composes at all, so a re-render
     * would say nothing about the rows — which are the part of this tab most likely to be disturbed
     * by one, since each takes eight parameters and is rebuilt from the list every time.
     */
    @Test
    fun `the tab survives a recomposition that changes none of its inputs`() {
        rerenderable(
            seedClients = {
                allowPermanently("alpha")
                blockPermanently("zulu")
                setLabel("alpha", "Booth iPad")
            },
        ) { set, _ ->
            onNodeWithText(ServerLabel.SECTION_SERVER).assertExists()
            serverSwitches().assertCountEquals(Switch.COUNT)
            onNodeWithText("Booth iPad").assertExists()

            set { this }

            onNodeWithText(ServerLabel.SECTION_SERVER).assertExists()
            serverSwitches().assertCountEquals(Switch.COUNT)
            onNodeWithText(ServerLabel.SECTION_CLIENTS).assertExists()
            onNodeWithText("Booth iPad").assertExists("the rows must survive a no-op re-render")
            onNodeWithText("alpha").assertExists()
            onNodeWithText("zulu").assertExists()
        }
    }

    /**
     * A setting far away from the client lists changes, so every row is called again with exactly the
     * parameters it already had. The rows must be left standing — this is the path where a row is
     * offered the chance to skip rather than rebuild.
     */
    @Test
    fun `changing an unrelated setting leaves the client rows untouched`() {
        rerenderable(
            seedClients = {
                allowPermanently("alpha")
                allowPermanently("mike")
                blockPermanently("zulu")
                setLabel("mike", "Middle One")
            },
        ) { set, clients ->
            onNodeWithText("Middle One").assertExists()

            set { copy(port = 9500) }
            assertFieldShows("9500", "the port after the change")

            set { copy(apiKeyEnabled = true, fileUploadEnabled = true) }

            onNodeWithText("Middle One").assertExists("the label must survive unrelated churn")
            for (id in listOf("alpha", "mike", "zulu")) onNodeWithText(id).assertExists()
            assertEquals(setOf("alpha", "mike"), clients.allowedClients, "and nothing may have moved list")
            assertEquals(setOf("zulu"), clients.blockedClients)
        }
    }

    /** The port box is `remember`-keyed on the stored port, so it must re-seed when that changes. */
    @Test
    fun `a stored port change reaches its box without any interaction`() = rerenderable { set, _ ->
        assertFieldShows("8765", "the port box out of the box")

        set { copy(port = 9200) }

        assertFieldShows("9200", "the port box after the settings changed")
    }

    @Test
    fun `a stored host change reaches its box without any interaction`() = rerenderable { set, _ ->
        set { copy(serverHost = "imported.local") }
        assertFieldShows("imported.local", "the host box after the settings changed")
    }

    @Test
    fun `a stored API key change reaches its box without any interaction`() {
        rerenderable(initial = serverSettings { copy(apiKeyEnabled = true, serverHost = "host.local") }) { set, _ ->
            set { copy(apiKey = "imported-key") }
            assertFieldShows("imported-key", "the key box after the settings changed")
        }
    }

    @Test
    fun `stored switch flags reach their switches without any interaction`() = rerenderable { set, _ ->
        serverSwitch(Switch.API_KEY).assertIsOff()
        serverSwitch(Switch.FILE_UPLOAD).assertIsOff()

        set { copy(apiKeyEnabled = true, fileUploadEnabled = true) }

        serverSwitch(Switch.API_KEY).assertIsOn()
        serverSwitch(Switch.FILE_UPLOAD).assertIsOn()
        onNodeWithText(ServerLabel.API_KEY).assertExists("and the key row must appear with it")
        onNodeWithText(ServerLabel.MAX_UPLOAD).assertExists("as must the upload cap")
    }

    @Test
    fun `a stored upload cap change reaches its box without any interaction`() {
        rerenderable(initial = serverSettings { copy(fileUploadEnabled = true) }) { set, _ ->
            set { copy(maxMediaUploadMb = 777) }
            assertFieldShows("777", "the cap box after the settings changed")
        }
    }

    /**
     * A whole-object replacement, as a settings import performs — every box and switch changes at
     * once and each must pick up its own value rather than a neighbour's.
     */
    @Test
    fun `replacing every setting at once repaints the whole card`() = rerenderable { set, _ ->
        set {
            copy(
                port = 9300,
                serverHost = "all-at-once.local",
                apiKeyEnabled = true,
                apiKey = "all-at-once-key",
                fileUploadEnabled = true,
                maxMediaUploadMb = 321,
            )
        }

        assertFieldShows("9300", "the port")
        assertFieldShows("all-at-once.local", "the host")
        assertFieldShows("all-at-once-key", "the key")
        assertFieldShows("321", "the cap")
        serverSwitch(Switch.API_KEY).assertIsOn()
        serverSwitch(Switch.FILE_UPLOAD).assertIsOn()
    }

    // ── The client rows ─────────────────────────────────────────────────────────────────────────

    /**
     * `ClientRow` is rebuilt whenever the list it belongs to changes. Removing one row must leave the
     * others rendering their own client rather than shifting onto a neighbour's.
     */
    @Test
    fun `removing a row re-renders the remaining ones against their own clients`() {
        rerenderable(
            seedClients = {
                allowPermanently("alpha")
                allowPermanently("mike")
                allowPermanently("zulu")
                setLabel("mike", "Middle One")
            },
        ) { _, clients ->
            onNodeWithText("Middle One").assertExists("fixture: the middle row is labelled")

            // Remove "alpha", the first row.
            onAllNodes(hasClickAction() and hasText(ServerLabel.REMOVE))[0].performScrollTo().performClick()
            waitForIdle()

            assertEquals(setOf("mike", "zulu"), clients.allowedClients, "only alpha may be gone")
            onNodeWithText("Middle One").assertExists("mike must keep its own label after the shift")
            onNodeWithText("zulu").assertExists()
            onNodeWithText("alpha").assertDoesNotExist()
        }
    }

    /** A label committed on one row must not leak onto the row that takes its place. */
    @Test
    fun `a label survives its row being re-rendered`() {
        rerenderable(
            initial = serverSettings { copy(serverHost = "host.local") },
            seedClients = {
                allowPermanently("alpha")
                allowPermanently("zulu")
            },
        ) { _, clients ->
            onAllNodes(hasClickAction() and hasText(ServerLabel.REMOVE))[0].performScrollTo().performClick()
            waitForIdle()
            assertEquals(setOf("zulu"), clients.allowedClients)

            onNodeWithContentDescription(ServerLabel.SET_LABEL).performScrollTo().performClick()
            waitForIdle()
            retypeField("", "Named After The Shift")
            onNodeWithContentDescription("Save").performClick()
            waitForIdle()

            assertEquals("Named After The Shift", clients.getLabel("zulu"), "the surviving row must take it")
            assertEquals("", clients.getLabel("alpha"), "and the removed one must not")
        }
    }

    /**
     * A label is committed on one row while both rows stay put, so only that row's `label` changes
     * and everything else it was given is identical. The other row must not follow it.
     */
    @Test
    fun `labelling a row in place leaves its neighbour's row alone`() {
        rerenderable(
            initial = serverSettings { copy(serverHost = "host.local") },
            seedClients = {
                allowPermanently("alpha")
                allowPermanently("zulu")
                setLabel("zulu", "Already Named")
            },
        ) { _, clients ->
            onNodeWithText("Already Named").assertExists("fixture: the second row is labelled")

            onAllNodes(hasClickAction() and hasText(ServerLabel.REMOVE)) // sanity: two rows
                .assertCountEquals(2)
            onAllNodesWithContentDescription(ServerLabel.SET_LABEL)[0].performScrollTo().performClick()
            waitForIdle()
            retypeField("", "Newly Named")
            onNodeWithContentDescription("Save").performClick()
            waitForIdle()

            assertEquals("Newly Named", clients.getLabel("alpha"), "the edited row must take the name")
            assertEquals("Already Named", clients.getLabel("zulu"), "and its neighbour must keep its own")
            onNodeWithText("Newly Named").assertExists()
            onNodeWithText("Already Named").assertExists()
        }
    }

    // ── Callback identity ───────────────────────────────────────────────────────────────────────

    /**
     * The parent hands the tab a **new `onSettingsChange` instance** on each recomposition, as
     * `OptionsDialog` does. The tab must use the callback it was last given: one that skipped the
     * update would keep writing into a callback the parent has already replaced, and the write would
     * appear to succeed while reaching nothing.
     */
    @Test
    fun `a click reaches the newest callback when the parent keeps replacing it`() = withIsolatedHome {
        val server = CompanionServer()
        val clients = RemoteClientManager()
        runComposeUiTest {
            var settings by mutableStateOf(AppSettings())
            var generation by mutableStateOf(0)
            var calledGeneration = -1

            setContent {
                MaterialTheme {
                    val thisGeneration = generation
                    ServerSettingsTab(
                        settings = settings,
                        onSettingsChange = { transform ->
                            calledGeneration = thisGeneration
                            settings = transform(settings)
                        },
                        companionServer = server,
                        remoteClientManager = clients,
                    )
                }
            }

            serverSwitch(Switch.API_KEY).performScrollTo().performClick()
            waitForIdle()
            assertEquals(0, calledGeneration, "the first callback must be the one invoked")
            assertEquals(true, settings.serverSettings.apiKeyEnabled, "and its write must land")

            generation = 1
            waitForIdle()

            serverSwitch(Switch.FILE_UPLOAD).performScrollTo().performClick()
            waitForIdle()
            assertEquals(1, calledGeneration, "the replacement callback must be invoked, not the stale one")
            assertEquals(true, settings.serverSettings.fileUploadEnabled, "and its write must land too")
        }
    }

    /**
     * The same round trip reached through [ServerHost] — the shape `OptionsDialog` uses, where the
     * parent forwards its own parameters, so the tab's skip decision comes from the caller's change
     * flags rather than from comparing values itself.
     */
    @Test
    fun `the tab tracks its inputs when reached through a parent that forwards them`() = withIsolatedHome {
        val server = CompanionServer()
        val clients = RemoteClientManager()
        runComposeUiTest {
            var settings by mutableStateOf(AppSettings())
            setContent {
                MaterialTheme {
                    ServerHost(
                        settings = settings,
                        onSettingsChange = { transform -> settings = transform(settings) },
                        companionServer = server,
                        remoteClientManager = clients,
                    )
                }
            }

            assertFieldShows("8765", "the port out of the box")
            settings = settings.copy(serverSettings = settings.serverSettings.copy(port = 9400))
            waitForIdle()
            assertFieldShows("9400", "the port after the parent changed it")

            serverSwitch(Switch.FILE_UPLOAD).performScrollTo().performClick()
            waitForIdle()
            assertEquals(
                true,
                settings.serverSettings.fileUploadEnabled,
                "a click must reach the parent's callback",
            )
        }
    }
}

/**
 * Stands in for `OptionsDialog`: a composable that takes the tab's inputs as its own parameters and
 * forwards them straight through, so the compiler propagates its caller's change flags into the tab.
 */
@Composable
private fun ServerHost(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    companionServer: CompanionServer,
    remoteClientManager: RemoteClientManager,
) {
    ServerSettingsTab(
        settings = settings,
        onSettingsChange = onSettingsChange,
        companionServer = companionServer,
        remoteClientManager = remoteClientManager,
    )
}

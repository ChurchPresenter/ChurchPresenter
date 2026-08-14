@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the Remote Clients card: the allow and block lists, the friendly-name editor and Remove.
 *
 * These rows are the one part of the tab whose state lives outside the settings object —
 * `RemoteClientManager` owns it and persists it to `~/.churchpresenter/remote_clients.json`. Every
 * test therefore asserts against the *manager* rather than against `AppSettings`, and runs inside its
 * own temporary home so nothing it writes escapes or leaks into the next test.
 *
 * The two lists render from the same composable with different callbacks, so each test that removes
 * or renames also checks the other list was left alone — a Remove wired to the wrong list would
 * otherwise look exactly right.
 */
class ServerSettingsTabClientsTest {

    // ── Empty state ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `both lists say so when they are empty`() = serverTab { _, clients ->
        assertTrue(clients.allowedClients.isEmpty(), "fixture: nothing allowed")
        assertTrue(clients.blockedClients.isEmpty(), "fixture: nothing blocked")
        onNodeWithText(ServerLabel.NO_ALLOWED)
            .assertExists("the allowed list must say it is empty")
        onNodeWithText(ServerLabel.NO_BLOCKED)
            .assertExists("the blocked list must say it is empty")
    }

    // ── Rendering the lists ─────────────────────────────────────────────────────────────────────

    @Test
    fun `an allowed client is listed by its id`() {
        serverTab(seedClients = { allowPermanently("ipad-in-the-booth") }) { _, _ ->
            onNodeWithText("ipad-in-the-booth").assertExists("the client id must be shown")
            onNodeWithText(ServerLabel.NO_ALLOWED)
                .assertDoesNotExist()
        }
    }

    @Test
    fun `a labelled client shows its friendly name as well as its id`() {
        serverTab(
            seedClients = {
                allowPermanently("ipad-in-the-booth")
                setLabel("ipad-in-the-booth", "Booth iPad")
            },
        ) { _, _ ->
            onNodeWithText("Booth iPad").assertExists("the friendly name must be shown")
            onNodeWithText("ipad-in-the-booth").assertExists("alongside the id it belongs to")
        }
    }

    @Test
    fun `allowed and blocked clients are listed under their own headings`() {
        serverTab(
            seedClients = {
                allowPermanently("allowed-one")
                blockPermanently("blocked-one")
            },
        ) { _, _ ->
            onNodeWithText("allowed-one").assertExists()
            onNodeWithText("blocked-one").assertExists()
            // One row each, so one Remove button each.
            onAllNodes(hasClickAction() and hasText(ServerLabel.REMOVE)).assertCountEquals(2)
            onAllNodesWithContentDescription(ServerLabel.SET_LABEL).assertCountEquals(2)
        }
    }

    @Test
    fun `every client in a list gets its own row`() {
        serverTab(
            seedClients = {
                allowPermanently("one")
                allowPermanently("two")
                allowPermanently("three")
            },
        ) { _, _ ->
            for (id in listOf("one", "two", "three")) onNodeWithText(id).assertExists()
            onAllNodes(hasClickAction() and hasText(ServerLabel.REMOVE)).assertCountEquals(3)
        }
    }

    /** The lists are sorted, so the rows are in a stable order whatever order they were added in. */
    @Test
    fun `clients are listed in sorted order`() {
        serverTab(
            seedClients = {
                allowPermanently("zulu")
                allowPermanently("alpha")
                allowPermanently("mike")
            },
        ) { _, _ ->
            val tops = listOf("alpha", "mike", "zulu").map {
                onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top
            }
            assertEquals(tops.sorted(), tops, "the rows must be laid out in sorted order")
        }
    }

    // ── Remove ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Remove takes a client off the allowed list`() {
        serverTab(seedClients = { allowPermanently("going-away") }) { _, clients ->
            assertTrue(clients.isAllowed("going-away"), "fixture: it starts allowed")

            onNode(hasClickAction() and hasText(ServerLabel.REMOVE)).performScrollTo().performClick()
            waitForIdle()

            assertTrue(!clients.isAllowed("going-away"), "Remove must take it off the list")
            onNodeWithText("going-away").assertDoesNotExist()
            onNodeWithText(ServerLabel.NO_ALLOWED)
                .assertExists("and the empty message must come back")
        }
    }

    @Test
    fun `Remove takes a client off the blocked list`() {
        serverTab(seedClients = { blockPermanently("blocked-one") }) { _, clients ->
            onNode(hasClickAction() and hasText(ServerLabel.REMOVE)).performScrollTo().performClick()
            waitForIdle()

            assertTrue(!clients.isBlocked("blocked-one"), "Remove must take it off the blocked list")
            onNodeWithText(ServerLabel.NO_BLOCKED)
                .assertExists("and the empty message must come back")
        }
    }

    /**
     * The failure this guards against: a Remove wired to the wrong list. With one client in each,
     * removing the allowed one must leave the blocked one exactly where it was.
     */
    @Test
    fun `removing from one list leaves the other alone`() {
        serverTab(
            seedClients = {
                allowPermanently("allowed-one")
                blockPermanently("blocked-one")
            },
        ) { _, clients ->
            // The allowed list composes first, so its Remove is the first of the two.
            onAllNodes(hasClickAction() and hasText(ServerLabel.REMOVE))[0].performScrollTo().performClick()
            waitForIdle()

            assertTrue(!clients.isAllowed("allowed-one"), "the allowed client must be gone")
            assertTrue(clients.isBlocked("blocked-one"), "the blocked client must remain")
            onNodeWithText("blocked-one").assertExists("and still be on screen")
        }
    }

    @Test
    fun `removing one of several leaves the rest`() {
        serverTab(
            seedClients = {
                allowPermanently("alpha")
                allowPermanently("mike")
                allowPermanently("zulu")
            },
        ) { _, clients ->
            // Sorted, so the first row is "alpha".
            onAllNodes(hasClickAction() and hasText(ServerLabel.REMOVE))[0].performScrollTo().performClick()
            waitForIdle()

            assertTrue(!clients.isAllowed("alpha"), "the first row's client must be the one removed")
            assertTrue(clients.isAllowed("mike") && clients.isAllowed("zulu"), "the others must remain")
            onAllNodes(hasClickAction() and hasText(ServerLabel.REMOVE)).assertCountEquals(2)
        }
    }

    // ── Friendly names ──────────────────────────────────────────────────────────────────────────

    /**
     * The pencil opens an inline editor in place of the row's text. What it commits is asserted
     * against the manager, since that — not the settings object — is where labels live.
     */
    /**
     * The host override is filled in by the fixture throughout this section: it is the tab's other
     * blank text field, and the inline name editor starts blank too, so without it the editor cannot
     * be told apart from the host box.
     */
    @Test
    fun `the pencil opens an editor and the typed name is stored`() {
        serverTab(
            initial = serverSettings { copy(serverHost = "host.local") },
            seedClients = { allowPermanently("bare-client") },
        ) { _, clients ->
            assertEquals("", clients.getLabel("bare-client"), "no label to start with")

            onAllNodesWithContentDescription(ServerLabel.SET_LABEL)[0].performScrollTo().performClick()
            waitForIdle()

            retypeField("", "Front of House")
            assertEquals("", clients.getLabel("bare-client"), "typing alone must not commit anything")

            onNodeWithContentDescription("Save").performClick()
            waitForIdle()
            assertEquals(
                "Front of House",
                clients.getLabel("bare-client"),
                "Save must commit the typed name to the client manager",
            )
            onNodeWithText("Front of House").assertExists("and the row must show it")
        }
    }

    /** The pencil is a toggle: clicking it again closes the editor without committing. */
    @Test
    fun `the pencil closes the editor when it is already open`() {
        serverTab(
            initial = serverSettings { copy(serverHost = "host.local") },
            seedClients = { allowPermanently("bare-client") },
        ) { _, clients ->
            onAllNodesWithContentDescription(ServerLabel.SET_LABEL)[0].performScrollTo().performClick()
            waitForIdle()
            onNodeWithContentDescription("Save").assertExists("the editor must be open")

            onAllNodesWithContentDescription(ServerLabel.SET_LABEL)[0].performClick()
            waitForIdle()

            onNodeWithContentDescription("Save").assertDoesNotExist()
            assertEquals("", clients.getLabel("bare-client"), "and nothing may have been committed")
        }
    }

    /** Cancel closes the editor and leaves the stored name exactly as it was. */
    @Test
    fun `Cancel abandons a typed name`() {
        serverTab(
            initial = serverSettings { copy(serverHost = "host.local") },
            seedClients = {
                allowPermanently("bare-client")
                setLabel("bare-client", "Original")
            },
        ) { _, clients ->
            onAllNodesWithContentDescription(ServerLabel.SET_LABEL)[0].performScrollTo().performClick()
            waitForIdle()
            retypeField("Original", "Typed But Abandoned")

            onNodeWithContentDescription("Cancel").performClick()
            waitForIdle()

            assertEquals("Original", clients.getLabel("bare-client"), "Cancel must not commit")
            onNodeWithText("Original").assertExists("and the row must still show the old name")
            onNodeWithText("Typed But Abandoned").assertDoesNotExist()
        }
    }

    @Test
    fun `a stored label survives a fresh render`() {
        serverTab(
            seedClients = {
                allowPermanently("bare-client")
                setLabel("bare-client", "Stage Left")
            },
        ) { _, _ ->
            onNodeWithText("Stage Left").assertExists("the stored label must be rendered")
        }
    }

    @Test
    fun `labelling one client leaves the other unlabelled`() {
        serverTab(
            initial = serverSettings { copy(serverHost = "host.local") },
            seedClients = {
                allowPermanently("alpha")
                allowPermanently("zulu")
            },
        ) { _, clients ->
            onAllNodesWithContentDescription(ServerLabel.SET_LABEL)[0].performScrollTo().performClick()
            waitForIdle()
            retypeField("", "Only Alpha")
            onNodeWithContentDescription("Save").performClick()
            waitForIdle()

            assertEquals("Only Alpha", clients.getLabel("alpha"), "the first row must take the name")
            assertEquals("", clients.getLabel("zulu"), "the second must be left alone")
        }
    }
}

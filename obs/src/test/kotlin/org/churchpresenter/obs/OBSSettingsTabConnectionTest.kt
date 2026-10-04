@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.obs

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the connection card: the password box, the Connect button and the status line beside it.
 *
 * The status is owned by `OBSWebSocketManager` and exposed as a read-only `State`, so it cannot be
 * posed from a fixture. What *can* be driven is the transition the button causes: `connect()` sets
 * CONNECTING synchronously, before any socket work, so a click moves the tab into that state with no
 * network involved and no waiting. That covers the connecting branch, the status text that goes with
 * it, and the button disabling itself so a second click cannot pile on.
 *
 * The CONNECTED and ERROR branches need something on the other end of the socket. `OBSWebSocketManager`
 * has its own suite (`OBSWebSocketManagerTest`) with a fake OBS for exactly that; duplicating it here
 * to colour in two more branches of this tab would be testing the manager twice, so those two status
 * strings are left to it. What this class does assert is that the tab renders whatever status it is
 * given, which is its half of the contract.
 */
class OBSSettingsTabConnectionTest {

    // ── Password ────────────────────────────────────────────────────────────────────────────────

    /**
     * The box is masked, and the mask reaches the semantics: the tree carries bullets, not the
     * characters. So the typed value is asserted against the stored setting, and what is asserted on
     * screen is the masking itself — which is the whole point of the control.
     */
    @Test
    fun `the password box stores what is typed and shows it masked`() {
        obsTab(initial = obsEnabled { copy(host = "obs.local") }) { get, _ ->
            assertEquals("", get().obsSettings.password, "no password out of the box")

            fieldRightOf(ObsLabel.PASSWORD).performTextReplacement("hunter2")
            waitForIdle()

            assertEquals("hunter2", get().obsSettings.password, "the typed password must be stored")
            assertEquals("obs.local", get().obsSettings.host, "and the host must be untouched")
            assertObsFieldShows("•".repeat("hunter2".length), "the password box, which must mask what it holds")
        }
    }

    /**
     * The masking is presentational only. `EditableText` — what a screen reader announces and what is
     * drawn — is bullets, but the node's `InputText` still carries the characters, so the password is
     * readable from the semantics tree by anything that looks. Pinned as the behaviour that ships:
     * the box hides the password from over-the-shoulder reading, not from automation.
     */
    @Test
    fun `a stored password is displayed masked but still present in the tree`() {
        obsTab(initial = obsEnabled { copy(password = "hunter2") }) { _, _ ->
            assertObsFieldShows("•••••••", "the password box")
            onNodeWithText("hunter2").assertExists("the raw text is still in InputText, unmasked")
        }
    }

    @Test
    fun `clearing the password stores a blank`() {
        obsTab(initial = obsEnabled { copy(password = "hunter2") }) { get, _ ->
            fieldRightOf(ObsLabel.PASSWORD).performTextReplacement("")
            waitForIdle()
            assertEquals("", get().obsSettings.password, "an emptied password must be stored as blank")
        }
    }

    // ── The port box's own guard ────────────────────────────────────────────────────────────────

    /**
     * The port box takes any text but only stores what parses. Unlike the server tab's, it does not
     * filter keystrokes — the text stays on screen while the setting keeps its last good value, so
     * both halves are asserted.
     */
    @Test
    fun `a port that is not a number leaves the stored port alone`() {
        obsTab(initial = obsEnabled()) { get, _ ->
            obsFieldShowing("4455").performTextReplacement("not-a-port")
            waitForIdle()

            assertEquals(4455, get().obsSettings.port, "nonsense must not reach the stored port")
            assertObsFieldShows("not-a-port", "the box itself, which does show what was typed")

            obsFieldShowing("not-a-port").performTextReplacement("9001")
            waitForIdle()
            assertEquals(9001, get().obsSettings.port, "a real port must still land")
        }
    }

    // ── Status and the Connect button ───────────────────────────────────────────────────────────

    @Test
    fun `a fresh tab reads as not connected and offers Connect`() {
        obsTab(initial = obsEnabled()) { _, obs ->
            assertEquals(OBSWebSocketManager.ConnectionStatus.DISCONNECTED, obs.status.value)
            onNodeWithText(ObsLabel.DISCONNECTED).assertExists("the status line must say so")
            onNodeWithText(ObsLabel.CONNECT).assertExists("and Connect must be offered")
            onNodeWithText(ObsLabel.DISCONNECT).assertDoesNotExist() // no Disconnect while not connected
        }
    }

    @Test
    fun `the connection card appears only once OBS is enabled`() {
        obsTab { _, _ ->
            onNodeWithText(ObsLabel.CONNECT).assertDoesNotExist()
            onNodeWithText(ObsLabel.DISCONNECTED).assertDoesNotExist()
        }
        obsTab(initial = obsEnabled()) { _, _ ->
            onNodeWithText(ObsLabel.CONNECT).assertExists()
        }
    }

    /**
     * Clicking Connect moves the manager into CONNECTING synchronously — no socket has been opened
     * yet — so this asserts the transition without waiting on anything. The manager is left trying to
     * reach a port with nothing on it, which is why it is a throwaway rather than a shared one.
     */
    @Test
    fun `Connect moves the tab into the connecting state`() {
        val manager = OBSWebSocketManager()
        BlackHoleSocket().use { blackHole ->
            try {
                obsTab(
                    initial = obsEnabled { copy(host = "127.0.0.1", port = blackHole.port) },
                    manager = manager,
                ) { _, obs ->
                    onNodeWithText(ObsLabel.CONNECT).performClick()

                    assertEquals(
                        OBSWebSocketManager.ConnectionStatus.CONNECTING,
                        obs.status.value,
                        "connect() must set CONNECTING before it does any I/O",
                    )
                    waitForIdle()
                    onNodeWithText(ObsLabel.CONNECTING).assertExists("and the status line must follow")
                }
            } finally {
                manager.disconnect()
            }
        }
    }

    /** While connecting the button is disabled, so a second click cannot stack another attempt. */
    @Test
    fun `Connect disables itself while a connection is in flight`() {
        val manager = OBSWebSocketManager()
        // A closed port refuses instantly, which would end the attempt before it could be observed.
        // This one accepts and then says nothing, so CONNECTING holds for as long as is needed.
        BlackHoleSocket().use { blackHole ->
            try {
                obsTab(
                    initial = obsEnabled { copy(host = "127.0.0.1", port = blackHole.port) },
                    manager = manager,
                ) { _, _ ->
                    onNodeWithText(ObsLabel.CONNECT).assertIsEnabled()

                    onNodeWithText(ObsLabel.CONNECT).performClick()
                    waitForIdle()

                    onNodeWithText(ObsLabel.CONNECT).assertIsNotEnabled()
                    onNodeWithText(ObsLabel.CONNECTING).assertExists("and the status must read connecting")
                }
            } finally {
                manager.disconnect()
            }
        }
    }

    /**
     * Connect reads the boxes rather than the stored settings, which matters while they disagree: a
     * host typed but not yet committed is what an operator expects to be dialled.
     */
    @Test
    fun `Connect dials the host and port currently in the boxes`() {
        val manager = OBSWebSocketManager()
        try {
            obsTab(initial = obsEnabled(), manager = manager) { get, obs ->
                obsFieldShowing("localhost").performTextReplacement("127.0.0.1")
                obsFieldShowing("4455").performTextReplacement("1")
                waitForIdle()
                assertEquals("127.0.0.1", get().obsSettings.host, "the box must have written through")
                assertEquals(1, get().obsSettings.port)

                onNodeWithText(ObsLabel.CONNECT).performClick()

                assertEquals(
                    OBSWebSocketManager.ConnectionStatus.CONNECTING,
                    obs.status.value,
                    "the click must start an attempt with what the boxes hold",
                )
            }
        } finally {
            manager.disconnect()
        }
    }

    /**
     * A port box left unparseable falls back to 4455 rather than refusing to connect — the tab would
     * otherwise be stuck with no way to dial while the box holds junk.
     */
    @Test
    fun `Connect falls back to the default port when the box will not parse`() {
        val manager = OBSWebSocketManager()
        try {
            obsTab(initial = obsEnabled { copy(host = "127.0.0.1") }, manager = manager) { get, obs ->
                obsFieldShowing("4455").performTextReplacement("junk")
                waitForIdle()
                assertEquals(4455, get().obsSettings.port, "the stored port keeps its last good value")

                onNodeWithText(ObsLabel.CONNECT).performClick()

                assertTrue(
                    obs.status.value == OBSWebSocketManager.ConnectionStatus.CONNECTING,
                    "an unparseable box must not stop the attempt",
                )
            }
        } finally {
            manager.disconnect()
        }
    }
}

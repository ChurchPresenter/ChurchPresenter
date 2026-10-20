@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.serverui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.server.DevicePermission
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What each allowed device may do, set from its row in the Remote Clients card: the Permissions
 * button opens one switch per permission, and the row says how many are on while it is closed.
 * Every switch is asserted against the manager, which is what the server is handed.
 */
class ServerClientPermissionsTest {

    private fun ComposeUiTest.openPermissions(row: Int = 0) {
        onAllNodesWithContentDescription(PERMISSIONS)[row].performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.flip(label: String) {
        onNode(isToggleable() and hasText(label)).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `an allowed device says it may do everything, and a blocked one has no permissions`() {
        serverTab(seedClients = {
            allowPermanently("phone-1")
            blockPermanently("tablet-2")
        }) { _, _ ->
            onNodeWithText(ALL_ALLOWED).assertExists()
            onAllNodesWithContentDescription(PERMISSIONS).assertCountEquals(1)

            showBlockedClients()
            onAllNodesWithContentDescription(PERMISSIONS).assertCountEquals(0)
        }
    }

    @Test
    fun `the Permissions button opens a switch for each permission, and closes them again`() {
        serverTab(seedClients = { allowPermanently("phone-1") }) { _, _ ->
            openPermissions()
            onAllNodes(isToggleable()).assertCountEquals(SERVER_SWITCHES + DevicePermission.entries.size)

            openPermissions()
            onAllNodes(isToggleable()).assertCountEquals(SERVER_SWITCHES)
        }
    }

    @Test
    fun `each switch turns its own permission off and on`() {
        val labels = mapOf(
            DevicePermission.CONTROL_OUTPUT to "Control the screen",
            DevicePermission.ADD_TO_SCHEDULE to "Add to the schedule",
            DevicePermission.REMOVE_FROM_SCHEDULE to "Remove from the schedule",
            DevicePermission.GO_LIVE to "Go live",
            DevicePermission.UPLOAD_FILES to "Upload files",
            DevicePermission.SHOW_TOASTS to "Show activity notifications",
        )
        serverTab(seedClients = { allowPermanently("phone-1") }) { _, clients ->
            openPermissions()
            labels.forEach { (permission, label) ->
                flip(label)
                val off = clients.permissionsFor("phone-1")
                assertFalse(off.allows(permission), "$label must switch $permission off")
                assertTrue(DevicePermission.entries.minus(permission).all(off::allows), "and nothing else")

                flip(label)
                assertTrue(clients.permissionsFor("phone-1").allows(permission), "$label must switch it back on")
            }
        }
    }

    @Test
    fun `a closed row says how many permissions are on, and that it is silenced`() {
        serverTab(seedClients = { allowPermanently("phone-1") }) { _, _ ->
            openPermissions()
            flip("Go live")
            flip("Show activity notifications")
            openPermissions()

            onNodeWithText("4 of 5 allowed").assertExists("notifications are not counted among what it may do")
            onNodeWithText("No notifications").assertExists()
            onNodeWithText(ALL_ALLOWED).assertDoesNotExist()
        }
    }

    @Test
    fun `each device's switches are its own`() {
        serverTab(seedClients = {
            allowPermanently("alpha")
            allowPermanently("zulu")
        }) { _, clients ->
            openPermissions(row = 1)
            flip("Upload files")

            assertFalse(clients.permissionsFor("zulu").allows(DevicePermission.UPLOAD_FILES))
            assertTrue(clients.permissionsFor("alpha").allows(DevicePermission.UPLOAD_FILES))
        }
    }

    private companion object {
        const val PERMISSIONS = "Permissions"
        const val ALL_ALLOWED = "All allowed"

        /** Enable Server, API Key Protection and Allow File Upload, above the clients card. */
        const val SERVER_SWITCHES = 3
    }
}

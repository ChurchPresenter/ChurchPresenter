package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.companionserver.InstanceLinkStatus
import kotlin.test.Test

/**
 * The instance-link status row shown in the link dialogs and toasts.
 *
 * The label branches on the connection state: a connected link may show a caller-supplied name
 * (which room it's linked to), an errored one a caller-supplied reason. Those override branches are
 * what tell the operator whether the second campus is actually live — asserting each proves the
 * right custom label wins for the right state.
 */
@OptIn(ExperimentalTestApi::class)
class ConnectionStatusRowTest {

    @Test
    fun `a connected link shows the supplied connection label`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ConnectionStatusRow(
                    status = InstanceLinkStatus.CONNECTED,
                    connectedLabel = "Linked to Overflow room",
                )
            }
        }
        onNodeWithText("Linked to Overflow room", substring = true)
            .assertExists("a connected link must name what it's connected to")
    }

    @Test
    fun `an errored link shows the supplied error label`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ConnectionStatusRow(
                    status = InstanceLinkStatus.ERROR,
                    errorLabel = "Host unreachable",
                )
            }
        }
        onNodeWithText("Host unreachable", substring = true)
            .assertExists("an errored link must surface the reason, not a generic state")
    }
}

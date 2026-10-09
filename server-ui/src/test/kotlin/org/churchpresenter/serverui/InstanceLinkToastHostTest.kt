package org.churchpresenter.serverui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.server.InstanceLinkCommandFailure
import kotlin.test.Test

/**
 * The bottom-of-screen toast that surfaces InstanceLink command failures — what used to be a
 * silent drop. The message branches on [InstanceLinkCommandFailure.soft]: a soft failure means the
 * primary never acknowledged (likely an old version), a hard one carries the command and reason.
 * Getting the wrong branch would tell the operator the wrong thing about why a remote action didn't
 * land, so each branch — and the empty case that must show nothing — is asserted.
 */
@OptIn(ExperimentalTestApi::class)
class InstanceLinkToastHostTest {

    @Test
    fun `a hard failure names the command and the reason`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                InstanceLinkToastHost(
                    failures = listOf(
                        InstanceLinkCommandFailure(commandType = "go_live", reason = "timeout", soft = false)
                    ),
                    onDismiss = {},
                )
            }
        }
        onNodeWithText("go_live", substring = true)
            .assertExists("a hard failure must name which command failed")
        onNodeWithText("timeout", substring = true)
            .assertExists("a hard failure must surface the reason it failed")
    }

    @Test
    fun `a soft failure shows the no-acknowledgement message, not a command-and-reason line`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                InstanceLinkToastHost(
                    failures = listOf(
                        InstanceLinkCommandFailure(commandType = "go_live", reason = null, soft = true)
                    ),
                    onDismiss = {},
                )
            }
        }
        onNodeWithText("did not acknowledge", substring = true)
            .assertExists("a soft failure must explain the primary never acknowledged")
        onNodeWithText("go_live", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `with no failures the toast shows nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                InstanceLinkToastHost(failures = emptyList(), onDismiss = {})
            }
        }
        onNodeWithText("Dismiss", substring = true)
            .assertDoesNotExist()
    }
}

package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.server.RemoteActivityNotification
import org.churchpresenter.server.RemoteEventType
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RemoteActivityToastHostTest {

    private fun host(
        notifications: List<RemoteActivityNotification>,
        followers: Set<String> = emptySet(),
        onDismiss: (RemoteActivityNotification) -> Unit = {},
        onDismissAll: () -> Unit = {},
        onBlockForSession: (RemoteActivityNotification) -> Unit = {},
    ): @androidx.compose.runtime.Composable () -> Unit = {
        MaterialTheme {
            RemoteActivityToastHost(
                notifications = notifications,
                onDismiss = onDismiss,
                onDismissAll = onDismissAll,
                onBlockForSession = onBlockForSession,
                connectedInstanceLinkFollowers = followers,
            )
        }
    }

    @Test
    fun `the most recent notification's action and title are shown`() = runComposeUiTest {
        setContent(
            host(
                listOf(
                    RemoteActivityNotification(RemoteEventType.UPLOAD, title = "Old upload"),
                    RemoteActivityNotification(RemoteEventType.PROJECT, title = "Amazing Grace"),
                )
            )
        )
        // PROJECT is the last entry, so its label ("Projected (Go Live)") and title win.
        onNodeWithText("Projected", substring = true)
            .assertExists("the action label for the newest notification must show")
        onNodeWithText("Amazing Grace", substring = true)
            .assertExists("the newest notification's title must show")
    }

    @Test
    fun `a client label is used for attribution when present`() = runComposeUiTest {
        setContent(
            host(
                listOf(
                    RemoteActivityNotification(
                        RemoteEventType.PROJECT,
                        title = "Psalm 23",
                        clientId = "device-abcdef",
                        clientLabel = "Booth iPad",
                    )
                )
            )
        )
        onNodeWithText("Booth iPad", substring = true)
            .assertExists("a human client label must be preferred for attribution")
    }

    @Test
    fun `an instance-link follower is badged`() = runComposeUiTest {
        setContent(
            host(
                notifications = listOf(
                    RemoteActivityNotification(
                        RemoteEventType.PROJECT,
                        title = "Psalm 23",
                        clientId = "follower-1",
                    )
                ),
                followers = setOf("follower-1"),
            )
        )
        onNodeWithText("Instance Link", substring = true)
            .assertExists("a connected follower's action must be badged as such")
    }

    @Test
    fun `with no notifications nothing is shown`() = runComposeUiTest {
        setContent(host(emptyList()))
        onNodeWithText("Dismiss", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `every remaining remote event type renders its own action label`() = runComposeUiTest {
        var notifications by mutableStateOf(emptyList<RemoteActivityNotification>())
        setContent {
            MaterialTheme {
                RemoteActivityToastHost(
                    notifications = notifications,
                    onDismiss = {},
                    onDismissAll = {},
                    onBlockForSession = {},
                )
            }
        }
        val labelByType = mapOf(
            RemoteEventType.ADD_TO_SCHEDULE to "Added to Schedule",
            RemoteEventType.REMOVE_FROM_SCHEDULE to "Removed from Schedule",
            RemoteEventType.PRESENT to "Presenting",
            RemoteEventType.UPLOAD to "Uploaded",
            RemoteEventType.CLEAR to "Cleared Display",
            RemoteEventType.QA_ADD to "Q&A Question Added",
            RemoteEventType.QA_EDIT to "Q&A Question Edited",
            RemoteEventType.QA_DELETE to "Q&A Question Deleted",
            RemoteEventType.QA_APPROVE to "Q&A Question Approved",
            RemoteEventType.QA_DENY to "Q&A Question Denied",
            RemoteEventType.QA_DONE to "Q&A Question Done",
            RemoteEventType.QA_DISPLAY to "Q&A Question Live",
            RemoteEventType.QA_CLEAR_DISPLAY to "Q&A Display Cleared",
        )
        for ((type, label) in labelByType) {
            notifications = listOf(RemoteActivityNotification(type, title = "Detail for $type"))
            waitForIdle()
            onNodeWithText(label, substring = true)
                .assertExists("the action label for $type must show")
        }
    }

    @Test
    fun `a blank-title presentation connect falls back to its detail line`() = runComposeUiTest {
        setContent(host(listOf(RemoteActivityNotification(RemoteEventType.PRESENTATION_CONNECT, title = ""))))
        onNodeWithText("Connected to Presentation Remote", substring = true).assertExists()
        onNodeWithText("Now connected", substring = true)
            .assertExists("a blank title on a connect event must fall back to its detail line")
    }

    @Test
    fun `a blank-title qa admin connect falls back to its detail line`() = runComposeUiTest {
        setContent(host(listOf(RemoteActivityNotification(RemoteEventType.QA_ADMIN_CONNECT, title = ""))))
        onNodeWithText("Connected to Q&A Admin", substring = true).assertExists()
        onNodeWithText("Now connected", substring = true)
            .assertExists("a blank title on a connect event must fall back to its detail line")
    }

    @Test
    fun `a notification's detail is shown alongside its title`() = runComposeUiTest {
        setContent(
            host(
                listOf(
                    RemoteActivityNotification(
                        RemoteEventType.UPLOAD,
                        title = "sermon-slides.pptx",
                        detail = "12.4 MB",
                    )
                )
            )
        )
        onNodeWithText("12.4 MB", substring = true)
            .assertExists("the detail text must render next to the title")
    }

    @Test
    fun `dismiss dismisses the current (most recent) notification`() = runComposeUiTest {
        var dismissed: RemoteActivityNotification? = null
        val newest = RemoteActivityNotification(RemoteEventType.PROJECT, title = "Newest")
        setContent(
            host(
                listOf(RemoteActivityNotification(RemoteEventType.UPLOAD, title = "Oldest"), newest),
                onDismiss = { dismissed = it },
            )
        )
        onNodeWithText("Dismiss").performClick()
        assertEquals(newest, dismissed, "dismiss must target the newest (currently shown) notification")
    }

    @Test
    fun `dismiss all invokes onDismissAll`() = runComposeUiTest {
        var dismissedAll = false
        setContent(
            host(
                listOf(
                    RemoteActivityNotification(RemoteEventType.UPLOAD, title = "Oldest"),
                    RemoteActivityNotification(RemoteEventType.PROJECT, title = "Newest"),
                ),
                onDismissAll = { dismissedAll = true },
            )
        )
        onNodeWithText("Dismiss All").performClick()
        assertTrue(dismissedAll, "the Dismiss All button must invoke onDismissAll")
    }

    @Test
    fun `blocking for session targets the current notification`() = runComposeUiTest {
        var blocked: RemoteActivityNotification? = null
        val notification = RemoteActivityNotification(RemoteEventType.PROJECT, title = "Psalm 23")
        setContent(host(listOf(notification), onBlockForSession = { blocked = it }))
        // The block-for-session icon button is the first clickable node in the toast (before Dismiss).
        onAllNodes(hasClickAction())[0].performClick()
        assertEquals(notification, blocked, "block-for-session must target the currently shown notification")
    }

    @Test
    fun `the toast auto-dismisses after the timeout`() = runComposeUiTest {
        var dismissed: RemoteActivityNotification? = null
        val notification = RemoteActivityNotification(RemoteEventType.PROJECT, title = "Psalm 23")
        mainClock.autoAdvance = false
        setContent(host(listOf(notification), onDismiss = { dismissed = it }))
        mainClock.advanceTimeBy(TOAST_AUTO_DISMISS_MS)
        waitForIdle()
        assertEquals(notification, dismissed, "the toast must dismiss itself once the auto-dismiss timer elapses")
    }
}

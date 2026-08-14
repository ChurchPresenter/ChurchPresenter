@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteEventContentTest {

    private class Result {
        var allowCalls = 0
        var allowForSessionCalls = 0
        var allowPermanentlyCalls = 0
        var blockForSessionCalls = 0
        var blockPermanentlyCalls = 0
        var denyCalls = 0
    }

    private fun dialog(
        event: RemoteEvent = RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "Amazing Grace"),
        remaining: Int = 0,
        showAllowPermanently: Boolean = true,
        isClientKnownAllowed: Boolean = false,
        isClientKnownBlocked: Boolean = false,
        isInstanceLinkFollower: Boolean = false,
        block: ComposeUiTest.(Result) -> Unit,
    ) {
        val result = Result()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    RemoteEventDialogContent(
                        event = event,
                        actionLabel = "Add to Schedule",
                        typeIcon = Icons.Filled.CalendarMonth,
                        typeAccent = MaterialTheme.colorScheme.primary,
                        bodyTitle = event.title,
                        remaining = remaining,
                        showAllowPermanently = showAllowPermanently,
                        isClientKnownAllowed = isClientKnownAllowed,
                        isClientKnownBlocked = isClientKnownBlocked,
                        isInstanceLinkFollower = isInstanceLinkFollower,
                        onAllow = { result.allowCalls++ },
                        onAllowForSession = { result.allowForSessionCalls++ },
                        onAllowPermanently = { result.allowPermanentlyCalls++ },
                        onBlockForSession = { result.blockForSessionCalls++ },
                        onBlockPermanently = { result.blockPermanentlyCalls++ },
                        onDeny = { result.denyCalls++ },
                    )
                }
            }
            block(result)
        }
    }

    @Test
    fun `the action label and item title are shown`() = dialog {
        onNodeWithText("Add to Schedule").assertExists()
        onNodeWithText("Amazing Grace").assertExists()
    }

    @Test
    fun `the prominent Allow button calls onAllow`() = dialog { result ->
        onNodeWithText("Allow").performClick()
        assertEquals(1, result.allowCalls)
    }

    @Test
    fun `the Allow icon button also calls onAllow`() = dialog { result ->
        onNodeWithContentDescription("Allow").performClick()
        assertEquals(1, result.allowCalls)
    }

    @Test
    fun `Deny calls onDeny`() = dialog { result ->
        onNodeWithContentDescription("Deny").performClick()
        assertEquals(1, result.denyCalls)
    }

    @Test
    fun `Allow for Session calls onAllowForSession`() = dialog { result ->
        onNodeWithContentDescription("Allow for Session").performClick()
        assertEquals(1, result.allowForSessionCalls)
    }

    @Test
    fun `Allow Permanently calls onAllowPermanently when it is offered`() = dialog { result ->
        onNodeWithContentDescription("Allow Permanently").performClick()
        assertEquals(1, result.allowPermanentlyCalls)
    }

    @Test
    fun `Allow Permanently is hidden once the client is already known`() = dialog(showAllowPermanently = false) {
        onNodeWithContentDescription("Allow Permanently").assertDoesNotExist()
    }

    @Test
    fun `Block for Session calls onBlockForSession`() = dialog { result ->
        onNodeWithContentDescription("Block for Session").performClick()
        assertEquals(1, result.blockForSessionCalls)
    }

    @Test
    fun `Block Permanently calls onBlockPermanently`() = dialog { result ->
        onNodeWithContentDescription("Block Permanently").performClick()
        assertEquals(1, result.blockPermanentlyCalls)
    }

    @Test
    fun `no queue badge is shown when nothing else is waiting`() = dialog(remaining = 0) {
        onNodeWithText("1 more request waiting in queue").assertDoesNotExist()
    }

    @Test
    fun `a single queued item uses the singular wording`() = dialog(remaining = 1) {
        onNodeWithText("+1").assertExists()
        onNodeWithText("1 more request waiting in queue").assertExists()
    }

    @Test
    fun `several queued items use the plural wording with a count`() = dialog(remaining = 3) {
        onNodeWithText("+3").assertExists()
        onNodeWithText("3 more requests waiting in queue").assertExists()
    }

    @Test
    fun `an allowed client shows its badge`() = dialog(
        event = RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "x", clientId = "device-1"),
        isClientKnownAllowed = true,
    ) {
        onNodeWithText("✓ allowed").assertExists()
    }

    @Test
    fun `a blocked client shows its badge`() = dialog(
        event = RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "x", clientId = "device-1"),
        isClientKnownBlocked = true,
    ) {
        onNodeWithText("⛔ blocked").assertExists()
    }

    @Test
    fun `an instance link follower shows its own badge`() = dialog(
        event = RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "x", clientId = "device-1"),
        isInstanceLinkFollower = true,
    ) {
        onNodeWithText("Instance Link — currently connected").assertExists()
    }

    @Test
    fun `a client label is shown alongside the raw device id`() = dialog(
        event = RemoteEvent(
            type = RemoteEventType.ADD_TO_SCHEDULE,
            title = "x",
            clientId = "device-1",
            clientLabel = "Front Row iPad",
        ),
    ) {
        onNodeWithText("Front Row iPad").assertExists()
        onNodeWithText("device-1").assertExists()
    }

    @Test
    fun `event detail text is shown when present`() = dialog(
        event = RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "Amazing Grace", detail = "Hymnal #42"),
    ) {
        onNodeWithText("Hymnal #42").assertExists()
    }

    private fun presentationFor(
        event: RemoteEvent,
        queueSize: Int = 1,
        isClientKnownAllowed: Boolean = false,
        isClientKnownBlocked: Boolean = false,
    ): RemoteEventPresentation {
        lateinit var result: RemoteEventPresentation
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    result = resolveRemoteEventPresentation(
                        event,
                        queueSize,
                        isClientKnownAllowed,
                        isClientKnownBlocked
                    )
                }
            }
        }
        return result
    }

    private fun themePrimaryColor(): Color {
        var result = Color.Unspecified
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    result = MaterialTheme.colorScheme.primary
                }
            }
        }
        return result
    }

    @Test
    fun `add to schedule resolves its label, icon and amber accent`() {
        val presentation = presentationFor(RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "Song"))
        assertEquals("Add to Schedule", presentation.actionLabel)
        assertEquals(Icons.Filled.CalendarMonth, presentation.typeIcon)
    }

    @Test
    fun `remove from schedule resolves its label and icon`() {
        val presentation = presentationFor(RemoteEvent(type = RemoteEventType.REMOVE_FROM_SCHEDULE, title = "Song"))
        assertEquals("Remove from Schedule", presentation.actionLabel)
        assertEquals(Icons.Filled.EventBusy, presentation.typeIcon)
    }

    @Test
    fun `project resolves its label and icon`() {
        val presentation = presentationFor(RemoteEvent(type = RemoteEventType.PROJECT, title = "Song"))
        assertEquals("Project (Go Live)", presentation.actionLabel)
        assertEquals(Icons.Filled.Cast, presentation.typeIcon)
    }

    @Test
    fun `presentation connect resolves its label, icon and blank-title detail fallback`() {
        val presentation = presentationFor(RemoteEvent(type = RemoteEventType.PRESENTATION_CONNECT, title = ""))
        assertEquals("Connect to Presentation Remote", presentation.actionLabel)
        assertEquals(Icons.Filled.Smartphone, presentation.typeIcon)
        assertEquals("A phone or tablet wants to browse and control your presentation slides.", presentation.bodyTitle)
    }

    @Test
    fun `qa admin connect resolves its label, icon and blank-title detail fallback`() {
        val presentation = presentationFor(RemoteEvent(type = RemoteEventType.QA_ADMIN_CONNECT, title = ""))
        assertEquals("Connect to Q&A Admin", presentation.actionLabel)
        assertEquals(Icons.Filled.Smartphone, presentation.typeIcon)
        assertEquals("A phone or tablet wants to moderate Q&A questions.", presentation.bodyTitle)
    }

    @Test
    fun `qa event types resolve their labels and the shared question-answer icon`() {
        val cases = listOf(
            RemoteEventType.QA_ADD to "Q&A: Add Question",
            RemoteEventType.QA_EDIT to "Q&A: Edit Question",
            RemoteEventType.QA_DELETE to "Q&A: Delete Question",
            RemoteEventType.QA_APPROVE to "Q&A: Approve Question",
            RemoteEventType.QA_DENY to "Q&A: Deny Question",
            RemoteEventType.QA_DONE to "Q&A: Mark as Done",
            RemoteEventType.QA_DISPLAY to "Q&A: Go Live",
            RemoteEventType.QA_CLEAR_DISPLAY to "Q&A: Clear Display",
        )
        for ((type, expectedLabel) in cases) {
            val presentation = presentationFor(RemoteEvent(type = type, title = "x"))
            assertEquals(expectedLabel, presentation.actionLabel, "label for $type")
            assertEquals(Icons.Filled.QuestionAnswer, presentation.typeIcon, "icon for $type")
        }
    }

    @Test
    fun `instant event types fall back to the generic request title and bell icon`() {
        val primary = themePrimaryColor()
        for (type in listOf(RemoteEventType.PRESENT, RemoteEventType.UPLOAD, RemoteEventType.CLEAR)) {
            val presentation = presentationFor(RemoteEvent(type = type, title = "x"))
            assertEquals("Remote API Request", presentation.actionLabel, "label for $type")
            assertEquals(Icons.Filled.Notifications, presentation.typeIcon, "icon for $type")
            assertEquals(primary, presentation.typeAccent, "accent for $type")
        }
    }

    @Test
    fun `a non-blank title is used as the body title instead of the connect detail text`() {
        val presentation = presentationFor(
            RemoteEvent(type = RemoteEventType.PRESENTATION_CONNECT, title = "Explicit Title"),
        )
        assertEquals("Explicit Title", presentation.bodyTitle)
    }

    @Test
    fun `a queue of one item has no remaining, allows permanently and uses the plain title and short height`() {
        val presentation = presentationFor(
            RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "x"),
            queueSize = 1,
        )
        assertEquals(0, presentation.remaining)
        assertTrue(presentation.showAllowPermanently)
        assertEquals("Remote API Request", presentation.dialogTitle)
        assertEquals(290.dp, presentation.dialogHeight)
    }

    @Test
    fun `a queue with items behind it reports the pending count and uses the taller height`() {
        val presentation = presentationFor(
            RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "x"),
            queueSize = 4,
        )
        assertEquals(3, presentation.remaining)
        assertEquals("Remote API Request (4 pending)", presentation.dialogTitle)
        assertEquals(330.dp, presentation.dialogHeight)
    }

    @Test
    fun `allow permanently is withheld once the client is already allowed or blocked`() {
        assertFalse(
            presentationFor(
                RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "x"),
                isClientKnownAllowed = true,
            ).showAllowPermanently
        )
        assertFalse(
            presentationFor(
                RemoteEvent(type = RemoteEventType.ADD_TO_SCHEDULE, title = "x"),
                isClientKnownBlocked = true,
            ).showAllowPermanently
        )
    }
}

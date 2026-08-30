@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.utils.ContactReporter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactUsContentTest {

    @BeforeTest
    fun stubReporter() {
        mockkObject(ContactReporter)
    }

    @AfterTest
    fun cleanUp() {
        unmockkObject(ContactReporter)
    }

    private val types = listOf(
        "Feature Request" to "featureRequest",
        "Feedback" to "feedback",
        "Testimonial" to "testimonial",
        "Bug Report" to "bugReport",
    )

    private class Result {
        var dismissed = 0
        var sentWith: Triple<String, String, String>? = null
    }

    private fun dialog(
        status: SendStatus = SendStatus.Idle,
        // Defaulted to a no-op, never to the real opener: a click that reached UrlOpener would
        // launch the machine's browser, which the headless JVM does not prevent.
        openUrl: (String) -> Unit = {},
        block: ComposeUiTest.(Result) -> Unit,
    ) {
        val result = Result()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var selectedType by remember { mutableStateOf(types.first()) }
                    var name by remember { mutableStateOf("") }
                    var email by remember { mutableStateOf("") }
                    var message by remember { mutableStateOf("") }
                    ContactUsDialogContent(
                        openUrl = openUrl,
                        onDismiss = { result.dismissed++ },
                        types = types,
                        selectedType = selectedType,
                        onSelectedTypeChange = { selectedType = it },
                        name = name,
                        onNameChange = { name = it },
                        email = email,
                        onEmailChange = { email = it },
                        message = message,
                        onMessageChange = { message = it },
                        status = status,
                        onSend = { result.sentWith = Triple(selectedType.second, name, message) },
                        sentText = "All done!",
                    )
                }
            }
            block(result)
        }
    }

    private fun ComposeUiTest.nameField(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[0]
    private fun ComposeUiTest.messageField(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[2]

    @Test
    fun `Send is disabled with no name or message`() = dialog {
        onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun `Send is disabled with a message but no name`() = dialog {
        messageField().performTextInput("Loving the app!")
        onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun `Send becomes enabled once both name and message are filled in`() = dialog {
        nameField().performTextInput("A Church")
        messageField().performTextInput("Loving the app!")
        onNodeWithText("Send").assertIsEnabled()
    }

    @Test
    fun `Send is disabled while a send is already in flight`() = dialog(status = SendStatus.Sending) {
        nameField().performTextInput("A Church")
        messageField().performTextInput("Loving the app!")
        onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun `clicking Send hands back the typed name, message and selected type`() = dialog { result ->
        nameField().performTextInput("A Church")
        messageField().performTextInput("Loving the app!")
        onNodeWithText("Send").performClick()

        assertEquals(Triple("featureRequest", "A Church", "Loving the app!"), result.sentWith)
    }

    @Test
    fun `picking a different type changes what Send hands back`() = dialog { result ->
        onNodeWithText("Bug Report").performClick()
        nameField().performTextInput("A Church")
        messageField().performTextInput("Something's broken")
        onNodeWithText("Send").performClick()

        assertEquals("bugReport", result.sentWith?.first)
    }

    @Test
    fun `Cancel dismisses without sending`() = dialog { result ->
        nameField().performTextInput("A Church")
        onNodeWithText("Cancel").performClick()

        assertEquals(1, result.dismissed)
        assertEquals(null, result.sentWith)
    }

    @Test
    fun `a Sending status shows a sending message`() = dialog(status = SendStatus.Sending) {
        onNodeWithText("Sending…").assertExists()
    }

    @Test
    fun `a Sent status shows the caller-supplied confirmation`() = dialog(status = SendStatus.Sent) {
        onNodeWithText("All done!").assertExists()
    }

    @Test
    fun `an Error status shows its own message`() = dialog(status = SendStatus.Error("Something went wrong")) {
        onNodeWithText("Something went wrong").assertExists()
    }

    @Test
    fun `an Idle status shows no status line`() = dialog {
        onNodeWithText("Sending…").assertDoesNotExist()
        onNodeWithText("All done!").assertDoesNotExist()
    }

    @Test
    fun `the browser fallback button is present and enabled`() = dialog {
        onNodeWithText("Open in Browser").assertIsEnabled()
    }

    @Test
    fun `clicking the browser fallback button opens the web contact form`() {
        var opened: String? = null
        dialog(openUrl = { opened = it }) {
            onNodeWithText("Open in Browser").performClick()
        }

        assertEquals(ContactReporter.WEB_CONTACT_URL, opened)
    }

    @Test
    fun `buildContactRequest trims free-text fields and carries the type through`() {
        val request = buildContactRequest(
            type = "bugReport",
            name = "  A Church  ",
            email = " pastor@church.org ",
            message = "  Something's broken  ",
        )

        assertEquals("bugReport", request.type)
        assertEquals("A Church", request.name)
        assertEquals("pastor@church.org", request.email)
        assertEquals("Something's broken", request.message)
    }

    @Test
    fun `buildContactRequest carries diagnostic context so bug reports are triageable`() {
        val request = buildContactRequest("bugReport", "A Church", "", "Something's broken")

        assertTrue(request.context.isNotBlank())
    }

    @Test
    fun `statusForOutcome maps Success to Sent`() {
        val status = statusForOutcome(
            ContactReporter.Outcome.Success,
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Sent, status)
    }

    @Test
    fun `statusForOutcome maps RateLimited to its own error text`() {
        val status = statusForOutcome(
            ContactReporter.Outcome.RateLimited,
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Error("rate limited"), status)
    }

    @Test
    fun `statusForOutcome maps NetworkError to its own error text`() {
        val status = statusForOutcome(
            ContactReporter.Outcome.NetworkError,
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Error("network"), status)
    }

    @Test
    fun `statusForOutcome maps Failure to the generic error text`() {
        val status = statusForOutcome(
            ContactReporter.Outcome.Failure,
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Error("error"), status)
    }

    @Test
    fun `statusForOutcome prefers the server's reason for an Invalid outcome`() {
        val status = statusForOutcome(
            ContactReporter.Outcome.Invalid("Message is required"),
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Error("Message is required"), status)
    }

    @Test
    fun `statusForOutcome falls back to the generic text when Invalid has no server reason`() {
        val status = statusForOutcome(
            ContactReporter.Outcome.Invalid(null),
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Error("error"), status)
    }

    @Test
    fun `submitContactRequest maps a successful submission to Sent`() = runBlocking {
        coEvery { ContactReporter.submit(any()) } returns ContactReporter.Outcome.Success

        val status = submitContactRequest(
            type = "bugReport",
            name = "A Church",
            email = "pastor@church.org",
            message = "Something's broken",
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Sent, status)
    }

    @Test
    fun `submitContactRequest sends the trimmed request built from its arguments`() = runBlocking {
        var captured: ContactReporter.ContactRequest? = null
        coEvery { ContactReporter.submit(any()) } answers {
            captured = firstArg()
            ContactReporter.Outcome.Success
        }

        submitContactRequest(
            type = "bugReport",
            name = "  A Church  ",
            email = " pastor@church.org ",
            message = "  Something's broken  ",
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals("bugReport", captured?.type)
        assertEquals("A Church", captured?.name)
        assertEquals("pastor@church.org", captured?.email)
        assertEquals("Something's broken", captured?.message)
    }

    @Test
    fun `submitContactRequest maps a network error to the caller-supplied text`() = runBlocking {
        coEvery { ContactReporter.submit(any()) } returns ContactReporter.Outcome.NetworkError

        val status = submitContactRequest(
            type = "feedback",
            name = "A Church",
            email = "",
            message = "Loving the app!",
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Error("network"), status)
    }

    @Test
    fun `submitContactRequest maps rate limiting to the caller-supplied text`() = runBlocking {
        coEvery { ContactReporter.submit(any()) } returns ContactReporter.Outcome.RateLimited

        val status = submitContactRequest(
            type = "feedback",
            name = "A Church",
            email = "",
            message = "Loving the app!",
            errorText = "error",
            networkText = "network",
            rateLimitedText = "rate limited",
        )

        assertEquals(SendStatus.Error("rate limited"), status)
    }
}

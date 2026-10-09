package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.window.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.churchpresenter.app.churchpresenter.utils.ContactReporter
import org.churchpresenter.helper.report.ChatSendResult
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.updater.UpdateCheckResult
import java.util.concurrent.Executor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Wick in production: off until Help → Show Helper starts it, and a chat sent only as the operator asks. */
class WickAvailabilityTest {

    // Launched work is never run: nothing here reaches the network.
    private val scope = CoroutineScope(SupervisorJob() + Executor { }.asCoroutineDispatcher())

    private val root = AppRootState(
        object : ApplicationScope {
            override fun exitApplication() = Unit
        },
        scope,
        secondaryDisplays = { emptyList() },
    )

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `outside dev mode Wick is here only once started from the Help menu`() {
        assertFalse(isWickAvailable(devMode = false, HelperSettings()))
        assertTrue(isWickAvailable(devMode = false, HelperSettings(startedByUser = true)))
        assertTrue(isWickAvailable(devMode = true, HelperSettings()))
    }

    @Test
    fun `Show Helper the first time starts Wick for good and plays the intro`() {
        root.appSettings = root.appSettings.copy(helper = HelperSettings(enabled = false))
        root.showHelper()
        val helper = root.appSettings.helper
        assertTrue(helper.startedByUser)
        assertTrue(helper.enabled)
        assertTrue(root.helperState.replayIntro)
        assertFalse(root.helperState.isOpen)
        assertTrue(root.wickIntroShowing)
    }

    @Test
    fun `Show Helper after the intro brings a hidden lamp back and opens it`() {
        val seen = HelperSettings(enabled = false, introSeen = true, startedByUser = true)
        root.appSettings = root.appSettings.copy(helper = seen)
        root.showHelper()
        assertEquals(seen.copy(enabled = true), root.appSettings.helper)
        assertTrue(root.helperState.isOpen)
        assertFalse(root.helperState.replayIntro)
    }

    @Test
    fun `Show Helper on a lamp shown in dev mode starts Wick for good`() {
        root.appSettings = root.appSettings.copy(helper = HelperSettings(introSeen = true))
        root.showHelper()
        assertTrue(root.appSettings.helper.startedByUser)
        assertTrue(root.helperState.isOpen)
    }

    @Test
    fun `Show Helper on a lamp already showing saves nothing new`() {
        val shown = HelperSettings(introSeen = true, startedByUser = true)
        root.appSettings = root.appSettings.copy(helper = shown)
        val before = root.appSettings
        root.showHelper()
        assertTrue(before === root.appSettings)
        assertTrue(root.helperState.isOpen)
    }

    @Test
    fun `a sent chat is a Contact Us request of its own type`() {
        val request = wickChatRequest("Note: hi\n\nYou: x", "me@example.org", packVersion = "3")
        assertEquals(WICK_CHAT_TYPE, request.type)
        assertEquals("wickChat", request.type)
        assertEquals("Wick chat", request.name)
        assertEquals("Note: hi\n\nYou: x", request.message)
        assertEquals("me@example.org", request.email)
        assertEquals("", request.company)
        assertTrue(request.context.startsWith(ContactReporter.defaultContext() + " · "), request.context)
        assertTrue(request.context.endsWith(" · Wick pack 3"), request.context)
    }

    @Test
    fun `the contact endpoint's answer reads as sent, too many, or failed`() {
        assertEquals(ChatSendResult.SENT, ContactReporter.Outcome.Success.asChatResult())
        assertEquals(ChatSendResult.RATE_LIMITED, ContactReporter.Outcome.RateLimited.asChatResult())
        listOf(
            ContactReporter.Outcome.Invalid("bad"),
            ContactReporter.Outcome.NetworkError,
            ContactReporter.Outcome.Failure,
        ).forEach { assertEquals(ChatSendResult.FAILED, it.asChatResult()) }
    }

    @Test
    fun `the intro waits until the app is ready and nothing else is up, then shows once`() {
        root.appSettings = root.appSettings.copy(helper = HelperSettings())
        root.appReady = true
        root.eulaAccepted = true
        root.showSetupWizard = false
        root.startupChecksDone = true
        root.showStoryPrompt = false
        assertTrue(root.wickIntroShowing)
        val blockers = listOf<Pair<() -> Unit, () -> Unit>>(
            { root.appReady = false } to { root.appReady = true },
            { root.eulaAccepted = false } to { root.eulaAccepted = true },
            { root.showSetupWizard = true } to { root.showSetupWizard = false },
            { root.startupChecksDone = false } to { root.startupChecksDone = true },
            { root.showStoryPrompt = true } to { root.showStoryPrompt = false },
            { root.appSettings = root.appSettings.copy(helper = HelperSettings(enabled = false)) } to
                { root.appSettings = root.appSettings.copy(helper = HelperSettings()) },
            { root.appSettings = root.appSettings.copy(helper = HelperSettings(introSeen = true)) } to
                { root.appSettings = root.appSettings.copy(helper = HelperSettings()) },
        )
        blockers.forEach { (block, unblock) ->
            block()
            assertFalse(root.wickIntroShowing)
            unblock()
            assertTrue(root.wickIntroShowing)
        }
        root.pendingUpdateResult = UpdateCheckResult.UpToDate
        assertFalse(root.wickIntroShowing, "never beside the update window")
        root.pendingUpdateResult = null
        root.presenterManager.setPresentingMode(Presenting.LYRICS)
        assertFalse(root.wickIntroShowing, "never over a live service")
    }
}

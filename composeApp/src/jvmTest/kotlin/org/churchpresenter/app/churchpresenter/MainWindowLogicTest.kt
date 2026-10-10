package org.churchpresenter.app.churchpresenter

import org.churchpresenter.server.RemoteEvent
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.updater.UpdateCheckResult
import org.churchpresenter.updater.UpdateInfo
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainWindowLogicTest {

    private val available = UpdateCheckResult.Available(UpdateInfo("9.9.9", "https://example.invalid", "notes"))

    @Test
    fun `the first update check ever shows its result as if asked for`() {
        assertEquals(
            UpdateCheckResult.UpToDate to true,
            pendingUpdateFor(isFirstEverCheck = true, UpdateCheckResult.UpToDate),
        )
        assertEquals(available to true, pendingUpdateFor(isFirstEverCheck = true, available))
    }

    @Test
    fun `a later check shows only an available update`() {
        assertEquals(available to false, pendingUpdateFor(isFirstEverCheck = false, available))
        assertNull(pendingUpdateFor(isFirstEverCheck = false, UpdateCheckResult.UpToDate))
    }

    @Test
    fun `a later check stays quiet about the version the operator skipped`() {
        assertNull(pendingUpdateFor(isFirstEverCheck = false, available, skippedVersion = "9.9.9"))
    }

    @Test
    fun `skipping one version does not hide the next`() {
        assertEquals(
            available to false,
            pendingUpdateFor(isFirstEverCheck = false, available, skippedVersion = "9.9.8"),
        )
    }

    @Test
    fun `the first check ever shows its result even for a skipped version`() {
        assertEquals(available to true, pendingUpdateFor(isFirstEverCheck = true, available, skippedVersion = "9.9.9"))
    }

    @Test
    fun `the story prompt waits for a pending update`() {
        assertTrue(shouldShowStoryPrompt(isDue = true, updatePending = false))
        assertFalse(shouldShowStoryPrompt(isDue = true, updatePending = true))
        assertFalse(shouldShowStoryPrompt(isDue = false, updatePending = false))
    }

    @Test
    fun `the operator is live when something is on screen that the engine did not put there`() {
        assertTrue(isOperatorLive(Presenting.LYRICS, engineItemShowing = null))
        assertTrue(isOperatorLive(Presenting.LYRICS, engineItemShowing = false))
        assertFalse(isOperatorLive(Presenting.LYRICS, engineItemShowing = true))
        assertFalse(isOperatorLive(Presenting.NONE, engineItemShowing = false))
    }

    @Test
    fun `Clear Display keeps its own title, other actions use the one sent`() {
        assertEquals("Cleared", remoteActivityTitle(RemoteEventType.CLEAR, "Cleared", "ignored"))
        assertEquals("Song 12", remoteActivityTitle(RemoteEventType.PROJECT, "Cleared", "Song 12"))
    }

    @Test
    fun `custom colours start on unless a custom look exists and a preset was picked since`() {
        assertTrue(customColorsByDefault(ThemeMode.CUSTOM, customAccent = "#123456"))
        assertTrue(customColorsByDefault(ThemeMode.DARK, customAccent = ""))
        assertFalse(customColorsByDefault(ThemeMode.DARK, customAccent = "#123456"))
    }

    @Test
    fun `a session decision records an attributable client once`() {
        assertTrue(shouldRecordSessionClient("phone-1", listOf("phone-2")))
        assertFalse(shouldRecordSessionClient("phone-1", listOf("phone-1")))
        assertFalse(shouldRecordSessionClient("", emptyList()))
    }

    @Test
    fun `a decision settles every queued request from that client`() {
        fun queued(client: String) = Triple(RemoteEvent(RemoteEventType.PROJECT, "t", clientId = client), Unit, Unit)
        val queue = listOf(queued("a"), queued("b"), queued("a"))
        assertEquals(listOf(queue[0], queue[2]), remoteEventsSettledBy(queue, "a"))
        assertEquals(queue, remoteEventsSettledBy(queue, ""))
    }
}

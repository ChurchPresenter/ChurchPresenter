@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.BibleSyncMode
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.settings.InstanceLinkSettings
import org.churchpresenter.companionserver.InstanceLinkStatus
import org.churchpresenter.companionserver.LiveStateDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstanceLinkContentTest {

    private class Result {
        var connected: InstanceLinkSettings? = null
        var saved: InstanceLinkSettings? = null
        var disconnectCalls = 0
        var dismissed = 0
    }

    private fun dialog(
        settings: InstanceLinkSettings = InstanceLinkSettings(),
        connectionStatus: InstanceLinkStatus = InstanceLinkStatus.DISCONNECTED,
        remoteScheduleCount: Int = 0,
        remoteLiveState: LiveStateDto? = null,
        lastMessageAtMs: Long? = null,
        block: ComposeUiTest.(Result) -> Unit,
    ) {
        val result = Result()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    InstanceLinkDialogContent(
                        isVisible = true,
                        settings = settings,
                        connectionStatus = connectionStatus,
                        remoteLiveState = remoteLiveState,
                        remoteScheduleCount = remoteScheduleCount,
                        lastMessageAtMs = lastMessageAtMs,
                        onConnect = { result.connected = it },
                        onSave = { result.saved = it },
                        onDisconnect = { result.disconnectCalls++ },
                        onDismiss = { result.dismissed++ },
                    )
                }
            }
            block(result)
        }
    }

    private fun ComposeUiTest.hostField(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[0]
    private fun ComposeUiTest.portField(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[1]
    private fun ComposeUiTest.apiKeyField(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[2]
    private fun ComposeUiTest.reconnectDelayField(): SemanticsNodeInteraction = onAllNodes(hasSetTextAction())[3]

    // ── Connect's enabled state ─────────────────────────────────────────────────

    @Test
    fun `Connect is disabled with no host`() = dialog {
        portField().performTextInput("8080")
        onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun `Connect is disabled with no port`() = dialog {
        hostField().performTextInput("192.168.1.10")
        onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun `Connect is disabled while the port is not a number`() = dialog {
        hostField().performTextInput("192.168.1.10")
        portField().performTextInput("abc")
        onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun `Connect becomes enabled with a host and a numeric port`() = dialog {
        hostField().performTextInput("192.168.1.10")
        portField().performTextInput("8080")
        onNodeWithText("Connect").assertIsEnabled()
    }

    @Test
    fun `an edit that introduces a non-digit character is rejected outright`() = dialog {
        portField().performTextInput("8080")
        hostField().performTextInput("192.168.1.10")
        onNodeWithText("Connect").assertIsEnabled()

        portField().performTextInput("x")

        // The whole edit is rejected rather than just the bad character, so the field keeps its
        // last valid value and Connect stays enabled on it.
        onNodeWithText("Connect").assertIsEnabled()
    }

    @Test
    fun `a port typed with a non-digit character never reaches a valid state`() = dialog {
        hostField().performTextInput("192.168.1.10")
        portField().performTextInput("8x")

        onNodeWithText("Connect").assertIsNotEnabled()
    }

    // ── Connecting ───────────────────────────────────────────────────────────────

    @Test
    fun `Connect hands back the trimmed host, api key and parsed port`() = dialog { result ->
        hostField().performTextInput("  192.168.1.10  ")
        portField().performTextInput("8080")
        apiKeyField().performTextInput("  secret  ")
        onNodeWithText("Connect").performClick()

        assertEquals("192.168.1.10", result.connected?.primaryHost)
        assertEquals(8080, result.connected?.primaryPort)
        assertEquals("secret", result.connected?.apiKey)
    }

    @Test
    fun `Connect closes the dialog after connecting`() = dialog { result ->
        hostField().performTextInput("192.168.1.10")
        portField().performTextInput("8080")
        onNodeWithText("Connect").performClick()

        assertEquals(1, result.dismissed)
    }

    @Test
    fun `Cancel dismisses without connecting`() = dialog { result ->
        hostField().performTextInput("192.168.1.10")
        onNodeWithText("Cancel").performClick()

        assertNull(result.connected)
        assertEquals(1, result.dismissed)
    }

    @Test
    fun `existing settings pre-fill the fields`() = dialog(
        settings = InstanceLinkSettings(primaryHost = "10.0.0.5", primaryPort = 9090, apiKey = "existing-key"),
    ) { result ->
        onNodeWithText("Connect").performClick()

        assertEquals("10.0.0.5", result.connected?.primaryHost)
        assertEquals(9090, result.connected?.primaryPort)
        assertEquals("existing-key", result.connected?.apiKey)
    }

    // ── Saving without connecting ────────────────────────────────────────────────
    //
    // Connect used to be the only button that persisted anything, so every settings edit — including
    // the ones consumed reactively and needing no handshake at all — cost a reconnect.

    @Test
    fun `Save persists the edits without connecting`() = dialog { result ->
        hostField().performTextInput("192.168.1.10")
        portField().performTextInput("8080")
        onNodeWithText("Save").performClick()

        assertEquals("192.168.1.10", result.saved?.primaryHost)
        assertEquals(8080, result.saved?.primaryPort)
        assertNull(result.connected, "Save must not open a connection")
        assertEquals(1, result.dismissed)
    }

    @Test
    fun `Save is available with no host, unlike Connect`() = dialog { result ->
        // Turning autoConnect back off is a legitimate edit on its own; requiring a reachable
        // primary to record it would make the setting impossible to undo from here.
        onNodeWithText("Connect").assertIsNotEnabled()
        onAllNodes(isToggleable())[0].performClick()
        onNodeWithText("Save").assertIsEnabled().performClick()

        assertEquals(true, result.saved?.autoConnect)
    }

    @Test
    fun `Save keeps the fields the dialog never shows`() = dialog(
        settings = InstanceLinkSettings(deviceId = "device-abc", enabled = true),
    ) { result ->
        onNodeWithText("Save").performClick()

        assertEquals("device-abc", result.saved?.deviceId)
        assertEquals(true, result.saved?.enabled)
    }

    // ── Reconnect delay ──────────────────────────────────────────────────────────

    @Test
    fun `the reconnect delay is pre-filled and editable`() = dialog(
        settings = InstanceLinkSettings(reconnectDelayMs = 3000),
    ) { result ->
        reconnectDelayField().performTextReplacement("5000")
        onNodeWithText("Save").performClick()

        assertEquals(5000, result.saved?.reconnectDelayMs)
    }

    @Test
    fun `a non-digit reconnect delay edit is rejected outright`() = dialog(
        settings = InstanceLinkSettings(reconnectDelayMs = 2000),
    ) { result ->
        reconnectDelayField().performTextInput("x")
        onNodeWithText("Save").performClick()

        assertEquals(2000, result.saved?.reconnectDelayMs)
    }

    @Test
    fun `an emptied reconnect delay falls back to the saved value`() = dialog(
        settings = InstanceLinkSettings(reconnectDelayMs = 2000),
    ) { result ->
        reconnectDelayField().performTextReplacement("")
        onNodeWithText("Save").performClick()

        assertEquals(2000, result.saved?.reconnectDelayMs)
    }

    @Test
    fun `a reconnect delay of zero is clamped to the floor`() = dialog { result ->
        // The setting is the floor of the client's backoff, so 0 would retry in a tight loop
        // against a primary that is most likely still restarting.
        reconnectDelayField().performTextReplacement("0")
        onNodeWithText("Save").performClick()

        assertEquals(INSTANCE_LINK_MIN_RECONNECT_DELAY_MS, result.saved?.reconnectDelayMs)
    }

    @Test
    fun `a mistyped extra digit is clamped to the ceiling`() = dialog { result ->
        reconnectDelayField().performTextReplacement("600000")
        onNodeWithText("Save").performClick()

        assertEquals(INSTANCE_LINK_MAX_RECONNECT_DELAY_MS, result.saved?.reconnectDelayMs)
    }

    @Test
    fun `parseReconnectDelayMs falls back rather than throwing on unusable input`() {
        assertEquals(2000, parseReconnectDelayMs("", fallback = 2000))
        assertEquals(2000, parseReconnectDelayMs("   ", fallback = 2000))
        // Longer than an Int can hold — toIntOrNull is null, not an exception.
        assertEquals(2000, parseReconnectDelayMs("99999999999999", fallback = 2000))
        assertEquals(3000, parseReconnectDelayMs(" 3000 ", fallback = 2000))
        assertEquals(INSTANCE_LINK_MIN_RECONNECT_DELAY_MS, parseReconnectDelayMs("0", fallback = 2000))
        assertEquals(INSTANCE_LINK_MAX_RECONNECT_DELAY_MS, parseReconnectDelayMs("999999", fallback = 2000))
    }

    // ── Disconnect ───────────────────────────────────────────────────────────────

    @Test
    fun `Disconnect is not offered while disconnected`() = dialog(connectionStatus = InstanceLinkStatus.DISCONNECTED) {
        onNodeWithText("Disconnect").assertDoesNotExist()
    }

    @Test
    fun `Disconnect is offered while connected and calls onDisconnect`() =
        dialog(connectionStatus = InstanceLinkStatus.CONNECTED) { result ->
        onNodeWithText("Disconnect").performClick()
        assertEquals(1, result.disconnectCalls)
    }

    @Test
    fun `the primary's schedule count is shown while connected`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteScheduleCount = 7,
    ) {
        onNodeWithText("Primary schedule: 7 item(s)").assertExists()
    }

    // ── Switches ─────────────────────────────────────────────────────────────────

    @Test
    fun `autoConnect starts off by default and can be turned on`() = dialog { result ->
        hostField().performTextInput("h")
        portField().performTextInput("1")
        onAllNodes(isToggleable())[0].assertIsOff().performClick()

        onNodeWithText("Connect").performClick()
        assertEquals(true, result.connected?.autoConnect)
    }

    @Test
    fun `autoConnect reflects an already-enabled setting`() =
        dialog(settings = InstanceLinkSettings(autoConnect = true)) {
        onAllNodes(isToggleable())[0].assertIsOn()
    }

    @Test
    fun `allowPushToSchedule can be turned on`() = dialog { result ->
        hostField().performTextInput("h")
        portField().performTextInput("1")
        onAllNodes(isToggleable())[1].assertIsOff().performClick()

        onNodeWithText("Connect").performClick()
        assertEquals(true, result.connected?.allowPushToSchedule)
    }

    // ── Role ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the role defaults to Controlled`() = dialog {
        onAllNodes(isSelectable())[0].assertIsSelected()
    }

    @Test
    fun `picking Controller switches the role and hides the Controlled-only settings`() = dialog { result ->
        onAllNodes(isSelectable())[1].performClick()
        onNodeWithText("Bible sync").assertDoesNotExist()

        hostField().performTextInput("h")
        portField().performTextInput("1")
        onNodeWithText("Connect").performClick()
        assertEquals(InstanceLinkRole.CONTROLLER, result.connected?.role)
    }

    @Test
    fun `Controlled-only settings are shown for the default role`() = dialog {
        onNodeWithText("Bible sync").assertExists()
        onNodeWithText("Allow adding items to the primary's schedule").assertExists()
    }

    @Test
    fun `pushing to the schedule is hidden for a Controller`() = dialog {
        // A Controller's schedule is its own local one — ScheduleViewModel is not following the
        // primary, so nothing it adds is ever pushed and the switch would be inert.
        onAllNodes(isSelectable())[1].performClick()
        onNodeWithText("Allow adding items to the primary's schedule").assertDoesNotExist()
    }

    @Test
    fun `switching to Controller and back keeps the push setting that was saved`() = dialog(
        settings = InstanceLinkSettings(allowPushToSchedule = true),
    ) { result ->
        onAllNodes(isSelectable())[1].performClick()
        onAllNodes(isSelectable())[0].performClick()

        onNodeWithText("Save").performClick()
        assertEquals(true, result.saved?.allowPushToSchedule)
    }

    // ── Bible sync (Controlled only) ─────────────────────────────────────────────

    @Test
    fun `bible sync defaults to full replica`() = dialog { result ->
        hostField().performTextInput("h")
        portField().performTextInput("1")
        onNodeWithText("Connect").performClick()
        assertEquals(BibleSyncMode.FULL_REPLICA, result.connected?.bibleSyncMode)
    }

    @Test
    fun `picking reference-only bible sync changes what Connect sends`() = dialog { result ->
        onAllNodes(isSelectable())[3].performClick()

        hostField().performTextInput("h")
        portField().performTextInput("1")
        onNodeWithText("Connect").performClick()
        assertEquals(BibleSyncMode.REFERENCE_ONLY, result.connected?.bibleSyncMode)
    }

    @Test
    fun `mirror backgrounds can be turned on`() = dialog { result ->
        hostField().performTextInput("h")
        portField().performTextInput("1")
        onAllNodes(isToggleable())[2].assertIsOff().performClick()

        onNodeWithText("Connect").performClick()
        assertEquals(true, result.connected?.mirrorBackgrounds)
    }

    // ── Last-update age readout ─────────────────────────────────────────────────

    @Test
    fun `formatInstanceLinkAge shows seconds under a minute`() {
        assertEquals("0s", formatInstanceLinkAge(0))
        assertEquals("45s", formatInstanceLinkAge(45))
        assertEquals("59s", formatInstanceLinkAge(59))
    }

    @Test
    fun `formatInstanceLinkAge shows minutes and seconds at and beyond a minute`() {
        assertEquals("1m 0s", formatInstanceLinkAge(60))
        assertEquals("1m 1s", formatInstanceLinkAge(61))
        assertEquals("2m 5s", formatInstanceLinkAge(125))
    }

    @Test
    fun `the age readout is shown while connected once a last-message timestamp exists`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        lastMessageAtMs = System.currentTimeMillis(),
    ) {
        onNodeWithText("Last update", substring = true).assertExists()
    }

    @Test
    fun `the age readout is absent without a last-message timestamp`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
    ) {
        onNodeWithText("Last update", substring = true).assertDoesNotExist()
    }

    // ── Last-received summary (remoteLiveState) ─────────────────────────────────

    @Test
    fun `the last-received line is absent without remote live state`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
    ) {
        onNodeWithText("Last received", substring = true).assertDoesNotExist()
    }

    @Test
    fun `BIBLE with a book name shows book, chapter and verse`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "BIBLE", bookName = "John", chapter = 3, verseNumber = 16),
    ) {
        onNodeWithText("Last received: John 3:16").assertExists()
    }

    @Test
    fun `BIBLE without a book name falls back to the generic Bible label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "BIBLE"),
    ) {
        onNodeWithText("Last received: Bible").assertExists()
    }

    @Test
    fun `LYRICS shows the song title`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "LYRICS", songTitle = "Amazing Grace"),
    ) {
        onNodeWithText("Last received: Amazing Grace").assertExists()
    }

    @Test
    fun `LYRICS without a title falls back to the generic Songs label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "LYRICS"),
    ) {
        onNodeWithText("Last received: Songs").assertExists()
    }

    @Test
    fun `PICTURES shows the generic Pictures label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "PICTURES"),
    ) {
        onNodeWithText("Last received: Pictures").assertExists()
    }

    @Test
    fun `PRESENTATION shows the generic Presentation label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "PRESENTATION"),
    ) {
        onNodeWithText("Last received: Presentation").assertExists()
    }

    @Test
    fun `MEDIA shows the filename from the URL`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "MEDIA", mediaUrl = "https://example.com/videos/clip.mp4"),
    ) {
        onNodeWithText("Last received: clip.mp4").assertExists()
    }

    @Test
    fun `MEDIA without a URL falls back to the generic Media label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "MEDIA"),
    ) {
        onNodeWithText("Last received: Media").assertExists()
    }

    @Test
    fun `ANNOUNCEMENTS shows the announcement text truncated to 40 chars`() {
        val longText = "Service starts in five minutes, please take your seats"
        dialog(
            connectionStatus = InstanceLinkStatus.CONNECTED,
            remoteLiveState = LiveStateDto(contentType = "ANNOUNCEMENTS", announcementText = longText),
        ) {
            onNodeWithText("Last received: ${longText.take(40)}").assertExists()
        }
    }

    @Test
    fun `ANNOUNCEMENTS without text falls back to the generic Announcements label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "ANNOUNCEMENTS"),
    ) {
        onNodeWithText("Last received: Announcements").assertExists()
    }

    @Test
    fun `WEBSITE prefers the page title over the URL`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(
            contentType = "WEBSITE",
            websiteTitle = "Church Home",
            websiteUrl = "https://church.example",
        ),
    ) {
        onNodeWithText("Last received: Church Home").assertExists()
    }

    @Test
    fun `WEBSITE falls back to the URL when there is no title`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "WEBSITE", websiteUrl = "https://church.example"),
    ) {
        onNodeWithText("Last received: https://church.example").assertExists()
    }

    @Test
    fun `WEBSITE falls back to the generic Website label when both are absent`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "WEBSITE"),
    ) {
        onNodeWithText("Last received: Website").assertExists()
    }

    @Test
    fun `CANVAS shows the scene name`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "CANVAS", sceneName = "Welcome Scene"),
    ) {
        onNodeWithText("Last received: Welcome Scene").assertExists()
    }

    @Test
    fun `CANVAS without a scene name falls back to the generic Canvas label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "CANVAS"),
    ) {
        onNodeWithText("Last received: Canvas").assertExists()
    }

    @Test
    fun `QA shows the question text truncated to 40 chars`() {
        val longText = "What time does the second service start on Sundays?"
        dialog(
            connectionStatus = InstanceLinkStatus.CONNECTED,
            remoteLiveState = LiveStateDto(contentType = "QA", questionText = longText),
        ) {
            onNodeWithText("Last received: ${longText.take(40)}").assertExists()
        }
    }

    @Test
    fun `QA without text falls back to the generic Q&A label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "QA"),
    ) {
        onNodeWithText("Last received: Q&A").assertExists()
    }

    @Test
    fun `DICTIONARY shows the word`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "DICTIONARY", dictionaryWord = "agape"),
    ) {
        onNodeWithText("Last received: agape").assertExists()
    }

    @Test
    fun `DICTIONARY without a word falls back to the generic Dictionary label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "DICTIONARY"),
    ) {
        onNodeWithText("Last received: Dictionary").assertExists()
    }

    @Test
    fun `LOWER_THIRD shows the generic Lower Third label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "LOWER_THIRD"),
    ) {
        onNodeWithText("Last received: Lower Third").assertExists()
    }

    @Test
    fun `NONE shows the generic Clear Display label`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "NONE"),
    ) {
        onNodeWithText("Last received: Clear Display").assertExists()
    }

    @Test
    fun `an unrecognized content type is shown verbatim`() = dialog(
        connectionStatus = InstanceLinkStatus.CONNECTED,
        remoteLiveState = LiveStateDto(contentType = "SOMETHING_NEW"),
    ) {
        onNodeWithText("Last received: SOMETHING_NEW").assertExists()
    }

    // ── InstanceLinkDialog (outer wrapper) ──────────────────────────────────────

    @Test
    fun `InstanceLinkDialog renders nothing when not visible`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                InstanceLinkDialog(
                    isVisible = false,
                    settings = InstanceLinkSettings(),
                    connectionStatus = InstanceLinkStatus.DISCONNECTED,
                    remoteLiveState = null,
                    remoteScheduleCount = 0,
                    onConnect = {},
                    onSave = {},
                    onDisconnect = {},
                    onDismiss = {},
                )
            }
        }
        onNodeWithText("Instance Link", substring = true).assertDoesNotExist()
    }
}

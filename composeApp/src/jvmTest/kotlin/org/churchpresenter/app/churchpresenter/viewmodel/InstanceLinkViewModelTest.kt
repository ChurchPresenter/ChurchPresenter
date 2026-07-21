package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.server.InstanceLinkClient
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.LiveStateDto
import org.churchpresenter.app.churchpresenter.server.ScheduleItemDto
import org.churchpresenter.app.churchpresenter.server.SongCatalogResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The follower/controller side of Instance Link: everything the primary broadcasts, turned into
 * typed state for the rest of the app, and every controller command forwarded to the primary.
 *
 * This view model deliberately holds no reference to `PresenterManager` or any other view model —
 * MainDesktop observes these flows and drives the local presenter — so the flows *are* the
 * contract. Two details in it are easy to get wrong and invisible until a service: the liveness
 * fields (`lastMessageAtMs`, `nextRetryAtMs`) are cleared on specific status transitions and not
 * others, and the cache-invalidation signals are counters rather than booleans precisely so a
 * second identical broadcast still wakes observers up.
 *
 * `InstanceLinkClient` is replaced with `mockkConstructor`, so no WebSocket is opened. The primary's
 * broadcasts are simulated by invoking the callbacks the view model handed to that client — they
 * are wired in a private constructor call, so this is the only way to reach the translation logic
 * without a second running instance of the whole app.
 */
class InstanceLinkViewModelTest {

    private val created = mutableListOf<InstanceLinkViewModel>()

    @BeforeTest
    fun stubClient() {
        mockkConstructor(InstanceLinkClient::class)
        every { anyConstructed<InstanceLinkClient>().connect(any(), any(), any(), any(), any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().disconnect() } returns Unit
        every { anyConstructed<InstanceLinkClient>().dispose() } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendAddToSchedule(any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendRemoveFromSchedule(any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendProject(any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendSelectBibleVerse(any(), any(), any(), any(), any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendSelectPicture(any(), any(), any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendSelectSongSection(any(), any(), any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendSelectSlide(any(), any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendClear() } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendBibleHold(any()) } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendNextPicture() } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendPreviousPicture() } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendNextSlide() } returns Unit
        every { anyConstructed<InstanceLinkClient>().sendPreviousSlide() } returns Unit
        every { anyConstructed<InstanceLinkClient>().mediaStreamUrl(any()) } returns null
        coEvery { anyConstructed<InstanceLinkClient>().fetchSongDetail(any(), any()) } returns null
        coEvery { anyConstructed<InstanceLinkClient>().fetchPictureImageBytes(any(), any()) } returns null
        coEvery { anyConstructed<InstanceLinkClient>().fetchPresentationSlideBytes(any(), any()) } returns null
        coEvery { anyConstructed<InstanceLinkClient>().fetchBibleFile() } returns null
        coEvery { anyConstructed<InstanceLinkClient>().fetchSecondaryBibleFile() } returns null
        coEvery { anyConstructed<InstanceLinkClient>().fetchLowerThirdJson(any()) } returns null
        coEvery { anyConstructed<InstanceLinkClient>().fetchBackgroundSettings() } returns null
        coEvery { anyConstructed<InstanceLinkClient>().fetchBackgroundAsset(any(), any()) } returns null
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkConstructor(InstanceLinkClient::class)
    }

    private fun vm(): InstanceLinkViewModel = InstanceLinkViewModel().also { created.add(it) }

    // ── Simulating the primary's broadcasts ─────────────────────────────────────

    /** One of the callbacks the view model handed to its client. */
    private fun <T> callback(vm: InstanceLinkViewModel, name: String): T {
        val clientField = InstanceLinkViewModel::class.java.getDeclaredField("client").apply { isAccessible = true }
        val client = clientField.get(vm)
        val field = InstanceLinkClient::class.java.getDeclaredField(name).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return field.get(client) as T
    }

    private fun reportStatus(vm: InstanceLinkViewModel, status: InstanceLinkStatus) =
        callback<(InstanceLinkStatus) -> Unit>(vm, "onStatusChanged")(status)

    private fun broadcastSchedule(vm: InstanceLinkViewModel, items: List<ScheduleItemDto>) =
        callback<(List<ScheduleItemDto>) -> Unit>(vm, "onScheduleUpdated")(items)

    private fun broadcastLiveState(vm: InstanceLinkViewModel, state: LiveStateDto) =
        callback<(LiveStateDto) -> Unit>(vm, "onLiveStateUpdated")(state)

    private fun broadcastSongs(vm: InstanceLinkViewModel, catalog: SongCatalogResponse) =
        callback<(SongCatalogResponse) -> Unit>(vm, "onSongsUpdated")(catalog)

    private fun broadcastSongSection(vm: InstanceLinkViewModel, index: Int) =
        callback<(Int) -> Unit>(vm, "onSongSectionSelected")(index)

    private fun broadcastSlide(vm: InstanceLinkViewModel, id: String, index: Int, total: Int, isPlaying: Boolean, isLive: Boolean) =
        callback<(String, Int, Int, Boolean, Boolean) -> Unit>(vm, "onPresentationSlideChanged")(id, index, total, isPlaying, isLive)

    private fun signal(vm: InstanceLinkViewModel, name: String) = callback<() -> Unit>(vm, name)()

    private fun reportMessage(vm: InstanceLinkViewModel) = signal(vm, "onMessageReceived")

    private fun reportReconnectIn(vm: InstanceLinkViewModel, delayMs: Long) =
        callback<(Long) -> Unit>(vm, "onReconnectScheduled")(delayMs)

    private fun reportCommandFailed(vm: InstanceLinkViewModel, type: String, reason: String?) =
        callback<(String, String?) -> Unit>(vm, "onCommandFailed")(type, reason)

    private fun reportNoAck(vm: InstanceLinkViewModel) = signal(vm, "onCommandNoAck")

    private fun scheduleItem(id: String, type: String = "song", displayText: String = id) =
        ScheduleItemDto(id = id, type = type, displayText = displayText)

    // ── Starting state ──────────────────────────────────────────────────────────

    @Test
    fun `a new link is disconnected with nothing mirrored`() {
        val vm = vm()
        assertEquals(InstanceLinkStatus.DISCONNECTED, vm.connectionStatus.value)
        assertTrue(vm.remoteSchedule.value.isEmpty())
        assertNull(vm.remoteSongCatalog.value)
        assertNull(vm.remoteLiveState.value)
        assertNull(vm.remoteSongSectionIndex.value)
        assertNull(vm.remotePresentationSlide.value)
        assertNull(vm.lastMessageAtMs.value, "no message has arrived yet")
        assertNull(vm.nextRetryAtMs.value, "nothing is being retried yet")
    }

    // ── Connection status and liveness ──────────────────────────────────────────

    @Test
    fun `the reported status is published`() {
        val vm = vm()
        reportStatus(vm, InstanceLinkStatus.CONNECTING)
        assertEquals(InstanceLinkStatus.CONNECTING, vm.connectionStatus.value)
        reportStatus(vm, InstanceLinkStatus.CONNECTED)
        assertEquals(InstanceLinkStatus.CONNECTED, vm.connectionStatus.value)
    }

    @Test
    fun `a scheduled retry is published as an absolute deadline`() {
        val vm = vm()
        val before = System.currentTimeMillis()

        reportReconnectIn(vm, 5_000L)

        val deadline = assertNotNull(vm.nextRetryAtMs.value)
        assertTrue(
            deadline in (before + 5_000)..(System.currentTimeMillis() + 5_000),
            "the badge counts down to this instant; got $deadline"
        )
    }

    @Test
    fun `an error keeps the pending retry deadline`() {
        val vm = vm()
        reportReconnectIn(vm, 5_000L)

        reportStatus(vm, InstanceLinkStatus.ERROR)

        assertNotNull(vm.nextRetryAtMs.value, "the 'reconnecting in Xs' badge must survive the error that caused it")
    }

    @Test
    fun `any other status clears the pending retry deadline`() {
        val vm = vm()
        listOf(InstanceLinkStatus.CONNECTING, InstanceLinkStatus.CONNECTED, InstanceLinkStatus.DISCONNECTED).forEach { status ->
            reportReconnectIn(vm, 5_000L)
            reportStatus(vm, status)
            assertNull(vm.nextRetryAtMs.value, "a stale countdown next to a $status link reads as still failing")
        }
    }

    @Test
    fun `a decoded message records when it arrived`() {
        val vm = vm()
        val before = System.currentTimeMillis()

        reportMessage(vm)

        val at = assertNotNull(vm.lastMessageAtMs.value)
        assertTrue(at in before..System.currentTimeMillis(), "drives the 'last update Xs ago' readout; got $at")
    }

    @Test
    fun `staying connected keeps the last-message time`() {
        val vm = vm()
        reportMessage(vm)

        reportStatus(vm, InstanceLinkStatus.CONNECTED)

        assertNotNull(vm.lastMessageAtMs.value, "a status re-report must not look like the link went quiet")
    }

    @Test
    fun `dropping the connection clears the last-message time`() {
        val vm = vm()
        reportMessage(vm)

        reportStatus(vm, InstanceLinkStatus.DISCONNECTED)

        assertNull(vm.lastMessageAtMs.value, "'last update 2s ago' beside a dead link is worse than no readout")
    }

    @Test
    fun `an error also clears the last-message time`() {
        val vm = vm()
        reportMessage(vm)
        reportStatus(vm, InstanceLinkStatus.ERROR)
        assertNull(vm.lastMessageAtMs.value)
    }

    // ── Mirrored content ────────────────────────────────────────────────────────

    @Test
    fun `the primary's schedule is published`() {
        val vm = vm()
        broadcastSchedule(vm, listOf(scheduleItem("a"), scheduleItem("b")))
        assertEquals(listOf("a", "b"), vm.remoteSchedule.value.map { it.id })
    }

    @Test
    fun `each schedule broadcast replaces the last`() {
        val vm = vm()
        broadcastSchedule(vm, listOf(scheduleItem("a"), scheduleItem("b")))
        broadcastSchedule(vm, listOf(scheduleItem("c")))
        assertEquals(listOf("c"), vm.remoteSchedule.value.map { it.id })
    }

    @Test
    fun `the primary clearing its schedule clears the mirror`() {
        val vm = vm()
        broadcastSchedule(vm, listOf(scheduleItem("a")))
        broadcastSchedule(vm, emptyList())
        assertTrue(vm.remoteSchedule.value.isEmpty())
    }

    @Test
    fun `what is live on the primary is published`() {
        val vm = vm()
        broadcastLiveState(vm, LiveStateDto(contentType = "BIBLE", bookName = "John", chapter = 3, verseNumber = 16))
        val state = assertNotNull(vm.remoteLiveState.value)
        assertEquals("BIBLE", state.contentType)
        assertEquals("John", state.bookName)
        assertEquals(3, state.chapter)
        assertEquals(16, state.verseNumber)
    }

    @Test
    fun `the primary's song catalog is published`() {
        val vm = vm()
        broadcastSongs(vm, SongCatalogResponse(songBook = emptyList(), songBooks = 2, total = 120))
        val catalog = assertNotNull(vm.remoteSongCatalog.value)
        assertEquals(2, catalog.songBooks)
        assertEquals(120, catalog.total)
    }

    @Test
    fun `the section the primary is on is published`() {
        val vm = vm()
        broadcastSongSection(vm, 3)
        assertEquals(3, vm.remoteSongSectionIndex.value)
        broadcastSongSection(vm, 0)
        assertEquals(0, vm.remoteSongSectionIndex.value, "the first section is a real value, not 'nothing selected'")
    }

    @Test
    fun `the slide the primary is on is published in full`() {
        val vm = vm()

        broadcastSlide(vm, id = "deck-1", index = 4, total = 12, isPlaying = true, isLive = true)

        val slide = assertNotNull(vm.remotePresentationSlide.value)
        assertEquals("deck-1", slide.id)
        assertEquals(4, slide.index)
        assertEquals(12, slide.total)
        assertTrue(slide.isPlaying)
        assertTrue(slide.isLive)
    }

    @Test
    fun `a slide that is staged rather than live says so`() {
        val vm = vm()
        broadcastSlide(vm, id = "deck-1", index = 0, total = 3, isPlaying = false, isLive = false)
        val slide = assertNotNull(vm.remotePresentationSlide.value)
        assertFalse(slide.isLive, "a follower must not put a merely-staged slide on its own screen")
        assertFalse(slide.isPlaying)
    }

    // ── Signals ─────────────────────────────────────────────────────────────────

    @Test
    fun `a repeated clear still reaches observers`() {
        val vm = vm()
        val start = vm.displayClearedSignal.value

        repeat(3) { signal(vm, "onDisplayCleared") }

        assertEquals(
            start + 3,
            vm.displayClearedSignal.value,
            "a boolean would go true once and never notify again on the second clear in a row"
        )
    }

    @Test
    fun `each cache-invalidation signal counts on its own`() {
        val vm = vm()

        signal(vm, "onBibleUpdated")
        signal(vm, "onBibleUpdated")
        signal(vm, "onSecondaryBibleUpdated")
        signal(vm, "onPicturesUpdated")
        signal(vm, "onBackgroundsUpdated")

        assertEquals(2, vm.bibleUpdatedSignal.value)
        assertEquals(1, vm.secondaryBibleUpdatedSignal.value)
        assertEquals(1, vm.picturesUpdatedSignal.value)
        assertEquals(1, vm.backgroundsUpdatedSignal.value)
    }

    @Test
    fun `one cache signal does not disturb the others`() {
        val vm = vm()
        signal(vm, "onPicturesUpdated")
        assertEquals(0, vm.bibleUpdatedSignal.value, "re-downloading a bible because pictures changed is wasted work")
        assertEquals(0, vm.secondaryBibleUpdatedSignal.value)
        assertEquals(0, vm.backgroundsUpdatedSignal.value)
        assertEquals(0, vm.displayClearedSignal.value)
    }

    // ── Command failures ────────────────────────────────────────────────────────

    /** Collects everything emitted while [block] runs. Unconfined so the collector subscribes before it does. */
    private fun collectFailures(vm: InstanceLinkViewModel, block: () -> Unit): List<InstanceLinkCommandFailure> = runBlocking {
        val seen = mutableListOf<InstanceLinkCommandFailure>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { vm.commandFailures.collect { seen.add(it) } }
        block()
        job.cancel()
        seen
    }

    @Test
    fun `a rejected command is surfaced with its reason`() {
        val vm = vm()

        val failures = collectFailures(vm) { reportCommandFailed(vm, "project", "not permitted") }

        val failure = failures.single()
        assertEquals("project", failure.commandType)
        assertEquals("not permitted", failure.reason)
        assertFalse(failure.soft, "a refusal is a hard failure the operator needs to see")
    }

    @Test
    fun `a command that could not be delivered is surfaced without a reason`() {
        val vm = vm()
        val failures = collectFailures(vm) { reportCommandFailed(vm, "next_slide", null) }
        assertEquals("next_slide", failures.single().commandType)
        assertNull(failures.single().reason)
    }

    @Test
    fun `a primary that never acknowledges is a soft notice`() {
        val vm = vm()

        val failures = collectFailures(vm) { reportNoAck(vm) }

        val failure = failures.single()
        assertTrue(failure.soft, "likely just an older primary — it must not read as a hard refusal")
        assertEquals("", failure.commandType)
        assertNull(failure.reason)
    }

    @Test
    fun `every failure is surfaced, not just the first`() {
        val vm = vm()

        val failures = collectFailures(vm) {
            reportCommandFailed(vm, "project", "rejected")
            reportCommandFailed(vm, "clear", "rejected")
            reportNoAck(vm)
        }

        assertEquals(listOf("project", "clear", ""), failures.map { it.commandType })
    }

    // ── Forwarding to the primary ───────────────────────────────────────────────

    @Test
    fun `connecting and disconnecting reach the client`() {
        val vm = vm()

        vm.connect(host = "10.0.0.9", port = 8765, apiKey = "secret", deviceId = "follower-1", reconnectDelayMs = 3_000L)
        vm.disconnect()

        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().connect("10.0.0.9", 8765, "secret", "follower-1", 3_000L) }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().disconnect() }
    }

    @Test
    fun `schedule pushes reach the primary`() {
        val vm = vm()
        val item = ScheduleItem.SongItem(id = "s1", songNumber = 1, title = "Amazing Grace", songbook = "Hymnal")

        vm.sendAddToSchedule(item)
        vm.sendRemoveFromSchedule("s1")

        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendAddToSchedule(item) }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendRemoveFromSchedule("s1") }
    }

    @Test
    fun `going live as a controller reaches the primary`() {
        val vm = vm()
        val item = ScheduleItem.SongItem(id = "s1", songNumber = 1, title = "Amazing Grace", songbook = "Hymnal")

        vm.sendProject(item)

        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendProject(item) }
    }

    @Test
    fun `each navigation command is forwarded as itself`() {
        // Guards the copy-paste hazard: four near-identical one-liners in a row.
        val vm = vm()

        vm.sendNextPicture()
        vm.sendPreviousPicture()
        vm.sendNextSlide()
        vm.sendPreviousSlide()

        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendNextPicture() }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendPreviousPicture() }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendNextSlide() }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendPreviousSlide() }
    }

    @Test
    fun `selection commands carry their arguments through`() {
        val vm = vm()

        vm.sendSelectBibleVerse("John", 3, 16, "For God so loved the world", "16-18")
        vm.sendSelectPicture("folder-1", 4, "photo.jpg")
        vm.sendSelectSlide("deck-1", 2)
        vm.sendClear()
        vm.sendBibleHold(true)

        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendSelectBibleVerse("John", 3, 16, "For God so loved the world", "16-18") }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendSelectPicture("folder-1", 4, "photo.jpg") }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendSelectSlide("deck-1", 2) }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendClear() }
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendBibleHold(true) }
    }

    @Test
    fun `selecting a song section defaults to the whole section`() {
        val vm = vm()

        vm.sendSelectSongSection("123", 2)

        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendSelectSongSection("123", 2, -1) }
    }

    @Test
    fun `selecting a single line of a section passes the line through`() {
        val vm = vm()
        vm.sendSelectSongSection("123", 2, 4)
        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().sendSelectSongSection("123", 2, 4) }
    }

    @Test
    fun `a media stream url comes straight from the client`() {
        val vm = vm()
        every { anyConstructed<InstanceLinkClient>().mediaStreamUrl("media-1") } returns "http://10.0.0.9:8765/media/media-1"

        assertEquals("http://10.0.0.9:8765/media/media-1", vm.mediaStreamUrl("media-1"))
    }

    @Test
    fun `an unknown media id has no stream url`() {
        val vm = vm()
        assertNull(vm.mediaStreamUrl("nope"))
    }

    @Test
    fun `fetched assets are returned as the client provided them`() = runBlocking {
        val vm = vm()
        val bytes = byteArrayOf(1, 2, 3)
        coEvery { anyConstructed<InstanceLinkClient>().fetchPictureImageBytes("folder-1", 2) } returns bytes
        coEvery { anyConstructed<InstanceLinkClient>().fetchPresentationSlideBytes("deck-1", 5) } returns bytes
        coEvery { anyConstructed<InstanceLinkClient>().fetchBibleFile() } returns bytes
        coEvery { anyConstructed<InstanceLinkClient>().fetchSecondaryBibleFile() } returns bytes
        coEvery { anyConstructed<InstanceLinkClient>().fetchLowerThirdJson("Welcome") } returns bytes
        coEvery { anyConstructed<InstanceLinkClient>().fetchBackgroundAsset("bible", true) } returns bytes

        assertEquals(bytes, vm.fetchPictureImageBytes("folder-1", 2))
        assertEquals(bytes, vm.fetchPresentationSlideBytes("deck-1", 5))
        assertEquals(bytes, vm.fetchBibleFile())
        assertEquals(bytes, vm.fetchSecondaryBibleFile())
        assertEquals(bytes, vm.fetchLowerThirdJson("Welcome"))
        assertEquals(bytes, vm.fetchBackgroundAsset("bible", true))
    }

    @Test
    fun `background settings are fetched from the primary`() = runBlocking {
        val vm = vm()
        val settings = BackgroundSettings()
        coEvery { anyConstructed<InstanceLinkClient>().fetchBackgroundSettings() } returns settings

        assertEquals(settings, vm.fetchBackgroundSettings())
        coVerify(exactly = 1) { anyConstructed<InstanceLinkClient>().fetchBackgroundSettings() }
    }

    @Test
    fun `a song's lyrics are fetched on demand`() = runBlocking {
        val vm = vm()

        vm.fetchSongDetail("123", "Hymnal")

        coVerify(exactly = 1) { anyConstructed<InstanceLinkClient>().fetchSongDetail("123", "Hymnal") }
    }

    @Test
    fun `disposing shuts the client down`() {
        val vm = InstanceLinkViewModel()

        vm.dispose()

        verify(exactly = 1) { anyConstructed<InstanceLinkClient>().dispose() }
    }
}

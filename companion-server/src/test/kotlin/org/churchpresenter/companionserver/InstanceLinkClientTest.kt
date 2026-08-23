package org.churchpresenter.companionserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [InstanceLinkClient] against a REAL [CompanionServer] — it is, byte for byte, the same `/ws`
 * protocol and REST surface a mobile companion already speaks (same route, same headers), so
 * there is no fake to write: the primary this class already has extensive test coverage for
 * (`CompanionServerTest`, `CompanionServerRemoteControlTest`) plays the other end directly.
 *
 * Instant commands ([InstanceLinkClient.sendClear] and friends) ack immediately and their real
 * effect is observed via the server's own `MutableSharedFlow`s, using the same `collecting` /
 * subscription-count-wait pattern `CompanionServerRemoteControlTest` established (a flow with no
 * replay dropped before a collector attaches is a real, hard-to-diagnose race otherwise).
 *
 * Approval-gated commands ([InstanceLinkClient.sendProject]/[sendAddToSchedule]) ack
 * `pending_approval` immediately regardless of what the operator later decides — the actual
 * decision arrives out-of-band (a schedule_updated broadcast, or nothing) — so what is asserted
 * here is that the operator is asked at all with the right item, not the eventual outcome.
 */
class InstanceLinkClientTest {

    private lateinit var server: CompanionServer
    private var port: Int = 0
    private var operatorScope: CoroutineScope? = null

    private class Callbacks {
        var status: InstanceLinkStatus? = null
        val statusHistory = mutableListOf<InstanceLinkStatus>()
        var songs: SongCatalogResponse? = null
        var scheduleUpdates = 0
        var backgroundsUpdated = 0
        var bibleUpdated = 0
        val commandFailures = mutableListOf<Pair<String, String?>>()
        var commandNoAck = 0

        fun client() = InstanceLinkClient(
            onStatusChanged = { status = it; statusHistory.add(it) },
            onScheduleUpdated = { scheduleUpdates++ },
            onLiveStateUpdated = {},
            onDisplayCleared = {},
            onSongSectionSelected = {},
            onPresentationSlideChanged = { _, _, _, _, _ -> },
            onSongsUpdated = { songs = it },
            onBibleUpdated = { bibleUpdated++ },
            onBackgroundsUpdated = { backgroundsUpdated++ },
            onCommandFailed = { type, reason -> commandFailures.add(type to reason) },
            onCommandNoAck = { commandNoAck++ },
        )
    }

    @BeforeTest
    fun startServer() {
        server = CompanionServer()
        server.start(port = testPort(39_780))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
        server.updateSongs(emptyList())
        server.updateSchedule(emptyList())
    }

    @AfterTest
    fun stopServer() {
        runCatching { operatorScope?.cancel() }
        operatorScope = null
        runCatching { server.stop() }
    }

    /** Starts collecting [flow] and does not return until the collector is actually subscribed —
     *  see the class doc comment for why that wait matters for a no-replay SharedFlow. */
    private fun <T> collecting(flow: MutableSharedFlow<T>, onEach: (T) -> Unit) {
        val scope = operatorScope ?: CoroutineScope(Dispatchers.IO).also { operatorScope = it }
        scope.launch { flow.collect { onEach(it) } }
        runBlocking {
            withTimeoutOrNull(5_000) { flow.subscriptionCount.first { it > 0 } }
                ?: error("collector never subscribed")
        }
    }

    private fun connectedClient(callbacks: Callbacks = Callbacks()): Pair<InstanceLinkClient, Callbacks> {
        val client = callbacks.client()
        client.connect(
            host = "127.0.0.1",
            port = port,
            apiKey = "",
            deviceId = "test-device",
            reconnectDelayMs = 60_000,
        )
        awaitUntil("CONNECTED") { callbacks.status == InstanceLinkStatus.CONNECTED }
        return client to callbacks
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    // ── Connect lifecycle ──────────────────────────────────────────────────────

    @Test
    fun `connecting reaches CONNECTED and passes through CONNECTING first`() {
        val (client, callbacks) = connectedClient()
        try {
            assertTrue(InstanceLinkStatus.CONNECTING in callbacks.statusHistory)
            assertEquals(InstanceLinkStatus.CONNECTED, callbacks.status)
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `the connect snapshot delivers the song catalog and a schedule update`() {
        server.updateSongs(listOf(SongItem(number = "1", title = "Amazing Grace", songbook = "Hymnal")))
        val (client, callbacks) = connectedClient()
        try {
            awaitUntil("songs snapshot") { callbacks.songs != null }
            assertEquals(1, callbacks.songs?.total)
            awaitUntil("schedule snapshot") { callbacks.scheduleUpdates > 0 }
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `a loaded Bible resends bible_updated on every connect`() {
        val dir = Files.createTempDirectory("cp-instance-link-bible-test").toFile()
        server.updateBible(SpbFixture.loadedBible(dir), "KJV")
        val (client, callbacks) = connectedClient()
        try {
            awaitUntil("bible_updated") { callbacks.bibleUpdated > 0 }
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `disconnect reports DISCONNECTED and stops further updates`() {
        val (client, callbacks) = connectedClient()
        client.disconnect()
        awaitUntil("DISCONNECTED") { callbacks.status == InstanceLinkStatus.DISCONNECTED }

        val scheduleUpdatesAtDisconnect = callbacks.scheduleUpdates
        server.updateSchedule(listOf(ScheduleItem.LabelItem(
            id = "l1",
            text = "Offering",
            textColor = "#FFFFFF",
            backgroundColor = "#000000",
        )))
        Thread.sleep(200) // a bounded settle window to prove a *negative* — nothing more arrives
        assertEquals(
            scheduleUpdatesAtDisconnect,
            callbacks.scheduleUpdates,
            "a disconnected client must not keep receiving broadcasts",
        )
        client.dispose()
    }

    // ── Instant commands ───────────────────────────────────────────────────────

    @Test
    fun `sendClear reaches the primary's onClear flow`() {
        val (client, _) = connectedClient()
        try {
            var cleared = false
            collecting(server.onClear) { cleared = true }
            client.sendClear()
            awaitUntil("onClear to fire") { cleared }
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `sendSelectBibleVerse reaches the primary with the exact reference sent`() {
        val (client, _) = connectedClient()
        try {
            val received = mutableListOf<SelectBibleVerseRequest>()
            collecting(server.onSelectBibleVerse) { received.add(it) }
            client.sendSelectBibleVerse("John", 3, 16, "For God so loved the world.", "")
            awaitUntil("onSelectBibleVerse") { received.isNotEmpty() }
            assertEquals(SelectBibleVerseRequest("John", 3, 16, "For God so loved the world.", ""), received.single())
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `sendSelectPicture reaches the primary with the exact selection sent`() {
        val (client, _) = connectedClient()
        try {
            val received = mutableListOf<SelectPictureRequest>()
            collecting(server.onSelectPicture) { received.add(it) }
            client.sendSelectPicture("folder-1", 2, "sunset.jpg")
            awaitUntil("onSelectPicture") { received.isNotEmpty() }
            assertEquals(SelectPictureRequest("folder-1", 2, "sunset.jpg"), received.single())
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `sendSelectSongSection reaches the primary with the exact section sent`() {
        val (client, _) = connectedClient()
        try {
            val received = mutableListOf<SelectSongSectionRequest>()
            collecting(server.onSelectSongSection) { received.add(it) }
            client.sendSelectSongSection("42", 1, lineIndex = 2)
            awaitUntil("onSelectSongSection") { received.isNotEmpty() }
            assertEquals(SelectSongSectionRequest("42", 1, 2), received.single())
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `sendSelectSlide reaches the primary with the exact slide sent`() {
        val (client, _) = connectedClient()
        try {
            val received = mutableListOf<SelectSlideRequest>()
            collecting(server.onSelectSlide) { received.add(it) }
            client.sendSelectSlide("pres-1", 4)
            awaitUntil("onSelectSlide") { received.isNotEmpty() }
            assertEquals(SelectSlideRequest("pres-1", 4), received.single())
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `sendBibleHold reaches the primary with the exact flag sent`() {
        val (client, _) = connectedClient()
        try {
            val received = mutableListOf<Boolean>()
            collecting(server.onBibleHold) { received.add(it) }
            client.sendBibleHold(true)
            awaitUntil("onBibleHold") { received.isNotEmpty() }
            assertEquals(listOf(true), received)
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `the next-previous picture and slide commands each reach their own primary flow`() {
        val (client, _) = connectedClient()
        try {
            var nextPicture = false; var prevPicture = false; var nextSlide = false; var prevSlide = false
            collecting(server.onNextPicture) { nextPicture = true }
            collecting(server.onPreviousPicture) { prevPicture = true }
            collecting(server.onNextSlide) { nextSlide = true }
            collecting(server.onPreviousSlide) { prevSlide = true }

            client.sendNextPicture()
            awaitUntil("onNextPicture") { nextPicture }
            client.sendPreviousPicture()
            awaitUntil("onPreviousPicture") { prevPicture }
            client.sendNextSlide()
            awaitUntil("onNextSlide") { nextSlide }
            client.sendPreviousSlide()
            awaitUntil("onPreviousSlide") { prevSlide }
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `sendRemoveFromSchedule asks the operator with the exact item id sent`() {
        val (client, _) = connectedClient()
        try {
            val asked = mutableListOf<String>()
            collecting(server.onRemoveFromSchedule) { asked.add(it.id); it.decision.complete(true) }
            client.sendRemoveFromSchedule("item-9")
            awaitUntil("the operator to be asked") { asked.isNotEmpty() }
            assertEquals("item-9", asked.single())
        } finally {
            client.dispose()
        }
    }

    // ── Approval-gated commands ────────────────────────────────────────────────

    @Test
    fun `sendProject asks the operator with the exact item sent`() {
        val (client, callbacks) = connectedClient()
        try {
            val asked = mutableListOf<ScheduleItem>()
            collecting(server.onProject) { asked.add(it.item); it.decision.complete(true) }

            val item = ScheduleItem.LabelItem(
                id = "proj-1",
                text = "Welcome",
                textColor = "#FFFFFF",
                backgroundColor = "#000000",
            )
            client.sendProject(item)

            awaitUntil("the operator to be asked") { asked.isNotEmpty() }
            assertEquals(item, asked.single())
            assertTrue(
                callbacks.commandFailures.isEmpty(),
                "an approval-gated command acks pending_approval, not a failure",
            )
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `sendAddToSchedule asks the operator with the exact item sent`() {
        val (client, _) = connectedClient()
        try {
            val asked = mutableListOf<ScheduleItem>()
            collecting(server.onAddToSchedule) { asked.add(it.item); it.decision.complete(true) }

            val item = ScheduleItem.LabelItem(
                id = "add-1",
                text = "Offering",
                textColor = "#FFFFFF",
                backgroundColor = "#000000",
            )
            client.sendAddToSchedule(item)

            awaitUntil("the operator to be asked") { asked.isNotEmpty() }
            assertEquals(item, asked.single())
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `sendCommand before ever connecting reports not_connected without touching the network`() {
        val callbacks = Callbacks()
        val client = callbacks.client()
        client.sendClear()
        assertEquals(listOf(Constants.WS_CMD_CLEAR to ("not_connected" as String?)), callbacks.commandFailures)
        client.dispose()
    }

    // ── REST fetches ───────────────────────────────────────────────────────────

    @Test
    fun `fetchSongDetail returns the primary's song, sections included`() {
        server.updateSongs(listOf(
            SongItem(
                number = "42",
                title = "Amazing Grace",
                songbook = "Hymnal",
                author = "Newton",
                lyrics = listOf("Amazing grace"),
            )
        ))
        val (client, _) = connectedClient()
        try {
            val detail = runBlocking { client.fetchSongDetail("42", "Hymnal") }
            assertEquals("Amazing Grace", detail?.title)
            assertEquals("Newton", detail?.author)
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchSongDetail for an unknown number is null`() {
        val (client, _) = connectedClient()
        try {
            assertNull(runBlocking { client.fetchSongDetail("no-such-number", "") })
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchBibleFile downloads the primary's loaded Bible bytes`() {
        val dir = Files.createTempDirectory("cp-instance-link-bible-fetch-test").toFile()
        val bibleFile = SpbFixture.spbFile(dir)
        server.updateBible(SpbFixture.loadedBible(dir), "KJV", filePath = bibleFile.absolutePath)
        val (client, _) = connectedClient()
        try {
            val bytes = runBlocking { client.fetchBibleFile() }
            assertNotNull(bytes)
            assertTrue(
                bytes.decodeToString().contains("Genesis"),
                "expected the fixture's own book name in the downloaded bytes",
            )
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchSecondaryBibleFile downloads the primary's secondary Bible bytes`() {
        val dir = Files.createTempDirectory("cp-instance-link-secondary-bible-test").toFile()
        val bibleFile = SpbFixture.spbFile(dir)
        server.updateSecondaryBibleFilePath(bibleFile.absolutePath)
        val (client, _) = connectedClient()
        try {
            val bytes = runBlocking { client.fetchSecondaryBibleFile() }
            assertNotNull(bytes)
            assertTrue(
                bytes.decodeToString().contains("Genesis"),
                "expected the fixture's own book name in the downloaded bytes",
            )
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchSecondaryBibleFile is null when the primary has none configured`() {
        val (client, _) = connectedClient()
        try {
            assertNull(runBlocking { client.fetchSecondaryBibleFile() })
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchBibleTranslations downloads every module in manifest order`() {
        val dir = Files.createTempDirectory("cp-instance-link-translations-test").toFile()
        val first = java.io.File(dir, "KJV.spb").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val second = java.io.File(dir, "NIV.spb").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        server.updateBibleFilePaths(listOf(first.absolutePath, second.absolutePath))
        val (client, _) = connectedClient()
        try {
            val translations = runBlocking { client.fetchBibleTranslations() }
            assertEquals(
                listOf("KJV.spb" to byteArrayOf(1, 2, 3).toList(), "NIV.spb" to byteArrayOf(4, 5, 6).toList()),
                translations.map { (name, bytes) -> name to bytes.toList() },
            )
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchBackgroundSettings round-trips what the primary has configured`() {
        server.updateBackgroundSettings(BackgroundSettings(defaultBackgroundColor = "#123456"))
        val (client, _) = connectedClient()
        try {
            val settings = runBlocking { client.fetchBackgroundSettings() }
            assertEquals("#123456", settings?.defaultBackgroundColor)
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchPictureImageBytes downloads the exact bytes of the requested image`() {
        val dir = Files.createTempDirectory("cp-instance-link-pictures-test").toFile()
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val imageFile = java.io.File(dir, "photo.jpg").apply { writeBytes(imageBytes) }
        server.updatePictures(
            folderId = "folder-1",
            folderName = "Folder",
            folderPath = dir.absolutePath,
            imageFiles = listOf(imageFile),
        )
        val (client, _) = connectedClient()
        try {
            val bytes = runBlocking { client.fetchPictureImageBytes("folder-1", 0) }
            assertEquals(imageBytes.toList(), bytes?.toList())
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchPictureImageBytes for an unknown folder is null`() {
        val (client, _) = connectedClient()
        try {
            assertNull(runBlocking { client.fetchPictureImageBytes("no-such-folder", 0) })
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchBackgroundAsset downloads the configured slot's file bytes`() {
        val dir = Files.createTempDirectory("cp-instance-link-background-test").toFile()
        val assetBytes = byteArrayOf(9, 8, 7)
        val assetFile = java.io.File(dir, "bg.png").apply { writeBytes(assetBytes) }
        server.updateBackgroundSettings(BackgroundSettings(defaultBackgroundImage = assetFile.absolutePath))
        val (client, _) = connectedClient()
        try {
            val bytes = runBlocking { client.fetchBackgroundAsset(Constants.BACKGROUND_SLOT_DEFAULT, isVideo = false) }
            assertEquals(assetBytes.toList(), bytes?.toList())
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchBackgroundAsset for a slot with nothing configured is null`() {
        server.updateBackgroundSettings(BackgroundSettings(defaultBackgroundImage = ""))
        val (client, _) = connectedClient()
        try {
            assertNull(runBlocking { client.fetchBackgroundAsset(Constants.BACKGROUND_SLOT_DEFAULT, isVideo = false) })
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetchPresentationSlideBytes downloads the exact bytes of the requested slide`() {
        val dir = Files.createTempDirectory("cp-instance-link-presentation-test").toFile()
        val slideBytes = byteArrayOf(5, 6, 7)
        val slideFile = java.io.File(dir, "slide0.jpg").apply { writeBytes(slideBytes) }
        server.updatePresentation(
            id = "pres-1",
            filePath = "",
            fileName = "Test.pptx",
            fileType = "pptx",
            slideFiles = listOf(slideFile),
        )
        val (client, _) = connectedClient()
        try {
            awaitUntil("presentation to be published") { runBlocking { client.fetchPresentationSlideBytes(
                "pres-1",
                0,
            ) } != null }
            val bytes = runBlocking { client.fetchPresentationSlideBytes("pres-1", 0) }
            assertEquals(slideBytes.toList(), bytes?.toList())
        } finally {
            client.dispose()
        }
    }

    // ── Connect failure / reconnect ────────────────────────────────────────────

    @Test
    fun `connecting to a port nothing listens on reports ERROR and schedules a reconnect`() {
        val deadPort = java.net.ServerSocket(0).use { it.localPort }
        val statusHistory = mutableListOf<InstanceLinkStatus>()
        var reconnectDelay: Long? = null
        val client = InstanceLinkClient(
            onStatusChanged = { statusHistory.add(it) },
            onScheduleUpdated = {}, onLiveStateUpdated = {}, onDisplayCleared = {}, onSongSectionSelected = {},
            onPresentationSlideChanged = { _, _, _, _, _ -> }, onSongsUpdated = {},
            onReconnectScheduled = { reconnectDelay = it },
        )
        try {
            client.connect(host = "127.0.0.1", port = deadPort, apiKey = "", deviceId = "d", reconnectDelayMs = 100)
            awaitUntil("ERROR status") { InstanceLinkStatus.ERROR in statusHistory }
            awaitUntil("a reconnect to be scheduled") { reconnectDelay != null }
            assertTrue(reconnectDelay!! >= 100, "the reconnect delay floor is the caller's reconnectDelayMs")
        } finally {
            client.dispose()
        }
    }

    @Test
    fun `fetch methods report not_connected before any connect() call`() {
        val callbacks = Callbacks()
        val client = callbacks.client()
        try {
            assertNull(runBlocking { client.fetchSongDetail("1", "") })
            assertNull(runBlocking { client.fetchBibleFile() })
            assertNull(runBlocking { client.fetchBackgroundSettings() })
            assertNull(runBlocking { client.fetchPictureImageBytes("folder", 0) })
            assertEquals(emptyList(), runBlocking { client.fetchBibleTranslations() })
        } finally {
            client.dispose()
        }
    }

    // ── mediaStreamUrl ─────────────────────────────────────────────────────────

    @Test
    fun `mediaStreamUrl is null before connecting`() {
        val client = Callbacks().client()
        assertNull(client.mediaStreamUrl("media-1"))
        client.dispose()
    }

    @Test
    fun `mediaStreamUrl builds against the connected primary once connected`() {
        val (client, _) = connectedClient()
        try {
            val url = client.mediaStreamUrl("media-1")
            assertNotNull(url)
            assertTrue(url.startsWith("http://127.0.0.1:$port"), url)
            assertTrue(url.endsWith("/media-1"), url)
        } finally {
            client.dispose()
        }
    }
}

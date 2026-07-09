package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.server.InstanceLinkClient
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.LiveStateDto
import org.churchpresenter.app.churchpresenter.server.ScheduleItemDto
import org.churchpresenter.app.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.app.churchpresenter.server.SongDetailDto

/**
 * One surfaced InstanceLink command failure — a controller-mode command the primary rejected
 * or that never got acknowledged. [soft] marks the once-per-connection "primary never acks"
 * notice (informational, likely a version mismatch) as opposed to a hard failure.
 */
data class InstanceLinkCommandFailure(
    val commandType: String,
    val reason: String?,
    val soft: Boolean = false
)

/** Snapshot of the primary's `presentation_slide_changed` broadcast — see [InstanceLinkClient]. */
data class RemotePresentationSlide(
    val id: String,
    val index: Int,
    val total: Int,
    val isPlaying: Boolean,
    val isLive: Boolean
)

/**
 * Owns the [InstanceLinkClient] connection to another ChurchPresenter instance's CompanionServer.
 * Exposes parsed, typed state as [StateFlow]s — deliberately does NOT hold a reference to
 * [PresenterManager] or any other ViewModel; MainDesktop (which owns both) observes these flows
 * and drives the local presenter, per this project's ViewModel-ownership rule.
 */
class InstanceLinkViewModel {

    private val _connectionStatus = MutableStateFlow(InstanceLinkStatus.DISCONNECTED)
    val connectionStatus: StateFlow<InstanceLinkStatus> = _connectionStatus.asStateFlow()

    private val _remoteSchedule = MutableStateFlow<List<ScheduleItemDto>>(emptyList())
    val remoteSchedule: StateFlow<List<ScheduleItemDto>> = _remoteSchedule.asStateFlow()

    private val _remoteSongCatalog = MutableStateFlow<SongCatalogResponse?>(null)
    val remoteSongCatalog: StateFlow<SongCatalogResponse?> = _remoteSongCatalog.asStateFlow()

    private val _remoteLiveState = MutableStateFlow<LiveStateDto?>(null)
    val remoteLiveState: StateFlow<LiveStateDto?> = _remoteLiveState.asStateFlow()

    private val _remoteSongSectionIndex = MutableStateFlow<Int?>(null)
    val remoteSongSectionIndex: StateFlow<Int?> = _remoteSongSectionIndex.asStateFlow()

    private val _remotePresentationSlide = MutableStateFlow<RemotePresentationSlide?>(null)
    val remotePresentationSlide: StateFlow<RemotePresentationSlide?> = _remotePresentationSlide.asStateFlow()

    // Incremented on every display_cleared broadcast — a counter (not a Boolean) so Compose/
    // LaunchedEffect observers see a change even on repeated clears in a row.
    private val _displayClearedSignal = MutableStateFlow(0)
    val displayClearedSignal: StateFlow<Int> = _displayClearedSignal.asStateFlow()

    // Application-level liveness: wall-clock time of the last decoded WS message, null while
    // not connected. Drives the "Last update Xs ago" readout.
    private val _lastMessageAtMs = MutableStateFlow<Long?>(null)
    val lastMessageAtMs: StateFlow<Long?> = _lastMessageAtMs.asStateFlow()

    // When the client scheduled its next reconnect attempt (absolute wall-clock ms), null while
    // connected/connecting. Drives the "Link lost — reconnecting in Xs" badge countdown.
    private val _nextRetryAtMs = MutableStateFlow<Long?>(null)
    val nextRetryAtMs: StateFlow<Long?> = _nextRetryAtMs.asStateFlow()

    // Cache-invalidation signals — counters (same rationale as displayClearedSignal). Observers
    // clear/re-fetch the corresponding instance-link cache when the value changes.
    private val _bibleUpdatedSignal = MutableStateFlow(0)
    val bibleUpdatedSignal: StateFlow<Int> = _bibleUpdatedSignal.asStateFlow()

    private val _secondaryBibleUpdatedSignal = MutableStateFlow(0)
    val secondaryBibleUpdatedSignal: StateFlow<Int> = _secondaryBibleUpdatedSignal.asStateFlow()

    private val _picturesUpdatedSignal = MutableStateFlow(0)
    val picturesUpdatedSignal: StateFlow<Int> = _picturesUpdatedSignal.asStateFlow()

    private val _backgroundsUpdatedSignal = MutableStateFlow(0)
    val backgroundsUpdatedSignal: StateFlow<Int> = _backgroundsUpdatedSignal.asStateFlow()

    // Controller-mode command failures (rejected, undeliverable, or never acked) — collected by
    // main.kt and surfaced as a toast; what used to be a silent drop.
    private val _commandFailures = MutableSharedFlow<InstanceLinkCommandFailure>(extraBufferCapacity = 8)
    val commandFailures: SharedFlow<InstanceLinkCommandFailure> = _commandFailures.asSharedFlow()

    private val client = InstanceLinkClient(
        onStatusChanged = { status ->
            _connectionStatus.value = status
            if (status != InstanceLinkStatus.ERROR) _nextRetryAtMs.value = null
            if (status != InstanceLinkStatus.CONNECTED) _lastMessageAtMs.value = null
        },
        onScheduleUpdated = { items -> _remoteSchedule.value = items },
        onLiveStateUpdated = { state -> _remoteLiveState.value = state },
        onDisplayCleared = { _displayClearedSignal.value++ },
        onSongSectionSelected = { index -> _remoteSongSectionIndex.value = index },
        onPresentationSlideChanged = { id, index, total, isPlaying, isLive ->
            _remotePresentationSlide.value = RemotePresentationSlide(id, index, total, isPlaying, isLive)
        },
        onSongsUpdated = { catalog -> _remoteSongCatalog.value = catalog },
        onMessageReceived = { _lastMessageAtMs.value = System.currentTimeMillis() },
        onReconnectScheduled = { delayMs -> _nextRetryAtMs.value = System.currentTimeMillis() + delayMs },
        onBibleUpdated = { _bibleUpdatedSignal.value++ },
        onSecondaryBibleUpdated = { _secondaryBibleUpdatedSignal.value++ },
        onPicturesUpdated = { _picturesUpdatedSignal.value++ },
        onBackgroundsUpdated = { _backgroundsUpdatedSignal.value++ },
        onCommandFailed = { type, reason ->
            _commandFailures.tryEmit(InstanceLinkCommandFailure(commandType = type, reason = reason))
        },
        onCommandNoAck = {
            _commandFailures.tryEmit(InstanceLinkCommandFailure(commandType = "", reason = null, soft = true))
        }
    )

    fun connect(host: String, port: Int, apiKey: String, deviceId: String, reconnectDelayMs: Long) {
        client.connect(host, port, apiKey, deviceId, reconnectDelayMs)
    }

    fun disconnect() {
        client.disconnect()
    }

    /** Adds an item to the primary's schedule — still gated by its own operator-approval dialog. */
    fun sendAddToSchedule(item: ScheduleItem) {
        client.sendAddToSchedule(item)
    }

    /** Removes an item from the primary's schedule — still gated by its own operator-approval dialog. */
    fun sendRemoveFromSchedule(id: String) {
        client.sendRemoveFromSchedule(id)
    }

    // ── Instance Link "Controller" mode — see InstanceLinkClient for the full doc comment on each. ──

    fun sendProject(item: ScheduleItem) = client.sendProject(item)

    fun sendSelectBibleVerse(bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) =
        client.sendSelectBibleVerse(bookName, chapter, verseNumber, verseText, verseRange)

    fun sendSelectPicture(folderId: String, index: Int, fileName: String?) =
        client.sendSelectPicture(folderId, index, fileName)

    fun sendSelectSongSection(number: String, section: Int) = client.sendSelectSongSection(number, section)

    fun sendSelectSlide(id: String, index: Int) = client.sendSelectSlide(id, index)

    fun sendClear() = client.sendClear()

    fun sendBibleHold(hold: Boolean) = client.sendBibleHold(hold)

    /** Fetches one song's full lyrics from the primary on demand — see [InstanceLinkClient.fetchSongDetail]. */
    suspend fun fetchSongDetail(number: String, songbook: String): SongDetailDto? =
        client.fetchSongDetail(number, songbook)

    /** Streaming URL for one of the primary's media files — see [InstanceLinkClient.mediaStreamUrl]. */
    fun mediaStreamUrl(mediaId: String): String? = client.mediaStreamUrl(mediaId)

    /** Fetches one live picture's bytes — see [InstanceLinkClient.fetchPictureImageBytes]. */
    suspend fun fetchPictureImageBytes(folderId: String, index: Int): ByteArray? =
        client.fetchPictureImageBytes(folderId, index)

    /** Fetches one live presentation slide's bytes — see [InstanceLinkClient.fetchPresentationSlideBytes]. */
    suspend fun fetchPresentationSlideBytes(id: String, index: Int): ByteArray? =
        client.fetchPresentationSlideBytes(id, index)

    /** Downloads the primary's raw bible file — see [InstanceLinkClient.fetchBibleFile]. */
    suspend fun fetchBibleFile(): ByteArray? = client.fetchBibleFile()

    /** Downloads the primary's raw secondary bible file — see [InstanceLinkClient.fetchSecondaryBibleFile]. */
    suspend fun fetchSecondaryBibleFile(): ByteArray? = client.fetchSecondaryBibleFile()

    /** Fetches one lower-third preset's Lottie JSON by name — see [InstanceLinkClient.fetchLowerThirdJson]. */
    suspend fun fetchLowerThirdJson(name: String): ByteArray? = client.fetchLowerThirdJson(name)

    /** Fetches the primary's current background settings — see [InstanceLinkClient.fetchBackgroundSettings]. */
    suspend fun fetchBackgroundSettings(): BackgroundSettings? = client.fetchBackgroundSettings()

    /** Fetches one background slot's raw asset bytes — see [InstanceLinkClient.fetchBackgroundAsset]. */
    suspend fun fetchBackgroundAsset(slot: String, isVideo: Boolean): ByteArray? =
        client.fetchBackgroundAsset(slot, isVideo)

    fun dispose() {
        client.dispose()
    }
}

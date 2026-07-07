package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.server.InstanceLinkClient
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.LiveStateDto
import org.churchpresenter.app.churchpresenter.server.ScheduleItemDto
import org.churchpresenter.app.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.app.churchpresenter.server.SongDetailDto

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

    private val client = InstanceLinkClient(
        onStatusChanged = { status -> _connectionStatus.value = status },
        onScheduleUpdated = { items -> _remoteSchedule.value = items },
        onLiveStateUpdated = { state -> _remoteLiveState.value = state },
        onDisplayCleared = { _displayClearedSignal.value++ },
        onSongSectionSelected = { index -> _remoteSongSectionIndex.value = index },
        onPresentationSlideChanged = { id, index, total, isPlaying, isLive ->
            _remotePresentationSlide.value = RemotePresentationSlide(id, index, total, isPlaying, isLive)
        },
        onSongsUpdated = { catalog -> _remoteSongCatalog.value = catalog }
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

    /** Fetches one song's full lyrics from the primary on demand — see [InstanceLinkClient.fetchSongDetail]. */
    suspend fun fetchSongDetail(number: String, songbook: String): SongDetailDto? =
        client.fetchSongDetail(number, songbook)

    /** Fetches one live picture's bytes — see [InstanceLinkClient.fetchPictureImageBytes]. */
    suspend fun fetchPictureImageBytes(folderId: String, index: Int): ByteArray? =
        client.fetchPictureImageBytes(folderId, index)

    /** Fetches one live presentation slide's bytes — see [InstanceLinkClient.fetchPresentationSlideBytes]. */
    suspend fun fetchPresentationSlideBytes(id: String, index: Int): ByteArray? =
        client.fetchPresentationSlideBytes(id, index)

    /** Downloads the primary's raw bible file — see [InstanceLinkClient.fetchBibleFile]. */
    suspend fun fetchBibleFile(): ByteArray? = client.fetchBibleFile()

    /** Fetches one lower-third preset's Lottie JSON by name — see [InstanceLinkClient.fetchLowerThirdJson]. */
    suspend fun fetchLowerThirdJson(name: String): ByteArray? = client.fetchLowerThirdJson(name)

    fun dispose() {
        client.dispose()
    }
}

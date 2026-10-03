package org.churchpresenter.server



import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow






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
class InstanceLinkViewModel private constructor(
    private val state: InstanceLinkState,
    private val client: InstanceLinkClient,
) : InstanceLinkCommands by client, InstanceLinkFetches by client {

    constructor() : this(InstanceLinkState())

    private constructor(state: InstanceLinkState) : this(state, state.newClient())

    val connectionStatus: StateFlow<InstanceLinkStatus> = state.connectionStatus
    val remoteSchedule: StateFlow<List<ScheduleItemDto>> = state.remoteSchedule
    val remoteSongCatalog: StateFlow<SongCatalogResponse?> = state.remoteSongCatalog
    val remoteLiveState: StateFlow<LiveStateDto?> = state.remoteLiveState
    val remoteSongSectionIndex: StateFlow<Int?> = state.remoteSongSectionIndex
    val remotePresentationSlide: StateFlow<RemotePresentationSlide?> = state.remotePresentationSlide
    val displayClearedSignal: StateFlow<Int> = state.displayClearedSignal
    val lastMessageAtMs: StateFlow<Long?> = state.lastMessageAtMs
    val nextRetryAtMs: StateFlow<Long?> = state.nextRetryAtMs
    val bibleUpdatedSignal: StateFlow<Int> = state.bibleUpdatedSignal
    val secondaryBibleUpdatedSignal: StateFlow<Int> = state.secondaryBibleUpdatedSignal
    val picturesUpdatedSignal: StateFlow<Int> = state.picturesUpdatedSignal
    val backgroundsUpdatedSignal: StateFlow<Int> = state.backgroundsUpdatedSignal
    val commandFailures: SharedFlow<InstanceLinkCommandFailure> = state.commandFailures

    fun connect(host: String, port: Int, apiKey: String, deviceId: String, reconnectDelayMs: Long) {
        client.connect(host, port, apiKey, deviceId, reconnectDelayMs)
    }

    fun disconnect() {
        client.disconnect()
    }

    fun dispose() {
        client.dispose()
    }
}

/** The link's typed state, fed by the client's callbacks; [InstanceLinkViewModel] exposes it. */
internal class InstanceLinkState {

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

    fun newClient(): InstanceLinkClient = InstanceLinkClient(
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
}


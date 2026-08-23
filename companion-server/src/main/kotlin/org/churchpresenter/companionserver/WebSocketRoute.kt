package org.churchpresenter.companionserver

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.churchpresenter.settings.utils.Constants

private const val SUMMARY_PREVIEW_CHARS = 60

/**
 * The companion WebSocket: snapshot on connect, then the live command/event stream.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing. The catalogues a new client is sent on connect arrive as identically-named
 * parameters; the live presentation position and the command flows are read per message through
 * [server].
 */
internal fun Route.webSocketRoute(
    server: CompanionServer,
    _apiKey: MutableStateFlow<String>,
    _apiKeyEnabled: MutableStateFlow<Boolean>,
    _bibleCatalog: MutableStateFlow<BibleCatalogResponse?>,
    _catalog: MutableStateFlow<SongCatalogResponse>,
    _connectedInstanceLinkFollowers: MutableStateFlow<Set<String>>,
    _liveState: MutableStateFlow<LiveStateDto?>,
    _pictureCatalog: MutableStateFlow<PictureFolderResponse?>,
    _pictureCatalogs: ConcurrentHashMap<String, PictureFolderResponse>,
    _presentationCatalog: MutableStateFlow<PresentationCatalogResponse>,
    _presentationCatalogs: ConcurrentHashMap<String, PresentationDto>,
    _schedule: MutableStateFlow<List<ScheduleItemDto>>,
    _scheduleItemToPresentationId: ConcurrentHashMap<String, String>,
    json: Json,
    scope: CoroutineScope,
) {
                webSocket(Constants.ENDPOINT_WS) {
                    val queryKey = call.request.queryParameters[Constants.QUERY_PARAM_API_KEY]
                    val headerKey = call.request.headers[Constants.HEADER_API_KEY]
                    if (_apiKeyEnabled.value && _apiKey.value.isNotEmpty()) {
                        val provided = queryKey ?: headerKey ?: ""
                        if (provided != _apiKey.value) {
                            InstanceLinkLogger.log(
                                InstanceLinkLogSide.PRIMARY,
                                "follower_unauthorized",
                                mapOf("reason" to "bad_api_key")
                            )
                            send(Frame.Text("{\"error\":\"Unauthorized\"}"))
                            return@webSocket
                        }
                    }
                    val wsClientId = call.request.headers[Constants.HEADER_DEVICE_ID]
                        ?: call.request.queryParameters[Constants.HEADER_DEVICE_ID]
                        ?: ""
                    // A blocked device gets no session at all: no live feed to watch, and no socket
                    // to send commands down. Checked before the follower registration below so a
                    // blocked instance never appears in the connected-followers count either.
                    if (server.isClientBlocked(wsClientId)) {
                        InstanceLinkLogger.log(
                            InstanceLinkLogSide.PRIMARY, "follower_unauthorized",
                            mapOf("reason" to "blocked", "deviceId" to wsClientId)
                        )
                        send(Frame.Text("{\"error\":\"Blocked\"}"))
                        return@webSocket
                    }
                    val isInstanceLinkFollower = call.request.headers[Constants.HEADER_CLIENT_ROLE] ==
                        Constants.CLIENT_ROLE_INSTANCE_LINK
                    // A follower instance is another desktop, not a phone, so only the remaining
                    // sessions count as the mobile app being used.
                    if (!isInstanceLinkFollower) server.host.onMobileClientConnected()
                    if (isInstanceLinkFollower && wsClientId.isNotEmpty()) {
                        _connectedInstanceLinkFollowers.value = _connectedInstanceLinkFollowers.value + wsClientId
                        InstanceLinkLogger.log(
                            InstanceLinkLogSide.PRIMARY,
                            "follower_connected",
                            mapOf("deviceId" to wsClientId)
                        )
                    }

                    // Subscribed to the server.broadcast flow BEFORE the connect snapshot is written, and
                    // released only once it has been: server.broadcastChannel has no replay, so a change
                    // emitted while the snapshot was still being sent used to land on a flow this
                    // session had not subscribed to yet and was lost outright -- the client then
                    // showed stale content until its next reconnect. Broadcasts that arrive during
                    // the snapshot queue in the flow's own buffer instead, so the whole snapshot
                    // still reaches the client ahead of any of them.
                    val snapshotSent = CompletableDeferred<Unit>()
                    val subscribed = CompletableDeferred<Unit>()
                    val broadcastJob = scope.launch {
                        server.broadcastChannel
                            .onSubscription { subscribed.complete(Unit) }
                            .collect { message ->
                                snapshotSent.await()
                                send(Frame.Text(message))
                            }
                    }
                    // A scope already cancelled (server stopping) never runs the block above, so the
                    // wait is released by the job ending too rather than hanging the handler; the
                    // fallback is exactly the old behaviour, a snapshot and no broadcasts.
                    broadcastJob.invokeOnCompletion { subscribed.complete(Unit) }
                    // Tied to this session rather than only to the `finally` below, because the
                    // snapshot sends between here and there are outside it: a client that vanishes
                    // mid-snapshot throws out of the handler before that `try` is ever entered, and
                    // the collector -- launched on the server-lifetime scope, not this session's --
                    // would be left parked on snapshotSent.await() for as long as the server runs,
                    // holding this session with it. One leaked coroutine per aborted connect, in
                    // exactly the reconnect churn this whole change is about.
                    coroutineContext.job.invokeOnCompletion { broadcastJob.cancel() }
                    subscribed.await()

                    sendConnectSnapshot(
                        server, _bibleCatalog, _catalog, _liveState, _pictureCatalog,
                        _presentationCatalog, _schedule, json,
                    )
                    // The snapshot is complete; anything the collector queued during it now flows.
                    snapshotSent.complete(Unit)

                    // Ack for commands that carried a commandId (InstanceLink controller mode) —
                    // no-op for clients that don't send one, so mobile behavior is unchanged.

                    try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            try {
                                val msg = json.decodeFromString(WebSocketMessage.serializer(), frame.readText())
                                InstanceLinkLogger.log(
                                    InstanceLinkLogSide.PRIMARY, "ws_command_received",
                                    mapOf("type" to msg.type, "deviceId" to wsClientId)
                                )
                                // Blocked *during* this session — the handshake check above cannot
                                // see a decision the operator makes while the socket is already open.
                                if (server.isClientBlocked(wsClientId)) {
                                    InstanceLinkLogger.log(
                                        InstanceLinkLogSide.PRIMARY, "ws_command_refused",
                                        mapOf("type" to msg.type, "deviceId" to wsClientId, "reason" to "blocked")
                                    )
                                    sendCommandAck(msg.commandId, ok = false, reason = "blocked", json = json)
                                    continue
                                }
                                handleWsCommand(
                                    msg, server, wsClientId, _pictureCatalogs, _presentationCatalogs,
                                    _scheduleItemToPresentationId,
                                    _schedule, json, scope,
                                )
                            } catch (e: Exception) {
                                InstanceLinkLogger.log(
                                    InstanceLinkLogSide.PRIMARY, "ws_frame_malformed",
                                    mapOf("deviceId" to wsClientId, "reason" to e.message)
                                )
                            }
                        }
                    }
                    } finally {
                        broadcastJob.cancel()
                        if (isInstanceLinkFollower && wsClientId.isNotEmpty()) {
                            _connectedInstanceLinkFollowers.value = _connectedInstanceLinkFollowers.value - wsClientId
                            InstanceLinkLogger.log(
                                InstanceLinkLogSide.PRIMARY,
                                "follower_disconnected",
                                mapOf("deviceId" to wsClientId)
                            )
                        }
                    }
                }

                // ── Lower Third Sequencer (Bitfocus Companion) ───────────────────
                // One HTTP call runs the whole timed sequence: ATEM key on → play
                // the lower third → key off when the animation ends.

}


/** Acks a command that carried a commandId (InstanceLink controller mode); a no-op without one. */
private suspend fun DefaultWebSocketServerSession.sendCommandAck(
    commandId: String?,
    ok: Boolean,
    reason: String? = null,
    json: Json,
) {
                if (commandId == null) return
                try {
                    send(Frame.Text(json.encodeToString(
                        WebSocketMessage.serializer(),
                        WebSocketMessage(
                            type = Constants.WS_EVENT_COMMAND_ACK,
                            payload = json.encodeToString(
                                CommandAckPayload.serializer(),
                                CommandAckPayload(commandId, ok, reason)
                            )
                        )
                    )))
                } catch (_: Exception) {
                    // session already closing — the follower's timeout covers this
                }
                InstanceLinkLogger.log(
                    InstanceLinkLogSide.PRIMARY, "command_ack",
                    mapOf("commandId" to commandId, "ok" to ok, "reason" to reason)
                )
}

/** Runs one command frame from a connected client. */
@Suppress("LongParameterList")
private suspend fun DefaultWebSocketServerSession.handleWsCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    wsClientId: String,
    _pictureCatalogs: ConcurrentHashMap<String, PictureFolderResponse>,
    _presentationCatalogs: ConcurrentHashMap<String, PresentationDto>,
    _scheduleItemToPresentationId: ConcurrentHashMap<String, String>,
    _schedule: MutableStateFlow<List<ScheduleItemDto>>,
    json: Json,
    scope: CoroutineScope,
) {
                when (msg.type) {
                    Constants.WS_CMD_SELECT_SONG -> {
                        val song = json.decodeFromString(ScheduleSongDto.serializer(), msg.payload)
                        scope.launch { server.onSongSelected.emit(song) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_SELECT_PICTURE -> {
                        val req = json.decodeFromString(SelectPictureRequest.serializer(), msg.payload)
                        scope.launch { server.onSelectPicture.emit(req) }
                        val folderName = _pictureCatalogs[req.folderId]?.folderName ?: req.folderId
                        val imageLabel = req.fileName ?: "Image ${req.index}"
                        scope.launch {
                            server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                "present", folderName, imageLabel, wsClientId
                            ))
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_SELECT_SONG_SECTION -> {
                        val req = json.decodeFromString(SelectSongSectionRequest.serializer(), msg.payload)
                        scope.launch { server.onSelectSongSection.emit(req) }
                        scope.launch {
                            server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                "present", "Song ${req.number}", "Section ${req.section}", wsClientId
                            ))
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_SELECT_SLIDE -> {
                        val req = json.decodeFromString(SelectSlideRequest.serializer(), msg.payload)
                        scope.launch { server.onSelectSlide.emit(req) }
                        val presName =
                            _presentationCatalogs[_scheduleItemToPresentationId[req.id] ?: req.id]?.fileName ?: req.id
                        scope.launch {
                            server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                "present", presName, "Slide ${req.index + 1}", wsClientId
                            ))
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_SELECT_BIBLE_VERSE -> {
                        val req = json.decodeFromString(SelectBibleVerseRequest.serializer(), msg.payload)
                        scope.launch { server.onSelectBibleVerse.emit(req) }
                        val ref = if (req.verseRange.isNotEmpty()) "${req.bookName} ${req.chapter}:${req.verseRange}"
                                  else "${req.bookName} ${req.chapter}:${req.verseNumber}"
                        scope.launch {
                            server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                "present", ref, req.verseText.take(SUMMARY_PREVIEW_CHARS), wsClientId
                            ))
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_CLEAR -> {
                        scope.launch { server.onClear.emit(Unit) }
                        scope.launch {
                            server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                "clear", "Clear Display", clientId = wsClientId
                            ))
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_BIBLE_HOLD -> {
                        val hold = try {
                            json.parseToJsonElement(msg.payload)
                                .jsonObject["hold"]?.toString()?.toBooleanStrictOrNull() ?: true
                        } catch (_: Exception) { true }
                        scope.launch { server.onBibleHold.emit(hold) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_NEXT_PICTURE -> {
                        scope.launch { server.onNextPicture.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_PREVIOUS_PICTURE -> {
                        scope.launch { server.onPreviousPicture.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_NEXT_SLIDE -> {
                        scope.launch { server.onNextSlide.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_PREVIOUS_SLIDE -> {
                        scope.launch { server.onPreviousSlide.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_MEDIA_PLAY_PAUSE -> {
                        scope.launch { server.onMediaPlayPause.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_MEDIA_STOP -> {
                        scope.launch { server.onMediaStop.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_MEDIA_SEEK_FORWARD -> {
                        scope.launch { server.onMediaSeekForward.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_MEDIA_SEEK_BACKWARD -> {
                        scope.launch { server.onMediaSeekBackward.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_MEDIA_SEEK_TO -> {
                        val ms = msg.payload.trim().toLongOrNull()
                        if (ms != null) {
                            scope.launch { server.onMediaSeekTo.emit(ms) }
                            sendCommandAck(msg.commandId, ok = true, json = json)
                        } else sendCommandAck(msg.commandId, ok = false, reason = "invalid_payload", json = json)
                    }
                    Constants.WS_CMD_MEDIA_SET_VOLUME -> {
                        val v = msg.payload.trim().toFloatOrNull()
                        if (v != null) {
                            scope.launch { server.onMediaSetVolume.emit(v) }
                            sendCommandAck(msg.commandId, ok = true, json = json)
                        } else sendCommandAck(msg.commandId, ok = false, reason = "invalid_payload", json = json)
                    }
                    Constants.WS_CMD_MEDIA_MUTE_TOGGLE -> {
                        scope.launch { server.onMediaMuteToggle.emit(Unit) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_ADD_TO_SCHEDULE -> {
                        val item = server.parseRemoteItem(msg.payload)
                            ?: json.decodeFromString(AddToScheduleRequest.serializer(), msg.payload).item
                        val pending = PendingRemoteRequest(item, wsClientId)
                        scope.launch {
                            server.onAddToSchedule.emit(pending)
                            val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                            val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                            try { send(Frame.Text(response)) } catch (_: Exception) { }
                        }
                        // Ack "queued" immediately — the operator's approval can take
                        // minutes, and its outcome still arrives via schedule_updated
                        // (plus the legacy raw {"ok":...} reply above for mobile).
                        sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = json)
                    }
                    Constants.WS_CMD_ADD_BATCH_TO_SCHEDULE -> {
                        val items = try {
                            json.decodeFromString(RemoteItemsRequest.serializer(), msg.payload)
                                .items.mapNotNull { it.toScheduleItem() }
                        } catch (_: Exception) { emptyList() }
                        if (items.isNotEmpty()) {
                            val pending = PendingBatchRequest(items, wsClientId)
                            scope.launch {
                                server.onAddBatchToSchedule.emit(pending)
                                val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                                val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                                try { send(Frame.Text(response)) } catch (_: Exception) { }
                            }
                            sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = json)
                        } else {
                            sendCommandAck(msg.commandId, ok = false, reason = "invalid_payload", json = json)
                        }
                    }
                    Constants.WS_CMD_PROJECT -> {
                        val item = server.parseRemoteItem(msg.payload)
                            ?: json.decodeFromString(ProjectRequest.serializer(), msg.payload).item
                        val pending = PendingRemoteRequest(item, wsClientId)
                        scope.launch {
                            server.onProject.emit(pending)
                            val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                            val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                            try { send(Frame.Text(response)) } catch (_: Exception) { }
                        }
                        sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = json)
                    }
                    Constants.WS_CMD_REMOVE_FROM_SCHEDULE -> {
                        val req = json.decodeFromString(RemoveFromScheduleRequest.serializer(), msg.payload)
                        val label = _schedule.value.firstOrNull { it.id == req.id }?.displayText ?: req.id
                        val pending = PendingRemoveRequest(req.id, label, wsClientId)
                        scope.launch {
                            server.onRemoveFromSchedule.emit(pending)
                            val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                            val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                            try { send(Frame.Text(response)) } catch (_: Exception) { }
                        }
                        sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = json)
                    }
                    else -> sendCommandAck(msg.commandId, ok = false, reason = "unknown_command", json = json)
                }
}


/**
 * Everything a freshly connected client is told up front — catalogues, schedule, live state, and
 * the empty-payload invalidation signals a reconnecting follower needs.
 */
@Suppress("LongParameterList")
private suspend fun DefaultWebSocketServerSession.sendConnectSnapshot(
    server: CompanionServer,
    _bibleCatalog: MutableStateFlow<BibleCatalogResponse?>,
    _catalog: MutableStateFlow<SongCatalogResponse>,
    _liveState: MutableStateFlow<LiveStateDto?>,
    _pictureCatalog: MutableStateFlow<PictureFolderResponse?>,
    _presentationCatalog: MutableStateFlow<PresentationCatalogResponse>,
    _schedule: MutableStateFlow<List<ScheduleItemDto>>,
    json: Json,
) {
    val catalog = _catalog.value
    val schedule = _schedule.value
    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
        WebSocketMessage(Constants.WS_EVENT_SONGS_UPDATED,
            json.encodeToString(SongCatalogResponse.serializer(), catalog)))))
    _bibleCatalog.value?.let { bibleCatalog ->
        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
            WebSocketMessage(Constants.WS_EVENT_BIBLE_UPDATED,
                json.encodeToString(BibleCatalogResponse.serializer(), bibleCatalog)))))
    }
    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
        WebSocketMessage(Constants.WS_EVENT_SCHEDULE_UPDATED,
            json.encodeToString(ScheduleResponse.serializer(), ScheduleResponse(schedule, schedule.size))))))
    val presentationCatalog = _presentationCatalog.value
    if (presentationCatalog.presentations.isNotEmpty()) {
        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
            WebSocketMessage(Constants.WS_EVENT_PRESENTATION_UPDATED,
                json.encodeToString(PresentationCatalogResponse.serializer(), presentationCatalog)))))
    }
    _pictureCatalog.value?.let { pictureCatalog ->
        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
            WebSocketMessage(Constants.WS_EVENT_PICTURES_UPDATED,
                json.encodeToString(PictureFolderResponse.serializer(), pictureCatalog)))))
    }
    // Resent on every (re)connect so a follower mirroring backgrounds
    // (InstanceLinkSettings.mirrorBackgrounds) always invalidates its local asset
    // cache once per connection — same reasoning as bible/pictures above. Without
    // this, a follower that reconnects (app restart, network blip, the automatic
    // backoff reconnect) keeps serving whatever it cached last session even if the
    // primary's background changed while it was disconnected.
    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
        WebSocketMessage(Constants.WS_EVENT_BACKGROUNDS_UPDATED, payload = ""))))
    // The secondary bible needs exactly the same treatment, and for exactly the same
    // reason: it is an invalidation signal with an empty payload, so a follower that
    // reconnects without it keeps serving the .spb it cached last session even if the
    // primary changed translation while it was away.
    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
        WebSocketMessage(Constants.WS_EVENT_SECONDARY_BIBLE_UPDATED, payload = ""))))
    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
        WebSocketMessage(
            type = Constants.WS_EVENT_PRESENTATION_SLIDE_CHANGED,
            payload =
                """{"id":"${server._currentPresentationId}","index":${server._currentSlideIndex},"total":""" +
                    """${server._currentSlideTotalCount},"isPlaying":${server._presentationIsPlaying},"isLive":""" +
                        """${server._presentationIsLive}}"""
        ))))
    _liveState.value?.let { state ->
        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
            WebSocketMessage(Constants.WS_EVENT_LIVE_STATE_CHANGED,
                json.encodeToString(LiveStateDto.serializer(), state)))))
    }

}

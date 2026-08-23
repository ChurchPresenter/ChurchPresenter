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
                    val catalogs = WsCatalogs(_pictureCatalogs, _presentationCatalogs, _scheduleItemToPresentationId)
                    val commandContext = WsCommandContext(server, catalogs, _schedule, json, scope)
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
                                handleWsCommand(msg, wsClientId, commandContext)
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

/**
 * The per-connection state every command handler below reads.
 *
 * One parameter instead of six. The handlers are split out of `handleWsCommand`, which ran to 177
 * lines as a single `when`; passing each of them the same six values individually would have traded
 * one over-long function for three over-long parameter lists.
 */
private class WsCommandContext(
    val server: CompanionServer,
    val catalogs: WsCatalogs,
    val schedule: MutableStateFlow<List<ScheduleItemDto>>,
    val json: Json,
    val scope: CoroutineScope,
) {
    /**
     * Tells the operator that a remote just drove the transport.
     *
     * These commands are not gated — halting a volume nudge on a dialog would make a live service
     * unusable — but they used to be *silent*, which is worse than ungated: eleven of the
     * twenty-two commands left no trace at all, so a phone stepping through slides or muting the
     * music was invisible to the person at the desk. A toast is the least that lets them notice.
     */
    suspend fun reportTransport(label: String, wsClientId: String) {
        server.onInstantAction.emit(
            CompanionServer.RemoteInstantAction("present", label, "", wsClientId),
        )
    }
}

/**
 * The three lookups a command uses to turn an id into something the operator can read.
 *
 * They travel together because they are used together: every prompt raised from a command frame
 * names the picture folder, the deck or the schedule row the request is about, and getting that
 * name wrong is worse than not prompting at all.
 */
private class WsCatalogs(
    val pictureCatalogs: ConcurrentHashMap<String, PictureFolderResponse>,
    val presentationCatalogs: ConcurrentHashMap<String, PresentationDto>,
    val scheduleItemToPresentationId: ConcurrentHashMap<String, String>,
)

/**
 * Runs one command frame from a connected client.
 *
 * Three groups, tried in order, each answering whether it recognised the type: what is on screen,
 * how it is being driven, and what is in the schedule. The final `else` is the only place an
 * unknown command is answered, so a type that no group claims is always acked as unknown rather
 * than silently dropped.
 */
private suspend fun DefaultWebSocketServerSession.handleWsCommand(
    msg: WebSocketMessage,
    wsClientId: String,
    ctx: WsCommandContext,
) {
    if (handleSelectionCommand(msg, wsClientId, ctx)) return
    if (handleTransportCommand(msg, wsClientId, ctx)) return
    if (handleScheduleCommand(msg, wsClientId, ctx)) return
    sendCommandAck(msg.commandId, ok = false, reason = "unknown_command", json = ctx.json)
}

/** What the operator is shown when a command asks to put something on screen. */
private data class ApprovalPrompt(val actionType: String, val title: String, val detail: String = "")

/**
 * Asks the operator, and acks the refusal itself so the caller has nothing to do on that path.
 *
 * Answering `false` rather than returning early is what keeps the command handlers to two returns
 * apiece: each arm wraps its work in `if (approvedByOperator(...)) { … }` instead of guarding with
 * a return, so adding a gated command adds no control flow to the function around it.
 */
private suspend fun DefaultWebSocketServerSession.approvedByOperator(
    msg: WebSocketMessage,
    ctx: WsCommandContext,
    prompt: ApprovalPrompt,
    wsClientId: String,
): Boolean {
    if (ctx.server.requestApproval(prompt.actionType, prompt.title, prompt.detail, wsClientId)) return true
    sendCommandAck(msg.commandId, ok = false, reason = "denied", json = ctx.json)
    return false
}

/**
 * Picks what content is live: a song, a picture, or a section within a song.
 *
 * Split from [handleScreenCommand] because seven arms that each ask the operator first is more
 * branching than one function should carry — not an arbitrary line: these three name something in
 * the library, the four next door act on what is already on the screen.
 */
private suspend fun DefaultWebSocketServerSession.handleSelectionCommand(
    msg: WebSocketMessage,
    wsClientId: String,
    ctx: WsCommandContext,
): Boolean {
    when (msg.type) {
                    Constants.WS_CMD_SELECT_SONG -> {
                        val song = ctx.json.decodeFromString(ScheduleSongDto.serializer(), msg.payload)
                        if (approvedByOperator(msg, ctx, ApprovalPrompt("present", song.title), wsClientId)) {
                            ctx.scope.launch { ctx.server.onSongSelected.emit(song) }
                            sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                        }
                    }
                    Constants.WS_CMD_SELECT_PICTURE -> {
                        val req = ctx.json.decodeFromString(SelectPictureRequest.serializer(), msg.payload)
                        val folderName = ctx.catalogs.pictureCatalogs[req.folderId]?.folderName ?: req.folderId
                        val imageLabel = req.fileName ?: "Image ${req.index}"
                        val prompt = ApprovalPrompt("present", folderName, imageLabel)
                        if (approvedByOperator(msg, ctx, prompt, wsClientId)) {
                            ctx.scope.launch { ctx.server.onSelectPicture.emit(req) }
                            sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                        }
                    }
                    Constants.WS_CMD_SELECT_SONG_SECTION -> {
                        val req = ctx.json.decodeFromString(SelectSongSectionRequest.serializer(), msg.payload)
                        val prompt = ApprovalPrompt("present", "Song ${req.number}", "Section ${req.section}")
                        if (approvedByOperator(msg, ctx, prompt, wsClientId)) {
                            ctx.scope.launch { ctx.server.onSelectSongSection.emit(req) }
                            sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                        }
                    }
        else -> return handleScreenCommand(msg, wsClientId, ctx)
    }
    return true
}

/** Acts on what is already live: the slide, the verse, holding it, or clearing the screen. */
private suspend fun DefaultWebSocketServerSession.handleScreenCommand(
    msg: WebSocketMessage,
    wsClientId: String,
    ctx: WsCommandContext,
): Boolean {
    when (msg.type) {
                    Constants.WS_CMD_SELECT_SLIDE -> {
                        val req = ctx.json.decodeFromString(SelectSlideRequest.serializer(), msg.payload)
                        val presName =
                            ctx.catalogs.presentationCatalogs[
                                ctx.catalogs.scheduleItemToPresentationId[req.id] ?: req.id
                            ]?.fileName ?: req.id
                        val prompt = ApprovalPrompt("present", presName, "Slide ${req.index + 1}")
                        if (approvedByOperator(msg, ctx, prompt, wsClientId)) {
                            ctx.scope.launch { ctx.server.onSelectSlide.emit(req) }
                            sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                        }
                    }
                    Constants.WS_CMD_SELECT_BIBLE_VERSE -> {
                        val req = ctx.json.decodeFromString(SelectBibleVerseRequest.serializer(), msg.payload)
                        val ref = if (req.verseRange.isNotEmpty()) "${req.bookName} ${req.chapter}:${req.verseRange}"
                                  else "${req.bookName} ${req.chapter}:${req.verseNumber}"
                        val prompt = ApprovalPrompt("present", ref, req.verseText.take(SUMMARY_PREVIEW_CHARS))
                        if (approvedByOperator(msg, ctx, prompt, wsClientId)) {
                            ctx.scope.launch { ctx.server.onSelectBibleVerse.emit(req) }
                            sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                        }
                    }
                    Constants.WS_CMD_CLEAR -> {
                        if (approvedByOperator(msg, ctx, ApprovalPrompt("clear", "Clear Display"), wsClientId)) {
                            ctx.scope.launch { ctx.server.onClear.emit(Unit) }
                            sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                        }
                    }
                    Constants.WS_CMD_BIBLE_HOLD -> {
                        val hold = try {
                            ctx.json.parseToJsonElement(msg.payload)
                                .jsonObject["hold"]?.toString()?.toBooleanStrictOrNull() ?: true
                        } catch (_: Exception) { true }
                        ctx.scope.launch { ctx.server.onBibleHold.emit(hold) }
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
        else -> return false
    }
    return true
}

/**
 * Tells the operator that a remote just drove the transport.
 *
 * These commands are not gated — halting a volume nudge on a dialog would make a live service
 * unusable — but they used to be *silent*, which is worse than ungated: eleven of the twenty-two
 * commands left no trace at all, so a phone stepping through slides or muting the music was
 * invisible to the person at the desk. A toast is the least that lets them notice and block it.
 */
/** Steps through what is already live, and drives the media transport. */
private suspend fun DefaultWebSocketServerSession.handleTransportCommand(
    msg: WebSocketMessage,
    wsClientId: String,
    ctx: WsCommandContext,
): Boolean {
    when (msg.type) {
                    Constants.WS_CMD_NEXT_PICTURE -> {
                        ctx.scope.launch { ctx.server.onNextPicture.emit(Unit) }
                        ctx.reportTransport("Next picture", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
                    Constants.WS_CMD_PREVIOUS_PICTURE -> {
                        ctx.scope.launch { ctx.server.onPreviousPicture.emit(Unit) }
                        ctx.reportTransport("Previous picture", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
                    Constants.WS_CMD_NEXT_SLIDE -> {
                        ctx.scope.launch { ctx.server.onNextSlide.emit(Unit) }
                        ctx.reportTransport("Next slide", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
                    Constants.WS_CMD_PREVIOUS_SLIDE -> {
                        ctx.scope.launch { ctx.server.onPreviousSlide.emit(Unit) }
                        ctx.reportTransport("Previous slide", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
                    Constants.WS_CMD_MEDIA_PLAY_PAUSE -> {
                        ctx.scope.launch { ctx.server.onMediaPlayPause.emit(Unit) }
                        ctx.reportTransport("Play/pause media", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
                    Constants.WS_CMD_MEDIA_STOP -> {
                        ctx.scope.launch { ctx.server.onMediaStop.emit(Unit) }
                        ctx.reportTransport("Stop media", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
                    Constants.WS_CMD_MEDIA_SEEK_FORWARD -> {
                        ctx.scope.launch { ctx.server.onMediaSeekForward.emit(Unit) }
                        ctx.reportTransport("Seek forward", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
                    Constants.WS_CMD_MEDIA_SEEK_BACKWARD -> {
                        ctx.scope.launch { ctx.server.onMediaSeekBackward.emit(Unit) }
                        ctx.reportTransport("Seek backward", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
                    Constants.WS_CMD_MEDIA_SEEK_TO -> {
                        val ms = msg.payload.trim().toLongOrNull()
                        if (ms != null) {
                            ctx.scope.launch { ctx.server.onMediaSeekTo.emit(ms) }
                            ctx.reportTransport("Seek to", wsClientId)
                            sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                        } else sendCommandAck(msg.commandId, ok = false, reason = "invalid_payload", json = ctx.json)
                    }
                    Constants.WS_CMD_MEDIA_SET_VOLUME -> {
                        val v = msg.payload.trim().toFloatOrNull()
                        if (v != null) {
                            ctx.scope.launch { ctx.server.onMediaSetVolume.emit(v) }
                            ctx.reportTransport("Set volume", wsClientId)
                            sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                        } else sendCommandAck(msg.commandId, ok = false, reason = "invalid_payload", json = ctx.json)
                    }
                    Constants.WS_CMD_MEDIA_MUTE_TOGGLE -> {
                        ctx.scope.launch { ctx.server.onMediaMuteToggle.emit(Unit) }
                        ctx.reportTransport("Mute/unmute media", wsClientId)
                        sendCommandAck(msg.commandId, ok = true, json = ctx.json)
                    }
        else -> return false
    }
    return true
}

/** Adds to, projects from, and removes from the schedule — each of these asks the operator. */
/** Puts something new into the schedule — one item, or a batch. Both ask the operator first. */
private suspend fun DefaultWebSocketServerSession.handleScheduleCommand(
    msg: WebSocketMessage,
    wsClientId: String,
    ctx: WsCommandContext,
): Boolean {
    when (msg.type) {
                    Constants.WS_CMD_ADD_TO_SCHEDULE -> {
                        val item = ctx.server.parseRemoteItem(msg.payload)
                            ?: ctx.json.decodeFromString(AddToScheduleRequest.serializer(), msg.payload).item
                        val pending = PendingRemoteRequest(item, wsClientId)
                        ctx.scope.launch {
                            ctx.server.onAddToSchedule.emit(pending)
                            val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                            val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                            try { send(Frame.Text(response)) } catch (_: Exception) { }
                        }
                        // Ack "queued" immediately — the operator's approval can take
                        // minutes, and its outcome still arrives via schedule_updated
                        // (plus the legacy raw {"ok":...} reply above for mobile).
                        sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = ctx.json)
                    }
                    Constants.WS_CMD_ADD_BATCH_TO_SCHEDULE -> {
                        val items = try {
                            ctx.json.decodeFromString(RemoteItemsRequest.serializer(), msg.payload)
                                .items.mapNotNull { it.toScheduleItem() }
                        } catch (_: Exception) { emptyList() }
                        if (items.isNotEmpty()) {
                            val pending = PendingBatchRequest(items, wsClientId)
                            ctx.scope.launch {
                                ctx.server.onAddBatchToSchedule.emit(pending)
                                val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                                val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                                try { send(Frame.Text(response)) } catch (_: Exception) { }
                            }
                            sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = ctx.json)
                        } else {
                            sendCommandAck(msg.commandId, ok = false, reason = "invalid_payload", json = ctx.json)
                        }
                    }
        else -> return handleScheduleItemCommand(msg, wsClientId, ctx)
    }
    return true
}

/** Acts on a row the schedule already holds: project it, or take it out. */
private suspend fun DefaultWebSocketServerSession.handleScheduleItemCommand(
    msg: WebSocketMessage,
    wsClientId: String,
    ctx: WsCommandContext,
): Boolean {
    when (msg.type) {
                    Constants.WS_CMD_PROJECT -> {
                        val item = ctx.server.parseRemoteItem(msg.payload)
                            ?: ctx.json.decodeFromString(ProjectRequest.serializer(), msg.payload).item
                        val pending = PendingRemoteRequest(item, wsClientId)
                        ctx.scope.launch {
                            ctx.server.onProject.emit(pending)
                            val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                            val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                            try { send(Frame.Text(response)) } catch (_: Exception) { }
                        }
                        sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = ctx.json)
                    }
                    Constants.WS_CMD_REMOVE_FROM_SCHEDULE -> {
                        val req = ctx.json.decodeFromString(RemoveFromScheduleRequest.serializer(), msg.payload)
                        val label = ctx.schedule.value.firstOrNull { it.id == req.id }?.displayText ?: req.id
                        val pending = PendingRemoveRequest(req.id, label, wsClientId)
                        ctx.scope.launch {
                            ctx.server.onRemoveFromSchedule.emit(pending)
                            val allowed = try { pending.decision.await() } catch (_: Exception) { false }
                            val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
                            try { send(Frame.Text(response)) } catch (_: Exception) { }
                        }
                        sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = ctx.json)
                    }
        else -> return false
    }
    return true
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

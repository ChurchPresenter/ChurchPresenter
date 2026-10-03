package org.churchpresenter.app.churchpresenter.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents

/**
 * The companion WebSocket: snapshot on connect, then the live command/event stream.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing. The catalogues, the live presentation position and the command flows are all read
 * through [server].
 */
internal fun Route.webSocketRoute(
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
) {
                webSocket(Constants.ENDPOINT_WS) {
                    val queryKey = call.request.queryParameters[Constants.QUERY_PARAM_API_KEY]
                    val headerKey = call.request.headers[Constants.HEADER_API_KEY]
                    if (server._apiKeyEnabled.value && server._apiKey.value.isNotEmpty()) {
                        val provided = queryKey ?: headerKey ?: ""
                        if (provided != server._apiKey.value) {
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
                    if (!isInstanceLinkFollower) UsageEvents.recordOncePerRun(UsageEvent.MOBILE_APP_CONNECTED)
                    if (isInstanceLinkFollower && wsClientId.isNotEmpty()) {
                        server._connectedInstanceLinkFollowers.update { it + wsClientId }
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

                    sendConnectSnapshot(server, json)
                    // The snapshot is complete; anything the collector queued during it now flows.
                    snapshotSent.complete(Unit)

                    // Ack for commands that carried a commandId (InstanceLink controller mode) —
                    // no-op for clients that don't send one, so mobile behavior is unchanged.

                    try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) guardFrame(wsClientId) {
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
                                    return@guardFrame
                                }
                                handleWsCommand(msg, server, wsClientId, json, scope)
                        }
                    }
                    } finally {
                        broadcastJob.cancel()
                        if (isInstanceLinkFollower && wsClientId.isNotEmpty()) {
                            server._connectedInstanceLinkFollowers.update { it - wsClientId }
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
internal suspend fun DefaultWebSocketServerSession.sendCommandAck(
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
 * Everything a freshly connected client is told up front — catalogues, schedule, live state, and
 * the empty-payload invalidation signals a reconnecting follower needs.
 */
private suspend fun DefaultWebSocketServerSession.sendConnectSnapshot(
    server: CompanionServer,
    json: Json,
) {
    val catalog = server._catalog.value
    val schedule = server._schedule.value
    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
        WebSocketMessage(Constants.WS_EVENT_SONGS_UPDATED,
            json.encodeToString(SongCatalogResponse.serializer(), catalog)))))
    server._bibleCatalog.value?.let { bibleCatalog ->
        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
            WebSocketMessage(Constants.WS_EVENT_BIBLE_UPDATED,
                json.encodeToString(BibleCatalogResponse.serializer(), bibleCatalog)))))
    }
    send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
        WebSocketMessage(Constants.WS_EVENT_SCHEDULE_UPDATED,
            json.encodeToString(ScheduleResponse.serializer(), ScheduleResponse(schedule, schedule.size))))))
    val presentationCatalog = server.presentations._presentationCatalog.value
    if (presentationCatalog.presentations.isNotEmpty()) {
        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
            WebSocketMessage(Constants.WS_EVENT_PRESENTATION_UPDATED,
                json.encodeToString(PresentationCatalogResponse.serializer(), presentationCatalog)))))
    }
    server.pictures.catalog.value?.let { pictureCatalog ->
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
    server._liveState.value?.let { state ->
        send(Frame.Text(json.encodeToString(WebSocketMessage.serializer(),
            WebSocketMessage(Constants.WS_EVENT_LIVE_STATE_CHANGED,
                json.encodeToString(LiveStateDto.serializer(), state)))))
    }

}

/** Runs one command frame; a frame that is malformed or whose command fails is logged, not fatal. */
private suspend fun guardFrame(wsClientId: String, handle: suspend () -> Unit) {
    try {
        handle()
    } catch (e: IllegalArgumentException) {
        // Not a WebSocketMessage: serialization errors are this type.
        frameRefused(wsClientId, e)
    } catch (e: IllegalStateException) {
        frameRefused(wsClientId, e)
    } catch (e: IOException) {
        // The command's own reply could not be sent.
        frameRefused(wsClientId, e)
    }
}

private fun frameRefused(wsClientId: String, e: Exception) {
    InstanceLinkLogger.log(
        InstanceLinkLogSide.PRIMARY, "ws_frame_malformed",
        mapOf("deviceId" to wsClientId, "reason" to e.message)
    )
}

package org.churchpresenter.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.settings.utils.Constants

private const val SUMMARY_PREVIEW_CHARS = 60

/** Runs one command frame from a connected client. */
internal suspend fun DefaultWebSocketServerSession.handleWsCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    wsClientId: String,
    json: Json,
    scope: CoroutineScope,
) {
    val signal = signalCommands(server)[msg.type]
    when {
        signal != null -> {
            scope.launch { signal.emit(Unit) }
            sendCommandAck(msg.commandId, ok = true, json = json)
        }
        presentCommand(msg, server, wsClientId, json, scope) -> Unit
        mediaValueCommand(msg, server, json, scope) -> Unit
        scheduleCommand(msg, server, wsClientId, json, scope) -> Unit
        else -> sendCommandAck(msg.commandId, ok = false, reason = "unknown_command", json = json)
    }
}

/** The commands that carry nothing: each one fires its flow and is acked. */
private fun signalCommands(server: CompanionServer): Map<String, MutableSharedFlow<Unit>> = mapOf(
    Constants.WS_CMD_NEXT_PICTURE to server.onNextPicture,
    Constants.WS_CMD_PREVIOUS_PICTURE to server.onPreviousPicture,
    Constants.WS_CMD_NEXT_SLIDE to server.onNextSlide,
    Constants.WS_CMD_PREVIOUS_SLIDE to server.onPreviousSlide,
    Constants.WS_CMD_MEDIA_PLAY_PAUSE to server.onMediaPlayPause,
    Constants.WS_CMD_MEDIA_STOP to server.onMediaStop,
    Constants.WS_CMD_MEDIA_SEEK_FORWARD to server.onMediaSeekForward,
    Constants.WS_CMD_MEDIA_SEEK_BACKWARD to server.onMediaSeekBackward,
    Constants.WS_CMD_MEDIA_MUTE_TOGGLE to server.onMediaMuteToggle,
)

/** Puts something on screen, or clears it. False when [msg] is not one of these. */
private suspend fun DefaultWebSocketServerSession.presentCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    wsClientId: String,
    json: Json,
    scope: CoroutineScope,
): Boolean {
                when (msg.type) {
                    Constants.WS_CMD_SELECT_SONG -> {
                        val song = json.decodeFromString(ScheduleSongDto.serializer(), msg.payload)
                        scope.launch { server.onSongSelected.emit(song) }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_SELECT_PICTURE -> {
                        val req = json.decodeFromString(SelectPictureRequest.serializer(), msg.payload)
                        scope.launch { server.onSelectPicture.emit(req) }
                        val folderName = server.pictures.catalogs[req.folderId]?.folderName ?: req.folderId
                        val imageLabel = req.fileName?.let(RemoteLabel::Text) ?: RemoteLabel.Image(req.index)
                        scope.launch {
                            server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                "present", RemoteLabel.Text(folderName), imageLabel, wsClientId
                            ))
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_SELECT_SONG_SECTION -> {
                        val req = json.decodeFromString(SelectSongSectionRequest.serializer(), msg.payload)
                        scope.launch { server.onSelectSongSection.emit(req) }
                        scope.launch {
                            server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                "present", RemoteLabel.Song(req.number), RemoteLabel.Section(req.section), wsClientId
                            ))
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_SELECT_SLIDE -> {
                        val req = json.decodeFromString(SelectSlideRequest.serializer(), msg.payload)
                        scope.launch { server.onSelectSlide.emit(req) }
                        val presentations = server.presentations
                        val presentationId = presentations._scheduleItemToPresentationId[req.id] ?: req.id
                        val presName = presentations._presentationCatalogs[presentationId]?.fileName ?: req.id
                        scope.launch {
                            server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                "present", RemoteLabel.Text(presName), RemoteLabel.Slide(req.index + 1), wsClientId
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
                                "present",
                                RemoteLabel.Text(ref),
                                RemoteLabel.Text(req.verseText.take(SUMMARY_PREVIEW_CHARS)),
                                wsClientId,
                            ))
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_CLEAR -> {
                        // With a "layer" in the payload, only that layer comes down.
                        val layer = clearLayerOf(msg.payload, json)
                        if (layer != null) {
                            scope.launch { server.onClearLayer.emit(layer) }
                        } else {
                            scope.launch { server.onClear.emit(Unit) }
                            scope.launch {
                                server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                                    "clear", RemoteLabel.EMPTY, clientId = wsClientId
                                ))
                            }
                        }
                        sendCommandAck(msg.commandId, ok = true, json = json)
                    }
                    Constants.WS_CMD_TAKE -> {
                        scope.launch { server.onTake.emit(Unit) }
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
                    else -> return false
                }
                return true
}

/** A media seek or volume, whose payload is the value. False when [msg] is not one of these. */
private suspend fun DefaultWebSocketServerSession.mediaValueCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
): Boolean {
                when (msg.type) {
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
                    else -> return false
                }
                return true
}

/**
 * Asks the operator to change the schedule or project an item; the client hears the decision later.
 * False when [msg] is not one of these.
 */
private suspend fun DefaultWebSocketServerSession.scheduleCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    wsClientId: String,
    json: Json,
    scope: CoroutineScope,
): Boolean {
                when (msg.type) {
                    Constants.WS_CMD_ADD_TO_SCHEDULE -> {
                        val item = server.parseRemoteItem(msg.payload)
                            ?: json.decodeFromString(AddToScheduleRequest.serializer(), msg.payload).item
                        val pending = PendingRemoteRequest(item, wsClientId)
                        scope.launch {
                            server.onAddToSchedule.emit(pending)
                            replyWithDecision(pending.decision)
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
                                replyWithDecision(pending.decision)
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
                            replyWithDecision(pending.decision)
                        }
                        sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = json)
                    }
                    Constants.WS_CMD_REMOVE_FROM_SCHEDULE -> {
                        val req = json.decodeFromString(RemoveFromScheduleRequest.serializer(), msg.payload)
                        val label = server._schedule.value.firstOrNull { it.id == req.id }?.displayText ?: req.id
                        val pending = PendingRemoveRequest(req.id, label, wsClientId)
                        scope.launch {
                            server.onRemoveFromSchedule.emit(pending)
                            replyWithDecision(pending.decision)
                        }
                        sendCommandAck(msg.commandId, ok = true, reason = "pending_approval", json = json)
                    }
                    else -> return false
                }
                return true
}

/** The legacy raw `{"ok":...}` reply a mobile client waits for once the operator decides. */
private suspend fun DefaultWebSocketServerSession.replyWithDecision(decision: CompletableDeferred<Boolean>) {
    val allowed = try { decision.await() } catch (_: Exception) { false }
    val response = if (allowed) """{"ok":true}""" else """{"ok":false,"reason":"denied"}"""
    try { send(Frame.Text(response)) } catch (_: Exception) { }
}

/** The `layer` a WebSocket `clear` names in its payload, or null for a clear of everything. */
internal fun clearLayerOf(payload: String, json: Json): String? = try {
    json.parseToJsonElement(payload).jsonObject["layer"]?.jsonPrimitive?.contentOrNull
} catch (_: IllegalArgumentException) {
    null
}

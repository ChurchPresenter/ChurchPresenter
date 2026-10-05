package org.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.fillMessage
import org.churchpresenter.settings.utils.Constants

/**
 * What a remote client asks to put up as a message: its own [text], or a saved [template] by name
 * or id, with [tokens] filled into either, for [durationSeconds] -- the template's when not given.
 */
@Serializable
data class MessageRequest(
    val text: String? = null,
    val template: String? = null,
    val tokens: Map<String, String> = emptyMap(),
    val durationSeconds: Int? = null,
)

/** A message resolved and ready to go up: what [CompanionServer.onMessage] carries. */
data class RemoteMessage(val text: String, val template: String? = null, val durationSeconds: Int? = null)

/**
 * [request] as it goes up, against the saved [templates], or null when it names a template there is
 * none of, or comes to no text at all.
 */
internal fun resolveMessage(request: MessageRequest, templates: List<MessageTemplate>): RemoteMessage? {
    val template = request.template?.trim()?.takeIf { it.isNotEmpty() }?.let { wanted ->
        templates.firstOrNull { it.id == wanted } ?: templates.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
            ?: return null
    }
    val text = fillMessage(request.text ?: template?.text.orEmpty(), request.tokens).trim()
    if (text.isEmpty()) return null
    return RemoteMessage(text, template?.name, request.durationSeconds ?: template?.durationSeconds)
}

/** POST /api/message: puts a message up -- see [MessageRequest]. */
internal fun Route.messageRoutes(server: CompanionServer, json: Json, scope: CoroutineScope) {
    post(Constants.ENDPOINT_MESSAGE) {
        if (!server.checkApiKey(call)) return@post
        val request = try {
            json.decodeFromString(MessageRequest.serializer(), call.receiveText())
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, """{"ok":false,"reason":"invalid request body"}""")
            return@post
        }
        val message = resolveMessage(request, server.messageTemplates)
        if (message == null) {
            call.respond(HttpStatusCode.BadRequest, """{"ok":false,"reason":"no message"}""")
            return@post
        }
        scope.launch { server.onMessage.emit(message) }
        call.respondText("""{"ok":true,"text":${JsonPrimitive(message.text)}}""", ContentType.Application.Json)
    }
}

/** The WebSocket `message` command, with a [MessageRequest] as its payload. False when [msg] is not one. */
internal suspend fun DefaultWebSocketServerSession.messageCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
): Boolean {
    if (msg.type != Constants.WS_CMD_MESSAGE) return false
    val request = runCatching { json.decodeFromString(MessageRequest.serializer(), msg.payload) }.getOrNull()
    val message = request?.let { resolveMessage(it, server.messageTemplates) }
    if (message == null) {
        sendCommandAck(msg.commandId, ok = false, reason = "no_message", json = json)
    } else {
        scope.launch { server.onMessage.emit(message) }
        sendCommandAck(msg.commandId, ok = true, json = json)
    }
    return true
}

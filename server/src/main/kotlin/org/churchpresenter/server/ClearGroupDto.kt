package org.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.utils.Constants

/** One clear group as `GET /api/clear-groups` lists it. */
@Serializable
data class ClearGroupDto(val id: String, val name: String, val layers: List<String>)

/** The clear group a remote client names, by id or by name in any case, or null for one there is none of. */
internal fun findClearGroup(groups: List<ClearGroup>, wanted: String): ClearGroup? =
    groups.firstOrNull { it.id == wanted } ?: groups.firstOrNull { it.name.equals(wanted.trim(), ignoreCase = true) }

/** GET /api/clear-groups: the clear groups a remote client may fire. */
internal fun Route.clearGroupRoutes(server: CompanionServer, json: Json) {
    get(Constants.ENDPOINT_CLEAR_GROUPS) {
        if (!server.checkApiKey(call)) return@get
        if (!call.requireDevMode(server)) return@get
        val listing = server.clearGroups.map { ClearGroupDto(it.id, it.name, it.layers) }
        val body = json.encodeToString(ListSerializer(ClearGroupDto.serializer()), listing)
        call.respondText(body, ContentType.Application.Json)
    }
}

/** Answers `POST /api/clear?group=`: fires the group [wanted] names, or 404 when there is none. */
internal suspend fun ApplicationCall.respondClearGroup(server: CompanionServer, scope: CoroutineScope, wanted: String) {
    val group = findClearGroup(server.clearGroups, wanted)
    if (group == null) {
        respond(HttpStatusCode.NotFound, """{"ok":false,"reason":"no such clear group"}""")
    } else {
        scope.launch { server.onClearGroup.emit(group.id) }
        respondText("""{"ok":true}""", ContentType.Application.Json)
    }
}

/**
 * The WebSocket `clear` command with a `group` in its payload: fires that group. False when the
 * payload names no group, so the command clears a layer or everything instead.
 */
internal suspend fun DefaultWebSocketServerSession.clearGroupCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
): Boolean {
    val wanted = runCatching { json.parseToJsonElement(msg.payload).jsonObject["group"]?.jsonPrimitive?.contentOrNull }
        .getOrNull() ?: return false
    if (refusedOutsideDevMode(msg, server, json)) return true
    val group = findClearGroup(server.clearGroups, wanted)
    if (group == null) {
        sendCommandAck(msg.commandId, ok = false, reason = "no_such_group", json = json)
    } else {
        scope.launch { server.onClearGroup.emit(group.id) }
        sendCommandAck(msg.commandId, ok = true, json = json)
    }
    return true
}

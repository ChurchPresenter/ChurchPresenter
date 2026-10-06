package org.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.utils.Constants

/** A remote client switching the prop [id]: on, off, or -- with [on] null -- the other way. */
data class PropSwitch(val id: String, val on: Boolean?)

/** One prop as `GET /api/props` lists it. */
@Serializable
data class PropDto(val id: String, val name: String, val kind: String, val on: Boolean)

/** The props a remote client may switch, each with whether it is up -- what `GET /api/props` answers. */
internal fun propsListing(props: List<PropDefinition>, onAir: Set<String>): List<PropDto> =
    props.map { PropDto(it.id, it.name, it.kind.name, it.id in onAir) }

/** The prop a remote client names, by id or by name in any case, or null for one there is none of. */
internal fun findProp(props: List<PropDefinition>, wanted: String): PropDefinition? =
    props.firstOrNull { it.id == wanted } ?: props.firstOrNull { it.name.equals(wanted.trim(), ignoreCase = true) }

/** What `/api/props/{id}/{action}` asks for: true for on, false for off, null to toggle, or no switch at all. */
internal fun propAction(action: String?): Result<Boolean?> = when (action) {
    "on" -> Result.success(true)
    "off" -> Result.success(false)
    "toggle" -> Result.success(null)
    else -> Result.failure(IllegalArgumentException("unknown action"))
}

/** GET /api/props and POST /api/props/{id}/on|off|toggle. */
internal fun Route.propRoutes(server: CompanionServer, json: Json, scope: CoroutineScope) {
    get(Constants.ENDPOINT_PROPS) {
        if (!server.checkApiKey(call)) return@get
        if (!call.requireDevMode(server)) return@get
        val onAir = server.liveState.value?.props.orEmpty().toSet()
        val body = json.encodeToString(ListSerializer(PropDto.serializer()), propsListing(server.props, onAir))
        call.respondText(body, ContentType.Application.Json)
    }
    post("${Constants.ENDPOINT_PROPS}/{id}/{action}") {
        if (!server.checkApiKey(call)) return@post
        if (!call.requireDevMode(server)) return@post
        val prop = findProp(server.props, call.parameters["id"].orEmpty())
        val action = propAction(call.parameters["action"])
        when {
            prop == null -> call.respond(HttpStatusCode.NotFound, """{"ok":false,"reason":"no such prop"}""")
            action.isFailure -> call.respond(HttpStatusCode.BadRequest, """{"ok":false,"reason":"unknown action"}""")
            else -> {
                scope.launch { server.onProp.emit(PropSwitch(prop.id, action.getOrNull())) }
                call.respondText("""{"ok":true}""", ContentType.Application.Json)
            }
        }
    }
}

/**
 * The WebSocket `prop` command: `{"id": "...", "on": true|false}`, or with no `on` to toggle. False
 * when [msg] is not one.
 */
internal suspend fun DefaultWebSocketServerSession.propCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
): Boolean {
    if (msg.type != Constants.WS_CMD_PROP) return false
    if (refusedOutsideDevMode(msg, server, json)) return true
    val payload = runCatching { json.parseToJsonElement(msg.payload).jsonObject }.getOrNull()
    val prop = payload?.get("id")?.jsonPrimitive?.content?.let { findProp(server.props, it) }
    if (prop == null) {
        sendCommandAck(msg.commandId, ok = false, reason = "no_such_prop", json = json)
    } else {
        val on = payload["on"]?.jsonPrimitive?.booleanOrNull
        scope.launch { server.onProp.emit(PropSwitch(prop.id, on)) }
        sendCommandAck(msg.commandId, ok = true, json = json)
    }
    return true
}

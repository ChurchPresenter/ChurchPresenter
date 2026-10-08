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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.settings.macroNamed
import org.churchpresenter.settings.utils.Constants

/** GET /api/macros and POST /api/macro/{name}: list the macros, and run one by id or name. */
internal fun Route.macroRoutes(server: CompanionServer, json: Json, scope: CoroutineScope) {
    get(Constants.ENDPOINT_MACROS) {
        if (!server.checkApiKey(call)) return@get
        if (!call.requireDevMode(server)) return@get
        val body = json.encodeToString(ListSerializer(MacroDto.serializer()), macrosListing(server.macros))
        call.respondText(body, ContentType.Application.Json)
    }
    post("${Constants.ENDPOINT_MACRO}/{name}") {
        if (!server.checkApiKey(call)) return@post
        if (!call.requireDevMode(server)) return@post
        val macro = server.macros.macroNamed(call.parameters["name"].orEmpty())
        if (macro == null) {
            call.respond(HttpStatusCode.NotFound, """{"ok":false,"reason":"no such macro"}""")
        } else {
            scope.launch { server.onMacro.emit(macro.id) }
            call.respondText("""{"ok":true}""", ContentType.Application.Json)
        }
    }
}

/** The WebSocket `macro` command: `{"name": "..."}`, a macro's id or name. False when [msg] is not one. */
internal suspend fun DefaultWebSocketServerSession.macroCommand(
    msg: WebSocketMessage,
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
): Boolean {
    if (msg.type != Constants.WS_CMD_MACRO) return false
    if (refusedOutsideDevMode(msg, server, json)) return true
    val wanted = runCatching { json.parseToJsonElement(msg.payload).jsonObject["name"]?.jsonPrimitive?.contentOrNull }
        .getOrNull()
    val macro = wanted?.let(server.macros::macroNamed)
    if (macro == null) {
        sendCommandAck(msg.commandId, ok = false, reason = "no_such_macro", json = json)
    } else {
        scope.launch { server.onMacro.emit(macro.id) }
        sendCommandAck(msg.commandId, ok = true, json = json)
    }
    return true
}

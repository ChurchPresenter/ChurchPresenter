package org.churchpresenter.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.Json

/**
 * Whether the app is in dev mode, where the features not yet ready for production answer remote
 * clients (messages, props, macros, clear groups and single layers, Take). Answers 403 and returns
 * false when it is not.
 */
internal suspend fun ApplicationCall.requireDevMode(server: CompanionServer): Boolean {
    if (server.devMode) return true
    respond(HttpStatusCode.Forbidden, """{"ok":false,"reason":"dev mode only"}""")
    return false
}

/** The WebSocket side of [requireDevMode]: acks [msg] as refused and returns true when dev mode is off. */
internal suspend fun DefaultWebSocketServerSession.refusedOutsideDevMode(
    msg: WebSocketMessage,
    server: CompanionServer,
    json: Json,
): Boolean {
    if (server.devMode) return false
    sendCommandAck(msg.commandId, ok = false, reason = "dev_mode_only", json = json)
    return true
}

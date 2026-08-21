package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants

/**
 * Routes for reading and mutating the schedule from a companion device.
 *
 * Body moved verbatim from `CompanionServer` — its raw-string literals make the indentation
 * load-bearing. Private state arrives as identically-named parameters so the body needed no
 * rewriting; [server] carries the approval flows and helpers that must be reached at request time.
 */
internal fun Route.scheduleRoutes(
    server: CompanionServer,
    _schedule: MutableStateFlow<List<ScheduleItemDto>>,
    json: Json,
    scope: CoroutineScope,
) {
                get(Constants.ENDPOINT_SCHEDULE) {
                    if (!server.checkApiKey(call)) return@get
                    val schedule = _schedule.value
                    call.respond(ScheduleResponse(schedule, schedule.size))
                }

                /**
                 * POST /api/schedule/add
                 * Suspends until the user approves or denies the request in the UI.
                 * Returns {"ok":true} on Allow, {"ok":false,"reason":"denied"} on Deny,
                 * or {"ok":false,"reason":"blocked"} if the session is blocked.
                 */
                post(Constants.ENDPOINT_SCHEDULE_ADD) {
                    if (!server.checkApiKey(call)) return@post
                    val body = call.receiveText()
                    val item = server.parseRemoteItem(body)
                    if (item == null) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingRemoteRequest(item, clientId)
                    scope.launch { server.onAddToSchedule.emit(pending) }
                    val allowed = pending.decision.await()
                    if (allowed) {
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    } else {
                        call.respond(HttpStatusCode.Forbidden,
                            """{"ok":false,"reason":"${pending.decision.let { "denied" }}"}""")
                    }
                }

                /**
                 * POST /api/schedule/add-batch
                 * Adds multiple items in a single call.  Suspends until the user approves or denies
                 * the whole batch.  On Allow every valid item is added; on Deny nothing is added.
                 *
                 * Request body:
                 * {
                 *   "items": [
                 *     { "bookName": "John",  "chapter": 3, "verseNumber": 16, "verseText": "For God so loved…" },
                 *     { "bookName": "John",  "chapter": 3, "verseNumber": 17, "verseText": "For God did not send…" }
                 *   ]
                 * }
                 *
                 * Success:  {"ok":true,"added":2}
                 * Denied:   HTTP 403  {"ok":false,"reason":"denied"}
                 * Bad body: HTTP 400  {"error":"…"}
                 */
                post(Constants.ENDPOINT_SCHEDULE_ADD_BATCH) {
                    if (!server.checkApiKey(call)) return@post
                    val body = call.receiveText()
                    val items = try {
                        json.decodeFromString(RemoteItemsRequest.serializer(), body)
                            .items.mapNotNull { it.toScheduleItem() }
                    } catch (_: Exception) { null }
                    if (items.isNullOrEmpty()) {
                        call.respond(HttpStatusCode.BadRequest,
                            """{"error":"invalid request body or no recognisable items"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingBatchRequest(items, clientId)
                    scope.launch { server.onAddBatchToSchedule.emit(pending) }
                    val allowed = pending.decision.await()
                    if (allowed) {
                        call.respondText("""{"ok":true,"added":${items.size}}""", ContentType.Application.Json)
                    } else {
                        call.respond(HttpStatusCode.Forbidden,
                            """{"ok":false,"reason":"denied"}""")
                    }
                }

                /**
                 * POST /api/project
                 * Same suspend-until-approved behaviour as /api/schedule/add.
                 */
                post(Constants.ENDPOINT_PROJECT) {
                    if (!server.checkApiKey(call)) return@post
                    val body = call.receiveText()
                    val item = server.parseRemoteItem(body)
                    if (item == null) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = PendingRemoteRequest(item, clientId)
                    scope.launch { server.onProject.emit(pending) }
                    val allowed = pending.decision.await()
                    if (allowed) {
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    } else {
                        call.respond(HttpStatusCode.Forbidden,
                            """{"ok":false,"reason":"denied"}""")
                    }
                }

                /**
                 * POST /api/clear
                 * Instantly switches the presenter to display-none (Presenting.NONE).
                 * No request body or approval needed.
                 * Response: {"ok":true}
                 */
                post(Constants.ENDPOINT_CLEAR) {
                    if (!server.checkApiKey(call)) return@post
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    scope.launch { server.onClear.emit(Unit) }
                    scope.launch { server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                        actionType = "clear",
                        title = "Clear Display",
                        clientId = clientId
                    )) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

}

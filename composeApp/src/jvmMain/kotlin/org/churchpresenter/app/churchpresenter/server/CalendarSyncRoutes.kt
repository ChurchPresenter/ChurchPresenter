package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.sync.Sanitize
import org.churchpresenter.settings.utils.Constants
import java.util.concurrent.ConcurrentHashMap

private const val MAX_DEVICE_NAME_CHARS = 120
private const val MAX_BODY_CHARS = 4_096
private const val CODE_DIGITS = 6

/** What a phone sends when it asks to plan the calendar: a name, and the code it is showing. */
@Serializable
internal data class CalendarEnrollBody(val deviceName: String = "", val code: String = "")

/** What the phone gets back: where the relay is and which instance. The rest comes by QR. */
@Serializable
data class CalendarEnrollReply(val relayUrl: String, val instanceId: String)

/** A phone asking to be enrolled with the calendar relay; [decision] is the reply once the operator has decided. */
data class PendingCalendarEnroll(
    val clientId: String,
    val deviceName: String,
    val code: String,
    val decision: CompletableDeferred<CalendarEnrollReply?> = CompletableDeferred(),
)

/** `POST /api/calendar/enroll` — a phone asking to plan the calendar; the operator approves it by name and code. */
internal fun Route.calendarSyncRoutes(server: CompanionServer, json: Json) {
    post(Constants.ENDPOINT_CALENDAR_ENROLL) {
        if (!server.checkApiKey(call)) return@post
        val clientId = call.request.headers[Constants.HEADER_DEVICE_ID].orEmpty()
        if (!Sanitize.isId(clientId)) {
            call.respondText("device id required", status = HttpStatusCode.BadRequest)
            return@post
        }
        val body = call.receiveText().take(MAX_BODY_CHARS)
        val parsed = runCatching { json.decodeFromString(CalendarEnrollBody.serializer(), body) }.getOrDefault(CalendarEnrollBody())
        val code = parsed.code.filter { it.isDigit() }.take(CODE_DIGITS)
        if (code.length != CODE_DIGITS) {
            call.respondText("code required", status = HttpStatusCode.BadRequest)
            return@post
        }
        val pending = PendingCalendarEnroll(
            clientId = clientId,
            deviceName = Sanitize.cleanText(parsed.deviceName, MAX_DEVICE_NAME_CHARS),
            code = code,
        )
        // One open request per device and a small total: anyone on the church WiFi holding the API key
        // could otherwise pile up requests, and prompts, for as long as they liked.
        if (pendingEnrollClients.size >= MAX_PENDING_ENROLLMENTS || !pendingEnrollClients.add(clientId)) {
            call.respondText("""{"error":"enrollment already pending"}""", status = HttpStatusCode.TooManyRequests)
            return@post
        }
        try {
            server.onCalendarEnroll.emit(pending)
            val reply = withTimeoutOrNull(ENROLL_WAIT_MS) { pending.decision.await() }
            // Unanswered in time: settle it as denied so an Allow clicked later does nothing.
            val timedOut = !pending.decision.isCompleted
            if (timedOut) pending.decision.complete(null)
            when {
                reply != null -> call.respond(reply)
                timedOut -> call.respondText("""{"error":"enrollment timed out"}""", status = HttpStatusCode.RequestTimeout)
                else -> call.respondText("""{"error":"enrollment denied"}""", status = HttpStatusCode.Forbidden)
            }
        } finally {
            pendingEnrollClients.remove(clientId)
        }
    }
}

private const val ENROLL_WAIT_MS = 120_000L
private const val MAX_PENDING_ENROLLMENTS = 5
private val pendingEnrollClients: MutableSet<String> = ConcurrentHashMap.newKeySet()

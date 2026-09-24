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

/**
 * What the phone gets back once the operator allows it: everything the QR would have carried, so
 * that Allow is the last step. The operator comparing the code on the phone with the one in their
 * prompt is what makes this safe; a second step to scan the same thing added nothing to that.
 */
@Serializable
data class CalendarEnrollReply(
    val relayUrl: String,
    val instanceId: String,
    val deviceId: String,
    val deviceToken: String,
    val instanceKey: String,
)

/** How a phone's request to be enrolled ended, each answered differently so the phone can say why. */
sealed class CalendarEnrollDecision {
    /** The operator allowed it and the desktop is registered: here is where to go, and the keys to get in. */
    data class Approved(val reply: CalendarEnrollReply) : CalendarEnrollDecision()

    /** The operator refused, the device is blocked, or nobody answered in time. */
    data object Denied : CalendarEnrollDecision()

    /** Calendar sync is switched off on the desktop, so there is nothing to enroll into. */
    data object SyncOff : CalendarEnrollDecision()

    /** Allowed, but the desktop could not register with the relay or enroll the phone. */
    data object RelayFailed : CalendarEnrollDecision()
}

/** An enrollment as the phone that asked for it over the LAN receives it. */
fun CalendarEnrollment.asReply(): CalendarEnrollReply =
    CalendarEnrollReply(relayUrl.trimEnd('/'), instanceId, deviceId, deviceToken, instanceKey)

/** A phone asking to be enrolled with the calendar relay; [decision] is the answer once the operator has decided. */
data class PendingCalendarEnroll(
    val clientId: String,
    val deviceName: String,
    val code: String,
    val decision: CompletableDeferred<CalendarEnrollDecision> = CompletableDeferred(),
)

/** The body the phone reads when the desktop's sync switch is off. */
const val ENROLL_ERROR_SYNC_OFF = "sync_off"

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
        val parsed = runCatching { json.decodeFromString(CalendarEnrollBody.serializer(), body) }
            .getOrDefault(CalendarEnrollBody())
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
            val decision = withTimeoutOrNull(ENROLL_WAIT_MS) { pending.decision.await() }
            // Unanswered in time: settle it as denied so an Allow clicked later does nothing.
            if (decision == null) pending.decision.complete(CalendarEnrollDecision.Denied)
            when (decision) {
                is CalendarEnrollDecision.Approved -> call.respond(decision.reply)
                CalendarEnrollDecision.SyncOff ->
                    call.respondText("""{"error":"$ENROLL_ERROR_SYNC_OFF"}""", status = HttpStatusCode.Conflict)
                CalendarEnrollDecision.RelayFailed ->
                    call.respondText("""{"error":"relay unreachable"}""", status = HttpStatusCode.BadGateway)
                CalendarEnrollDecision.Denied ->
                    call.respondText("""{"error":"enrollment denied"}""", status = HttpStatusCode.Forbidden)
                null -> call.respondText(
                    """{"error":"enrollment timed out"}""",
                    status = HttpStatusCode.RequestTimeout,
                )
            }
        } finally {
            pendingEnrollClients.remove(clientId)
        }
    }
}

private const val ENROLL_WAIT_MS = 120_000L
private const val MAX_PENDING_ENROLLMENTS = 5
private val pendingEnrollClients: MutableSet<String> = ConcurrentHashMap.newKeySet()

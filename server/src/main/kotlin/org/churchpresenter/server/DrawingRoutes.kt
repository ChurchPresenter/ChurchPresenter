package org.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.settings.utils.Constants

/** The largest page a device may send, in bytes: a 1080p page as a PNG is a few hundred KB. */
internal const val DRAWING_MAX_BYTES = 8 * 1024 * 1024

/** The eight bytes every PNG starts with. */
private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)

private val PAPER_COLOUR = Regex("^#[0-9A-Fa-f]{6}$")

/**
 * A page drawn on a phone or tablet's canvas, put on the outputs — see `COMPANION_API.md`, Drawing.
 *
 * - `POST /api/drawing/frame?takeOver=true` is Go Live: it asks the operator exactly as
 *   `POST /api/project` does, and waits for the answer.
 * - Without `takeOver` a page only replaces one that is live; it never takes the screen back.
 * - `POST /api/drawing/end` takes the device's drawing down, or withdraws its waiting request.
 *
 * Every page is kept in memory only, the latest of each device's, in [DrawingDesk].
 */
internal fun Route.drawingRoutes(server: CompanionServer, scope: CoroutineScope) {
    post(Constants.ENDPOINT_DRAWING_FRAME) {
        if (!call.allowedTo(server, DevicePermission.GO_LIVE)) return@post
        val declared = call.request.headers["Content-Length"]?.toLongOrNull() ?: 0L
        if (declared > DRAWING_MAX_BYTES) {
            call.respondText(TOO_LARGE, ContentType.Application.Json, HttpStatusCode.PayloadTooLarge)
            return@post
        }
        // Read at most one byte past the cap, so a body that lied about its length still stops there.
        val png = withContext(Dispatchers.IO) { call.receiveStream().use { it.readNBytes(DRAWING_MAX_BYTES + 1) } }
        when {
            png.size > DRAWING_MAX_BYTES ->
                call.respondText(TOO_LARGE, ContentType.Application.Json, HttpStatusCode.PayloadTooLarge)
            !isPng(png) ->
                call.respondText(BAD_IMAGE, ContentType.Application.Json, HttpStatusCode.BadRequest)
            else -> {
                val params = call.request.queryParameters
                val frame = DrawingFrame(
                    clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: "",
                    png = png,
                    paper = params["paper"]?.takeIf { PAPER_COLOUR.matches(it) },
                    title = params["title"].orEmpty().take(MAX_TITLE),
                )
                if (params["takeOver"] == "true") {
                    goLive(server, scope, frame)
                } else {
                    call.respondText(showingJson(refresh(server, frame)), ContentType.Application.Json)
                }
            }
        }
    }

    post(Constants.ENDPOINT_DRAWING_END) {
        if (!call.allowedTo(server, DevicePermission.GO_LIVE)) return@post
        val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
        server.drawings.withdraw(clientId)?.decision?.complete(false)
        if (server.drawings.end(clientId)) scope.launch { server.onDrawingEnd.emit(clientId) }
        call.respondText(OK, ContentType.Application.Json)
    }
}

/** Puts [frame]'s drawing to the operator and answers once they have: shown, or denied. */
private suspend fun io.ktor.server.routing.RoutingContext.goLive(
    server: CompanionServer,
    scope: CoroutineScope,
    frame: DrawingFrame,
) {
    val (request, replaced) = server.drawings.ask(frame)
    replaced?.decision?.complete(false)
    scope.launch { server.onDrawingRequest.emit(request) }
    val approved = request.decision.await()
    server.drawings.settle(request)
    if (approved) {
        call.respondText(showingJson(true), ContentType.Application.Json)
    } else {
        call.respondText(DENIED, ContentType.Application.Json, HttpStatusCode.Forbidden)
    }
}

/**
 * A page that is not a Go Live: put up when its drawing is the live one, kept for the operator
 * when its Go Live is waiting, and otherwise dropped. Returns whether it is on the outputs.
 */
private fun refresh(server: CompanionServer, frame: DrawingFrame): Boolean = when {
    server.drawings.isLive(frame.clientId) -> {
        server.drawingFrames.value = frame
        true
    }
    else -> {
        server.drawings.hold(frame)
        false
    }
}

/** Whether [bytes] begins as a PNG does. The app decodes it; this only turns away what cannot be one. */
internal fun isPng(bytes: ByteArray): Boolean =
    bytes.size > PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }

private fun showingJson(showing: Boolean) = """{"ok":true,"showing":$showing}"""

private const val OK = """{"ok":true}"""
private const val TOO_LARGE = """{"ok":false,"reason":"too_large"}"""
private const val BAD_IMAGE = """{"ok":false,"reason":"bad_image"}"""

/** As `POST /api/project` answers a denial, with whether the page is showing beside it. */
private const val DENIED = """{"ok":false,"reason":"denied","showing":false}"""

/** The longest title the operator's prompt is given. */
private const val MAX_TITLE = 200

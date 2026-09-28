package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.churchpresenter.app.churchpresenter.presenter.BrowserSourceFrame
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

/**
 * Routes for the OBS/vMix Browser Source overlay page and its frame WebSocket.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing (this group serves an HTML page built as one).
 */
internal fun Route.browserSourceRoutes(
    server: CompanionServer,
    _browserSourceFrameFlows: ConcurrentHashMap<Int, SharedFlow<BrowserSourceFrame>>,
    _browserSourceSessions: ConcurrentHashMap<Int, MutableSet<DefaultWebSocketServerSession>>,
) {
                get("${Constants.ENDPOINT_BROWSER_SOURCE}/{index}") {
                    // Path segment is the 1-based number shown in Projection Settings
                    // (e.g. "Browser Source 1" -> /browser-source/1); convert to the
                    // 0-based array index for lookups.
                    val displayIndex = call.parameters["index"]?.toIntOrNull()
                    val index = displayIndex?.minus(1)
                    val output = index?.let { server.browserSourceOutput(it) }
                    if (displayIndex == null || output == null) {
                        call.respond(HttpStatusCode.NotFound, "Unknown browser source output")
                        return@get
                    }
                    if (!output.browserSourceEnabled) {
                        call.respond(HttpStatusCode.NotFound, "Browser source output is disabled")
                        return@get
                    }
                    if (!server.browserSource.checkBrowserSourceApiKey(call, output)) return@get
                    val bgOverride = call.request.queryParameters["bg"]
                    // OBS/browsers cache this page aggressively and won't refetch it on their own
                    // (OBS requires an explicit "Refresh cache of current page"). no-store ensures
                    // a client always gets JS that matches this server's current wire protocol,
                    // rather than silently running stale JS against a since-changed stream format.
                    call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                    call.respondText(server.browserSourceOverlayPageHtml(displayIndex, output, bgOverride), ContentType.Text.Html)
                }

                // WebSocket delta stream of this output's off-screen-rendered content — see
                // BrowserSourceVideoRenderer in main.kt. A frame is only pushed when its pixels
                // actually changed since the previous tick, so a static slide costs one frame,
                // not continuous encoding. Each message is usually just the changed sub-rectangle,
                // not the full frame — see server.browserSource.encodeBrowserSourceFrameMessage for the binary layout.
                // The client composites deltas onto an offscreen full-frame canvas (see
                // server.browserSourceOverlayPageHtml below). Previously HTTP multipart/x-mixed-replace;
                // see the comment above _browserSourceFrameFlows for why that was replaced.
                webSocket("/api${Constants.ENDPOINT_BROWSER_SOURCE}/{index}/ws") {
                    // Same 1-based -> 0-based conversion as the overlay page route above, since
                    // this URL is embedded inside that page using the same display index. A
                    // WebSocket route is already past the handshake by the time this block runs,
                    // so invalid requests are rejected via close(CloseReason(...)) rather than an
                    // HTTP status code — the client reads the close reason to show a diagnostic.
                    val displayIndex = call.parameters["index"]?.toIntOrNull()
                    val index = displayIndex?.minus(1)
                    val output = index?.let { server.browserSourceOutput(it) }
                    if (displayIndex == null || output == null) {
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Unknown browser source output"))
                        return@webSocket
                    }
                    if (!output.browserSourceEnabled) {
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Browser source output is disabled"))
                        return@webSocket
                    }
                    if (!server.browserSource.browserSourceApiKeyValid(call, output)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid API key"))
                        return@webSocket
                    }
                    val frames = _browserSourceFrameFlows[index]
                    if (frames == null) {
                        close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Renderer not ready"))
                        return@webSocket
                    }
                    // Track the session so server.registerBrowserSourceFrames can close it if this
                    // output's renderer is replaced (the captured `frames` flow goes dead then).
                    val sessions = _browserSourceSessions.computeIfAbsent(index) {
                        ConcurrentHashMap.newKeySet()
                    }
                    sessions.add(this)
                    // A frame is only ever emitted on a real content change, so a static slide
                    // can leave this connection completely silent for minutes. Safari (and
                    // plenty of consumer routers doing NAT connection tracking) have been
                    // observed killing an idle streaming connection after roughly 30-60s of no
                    // data. Re-sending the last known frame on a timer keeps it alive; the client
                    // draws it identically to before since it's the exact same bytes/rect.
                    // Sends are Mutex-serialized: the frame collector and the heartbeat run in
                    // separate coroutines, and a WebSocket session does not allow concurrent send.
                    val lastFrame = AtomicReference<BrowserSourceFrame?>(null)
                    val sendMutex = Mutex()
                    suspend fun sendFrame(frame: BrowserSourceFrame) {
                        sendMutex.withLock {
                            send(Frame.Binary(true, server.browserSource.encodeBrowserSourceFrameMessage(frame)))
                        }
                    }
                    // Launched in the session scope (not the server scope) so they can never
                    // outlive this connection; the finally-cancel below is belt-and-braces.
                    val frameJob = launch {
                        frames.collect { frame ->
                            lastFrame.set(frame)
                            sendFrame(frame)
                        }
                    }
                    val heartbeatJob = launch {
                        while (true) {
                            delay(15_000)
                            lastFrame.get()?.let { sendFrame(it) }
                        }
                    }
                    val transposeJob = launchTransposeState(server.browserSource, index, sendMutex)
                    try {
                        for (frame in incoming) {
                            // One-way server push — inbound frames (pings/pongs aside, handled by
                            // the engine) aren't meaningful here; just keep the session open until
                            // the client disconnects.
                        }
                    } catch (_: Exception) {
                        // client disconnected
                    } finally {
                        sessions.remove(this)
                        frameJob.cancel()
                        heartbeatJob.cancel()
                        transposeJob.cancel()
                    }
                }

                // ── Q&A Endpoints ─────────────────────────────────────────────────

                // Public: submission page
}

/**
 * Sends a page of output [index] its transpose state, as text, while that output's profile offers
 * the buttons -- plus one `controls:false` when it stops, so an open tablet hides them. An output
 * that never offers them is never sent a text frame at all. Launched in the session's own scope.
 */
private fun DefaultWebSocketServerSession.launchTransposeState(hub: BrowserSourceHub, index: Int, sendMutex: Mutex) =
    launch {
        var shown = false
        combine(hub.transposes, hub.transposeControls) { transposes, controls ->
            (index in controls) to hub.transposeState(transposes, controls, index)
        }.distinctUntilChanged().collect { (offered, state) ->
            if (offered || shown) {
                sendMutex.withLock { send(Frame.Text(state)) }
            }
            shown = offered
        }
    }

/**
 * The musicians' transpose buttons on a Browser Source page (issue #649): the handshake and the
 * press. Only an output whose profile offers the buttons accepts either, so an OBS/vMix output can
 * never be moved from its own page; and a press is taken only from a device the desktop approved.
 * Commands never ride the frame socket, which stays one-way.
 */
internal fun Route.browserSourceTransposeRoutes(server: CompanionServer) {
    // The desktop approves the device once, on the page's own handshake -- not on every press.
    post("/api${Constants.ENDPOINT_BROWSER_SOURCE}/{index}/auth") {
        val (displayIndex, output) = transposableOutput(server) ?: return@post
        if (!server.browserSource.checkBrowserSourceApiKey(call, output)) return@post
        if (!server.checkMusicianConnect(call)) return@post
        call.respondText("""{"ok":true,"output":$displayIndex}""", ContentType.Application.Json)
    }

    post("/api${Constants.ENDPOINT_BROWSER_SOURCE}/{index}/transpose") {
        val (displayIndex, output) = transposableOutput(server) ?: return@post
        if (!server.browserSource.checkBrowserSourceApiKey(call, output)) return@post
        if (!server.browserSource.isApprovedMusician(call.request.headers[Constants.HEADER_DEVICE_ID])) {
            call.respond(HttpStatusCode.Forbidden, """{"error":"device not approved"}""")
            return@post
        }
        val command = parseTransposeCommand(displayIndex - 1, call.receiveText())
        if (command == null) {
            call.respond(HttpStatusCode.BadRequest, """{"error":"expected delta or reset"}""")
            return@post
        }
        server.onBrowserSourceTranspose.emit(command)
        call.respondText("""{"ok":true}""", ContentType.Application.Json)
    }
}

/**
 * The 1-based display index and the output a transpose route names, or null once it has been
 * refused: 404 for an unknown or disabled output, 403 for one whose profile does not offer the
 * transpose buttons.
 */
private suspend fun RoutingContext.transposableOutput(server: CompanionServer): Pair<Int, ScreenAssignment>? {
    val displayIndex = call.parameters["index"]?.toIntOrNull()
    val output = displayIndex?.let { server.browserSourceOutput(it - 1) }
    if (displayIndex == null || output == null || !output.browserSourceEnabled) {
        call.respond(HttpStatusCode.NotFound, "Unknown browser source output")
        return null
    }
    if (!server.browserSource.offersTranspose(displayIndex - 1)) {
        call.respond(HttpStatusCode.Forbidden, """{"error":"this output has no transpose buttons"}""")
        return null
    }
    return displayIndex to output
}

/** A transpose body — `{"delta":±1}` or `{"reset":true}` — for the output at [index], or null. */
internal fun parseTransposeCommand(index: Int, body: String): BrowserSourceTransposeCommand? {
    val obj = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    if ((obj["reset"] as? JsonPrimitive)?.booleanOrNull == true) {
        return BrowserSourceTransposeCommand(index, reset = true)
    }
    val delta = (obj["delta"] as? JsonPrimitive)?.intOrNull ?: return null
    return if (delta == 1 || delta == -1) BrowserSourceTransposeCommand(index, delta = delta) else null
}

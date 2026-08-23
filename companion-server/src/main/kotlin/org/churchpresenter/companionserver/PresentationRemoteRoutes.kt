package org.churchpresenter.companionserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Routes for the phone-in-hand presentation remote.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing. Almost every dependency here is mutable and read per request, so it is reached
 * through [server] rather than captured.
 */
internal fun Route.presentationRemoteRoutes(
    server: CompanionServer,
    _presentationNotes: ConcurrentHashMap<String, List<String>>,
    scope: CoroutineScope,
) {
                get("/presentation-remote") {
                    call.respondText(presentationRemotePageHtml(), ContentType.Text.Html)
                }

                /** GET /api/presentation-remote/status — current presentation state (no auth needed) */
                get("/api/presentation-remote/status") {
                    val note = _presentationNotes[server._currentPresentationId]
                        ?.getOrNull(server._currentSlideIndex) ?: ""
                    call.respondText(
                        """{"enabled":${server.presentationRemoteEnabled},""" +
                            """"id":"${server._currentPresentationId}",""" +
                            """"index":${server._currentSlideIndex},""" +
                            """"total":${server._currentSlideTotalCount},""" +
                            """"frozen":${server._presentationFrozen},""" +
                            """"isPlaying":${server._presentationIsPlaying},""" +
                            """"isLive":${server._presentationIsLive},""" +
                            """"autoScrollInterval":${server._autoScrollInterval},""" +
                            """"looping":${server._presentationIsLooping},""" +
                            """"passwordRequired":${server.presentationRemotePassword.isNotEmpty()},""" +
                            """"notes":"${jsonEscape(note)}"}""",
                        ContentType.Application.Json
                    )
                }

                /** POST /api/presentation-remote/auth — verify password, then ask the operator to approve the device */
                post("/api/presentation-remote/auth") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    if (!server.checkPresentationRemoteConnect(call)) return@post
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/next */
                post("/api/presentation-remote/next") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    val next = (server._currentSlideIndex + 1).coerceAtMost(server._currentSlideTotalCount - 1)
                    scope.launch { server.onPresentationGoto.emit(next) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/previous */
                post("/api/presentation-remote/previous") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    val prev = (server._currentSlideIndex - 1).coerceAtLeast(0)
                    scope.launch { server.onPresentationGoto.emit(prev) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/goto/{index} */
                post("/api/presentation-remote/goto/{index}") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    val index = call.parameters["index"]?.toIntOrNull()
                        ?: run { call.respond(HttpStatusCode.BadRequest, """{"error":"missing index"}"""); return@post }
                    val clamped = index.coerceIn(0, (server._currentSlideTotalCount - 1).coerceAtLeast(0))
                    scope.launch { server.onPresentationGoto.emit(clamped) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/freeze — toggle blank/unblank */
                post("/api/presentation-remote/freeze") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    scope.launch { server.onPresentationFreezeToggle.emit(Unit) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/play-pause */
                post("/api/presentation-remote/play-pause") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    scope.launch { server.onPresentationPlayPause.emit(Unit) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/loop — toggle slide looping */
                post("/api/presentation-remote/loop") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    scope.launch { server.onPresentationLoopToggle.emit(Unit) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/go-live — send presentation to presenter screen */
                post("/api/presentation-remote/go-live") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    scope.launch { server.onPresentationGoLive.emit(Unit) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /** POST /api/presentation-remote/upload — base64 file upload from remote page */
                post("/api/presentation-remote/upload") {
                    if (!server.checkPresentationRemoteAuth(call)) return@post
                    server.handlePresentationFileUpload(call)
                }

                // ── Picture endpoints ─────────────────────────────────────────

                /**
                 * GET /api/pictures
                 * Returns the currently loaded picture folder metadata with per-image thumbnail URLs.
                 */
}

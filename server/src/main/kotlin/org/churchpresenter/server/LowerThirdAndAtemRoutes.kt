package org.churchpresenter.server

import org.churchpresenter.lowerthird.LowerThirdSequencer
import org.churchpresenter.lowerthird.render.LottieRenderCache
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json


/**
 * Routes for lower-third triggers and the ATEM upstream/downstream key.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing. Nearly all of this group's work is delegated back to the server's ATEM helpers,
 * so it takes [server] and little else.
 */
internal fun Route.lowerThirdAndAtemRoutes(
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
) {
    lowerThirdRoutes(server, json)
    atemMediaPoolRoutes(server, scope)
    atemKeyRoutes(server)
}

private fun Route.lowerThirdRoutes(
    server: CompanionServer,
    json: Json,
) {
                get("/api/lowerthirds") {
                    if (!server.checkApiKey(call)) return@get
                    val items = server.atem.lowerThirdFiles().map { f ->
                        val dur = try {
                            LottieRenderCache.lottieDurationMs(f.readText()) ?: 0L
                        } catch (_: Exception) { 0L }
                        val nameJson = json.encodeToString(
                            kotlinx.serialization.serializer<String>(),
                            f.nameWithoutExtension
                        )
                        """{"name":$nameJson,"durationMs":$dur}"""
                    }
                    call.respondText("[${items.joinToString(",")}]", ContentType.Application.Json)
                }

                /**
                 * GET /api/lowerthirds/{name}/json — returns the raw Lottie JSON for a preset by
                 * name, so an InstanceLink follower can play the exact same animation via
                 * PresenterManager.setLottieContent() instead of only switching presenting mode.
                 * Reuses the same by-name file lookup as the run/show/hide endpoints above.
                 */
                get("/api/lowerthirds/{name}/json") {
                    if (!server.checkApiKey(call)) return@get
                    val rawName = call.parameters["name"] ?: ""
                    val file = server.atem.lowerThirdFiles().firstOrNull {
                        it.nameWithoutExtension.equals(rawName, ignoreCase = true)
                    }
                    if (file == null) {
                        server.logRest(
                            "/api/lowerthirds/{name}/json",
                            HttpStatusCode.NotFound.value,
                            "lower_third_not_found"
                        )
                        call.respond(HttpStatusCode.NotFound, """{"error":"lower third not found"}""")
                        return@get
                    }
                    val ltJson = try { file.readText() } catch (_: Exception) {
                        server.logRest(
                            "/api/lowerthirds/{name}/json",
                            HttpStatusCode.InternalServerError.value,
                            "could_not_read_lottie_file"
                        )
                        call.respond(HttpStatusCode.InternalServerError, """{"error":"could not read lottie file"}""")
                        return@get
                    }
                    server.logRest("/api/lowerthirds/{name}/json", HttpStatusCode.OK.value)
                    call.respondText(ltJson, ContentType.Application.Json)
                }

                post("/api/lowerthirds/{name}/run") {
                    if (!server.checkApiKey(call)) return@post
                    server.atem.handleLowerThirdTrigger(call, autoEnd = true)
                }

                post("/api/lowerthirds/{name}/show") {
                    if (!server.checkApiKey(call)) return@post
                    server.atem.handleLowerThirdTrigger(call, autoEnd = false)
                }

                post("/api/lowerthirds/hide") {
                    if (!server.checkApiKey(call)) return@post
                    LowerThirdSequencer.stop()
                    call.respondText("""{"status":"stopped"}""", ContentType.Application.Json)
                }

                // ── ATEM Media Upload Endpoints ────────────────────────────────────

                // POST /api/atem/still/{name}?slot=N&me=E&key=M
                // Renders the named lower third as a single still frame and uploads it
                // to ATEM still slot N (1-based; defaults to atemSettings.defaultStillSlot).
                // If ?key=M (M > 0) is provided, turns upstream key M on M/E E on after upload.
                // Responds immediately; upload runs in background.
}

private fun Route.atemMediaPoolRoutes(
    server: CompanionServer,
    scope: CoroutineScope,
) {
                post("/api/atem/still/{name}") {
                    handleAtemStillUpload(call, server, scope)
                }

                // POST /api/atem/clip/{name}?slot=N&me=E&key=M
                // Renders the named lower third as a full animated clip and uploads it
                // to ATEM clip slot N (1-based; defaults to atemSettings.defaultClipSlot).
                // If ?key=M (M > 0) is provided, turns upstream key M on M/E E on after upload,
                // then off after the clip duration. Responds immediately; upload runs in background.
    atemClipRoutes(server, scope)
}

private fun Route.atemClipRoutes(
    server: CompanionServer,
    scope: CoroutineScope,
) {
                post("/api/atem/clip/{name}") {
                    if (!server.checkApiKey(call)) return@post
                    val name = call.parameters["name"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"name required"}""")
                        return@post
                    }
                    val file = server.atem.lowerThirdFiles().firstOrNull {
                        it.nameWithoutExtension.equals(name, ignoreCase = true)
                    }
                    if (file == null) {
                        call.respond(HttpStatusCode.NotFound, """{"error":"lower third not found"}""")
                        return@post
                    }
                    val atem = server.atem._atemSettings
                    if (atem == null || atem.host.isBlank()) {
                        call.respond(HttpStatusCode.ServiceUnavailable, """{"error":"ATEM not configured"}""")
                        return@post
                    }
                    val slotParam = call.request.queryParameters["slot"]?.toIntOrNull()
                    val slot = if (slotParam != null) slotParam - 1 else atem.defaultClipSlot
                    val key = atemKeyTarget(call, server, atem)
                    if (key.on) server.atem.validateKeyTarget(atem, key.useDsk, key.mixEffect, key.keyer)?.let {
                        call.respond(HttpStatusCode.BadRequest, """{"error":${server.atem.jsonStr(it)}}""")
                        return@post
                    }
                    val lottieJson = file.readText()
                    val fps = atem.clipFps
                    val frameCount = LottieRenderCache.clipFrameCount(lottieJson, fps) ?: 1
                    // Capacity pre-flight (mirrors the Lower Third UI): block a clip that can't
                    // fit the slot before responding "uploading", so the caller gets a real error.
                    val clipCapacity = atem.detectedClipMaxFrames.getOrNull(slot)
                    if (clipCapacity != null && frameCount > clipCapacity) {
                        val secs = String.format(java.util.Locale.US, "%.1f", clipCapacity / fps)
                        val message = "Clip is $frameCount frames but slot ${slot + 1} holds at most " +
                            "$clipCapacity frames (≈$secs s); use a shorter clip or lower fps"
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            """{"error":${server.atem.jsonStr(message)}}"""
                        )
                        return@post
                    }
                    server.atem.trackUpload(
                        scope.launch {
                            uploadClipFrames(AtemClip(file, name, lottieJson, fps, frameCount), atem, slot, key)
                        }
                    )
                    val keyInfoClip = when {
                        !key.on -> ""
                        key.useDsk -> ""","dsk":${key.keyer + 1}"""
                        else -> ""","me":${key.mixEffect + 1},"key":${key.keyer + 1}"""
                    }
                    call.respondText(
                        """{"status":"uploading","type":"clip","name":${server.atem.jsonStr(name)},"slot":""" +
                            """${slot + 1}$keyInfoClip}""",
                        ContentType.Application.Json
                    )
                }

                // POST /api/atem/key/on?me=E&key=M  — turn upstream key M on M/E E on air (standalone)
}


private fun Route.atemKeyRoutes(
    server: CompanionServer,
) {
                post("/api/atem/key/on") {
                    if (!server.checkApiKey(call)) return@post
                    server.atem.handleKeyToggle(call, onAir = true)
                }

                // POST /api/atem/key/off?me=E&key=M  — turn upstream key M on M/E E off air (standalone)
                post("/api/atem/key/off") {
                    if (!server.checkApiKey(call)) return@post
                    server.atem.handleKeyToggle(call, onAir = false)
                }

                // ── Browser Source Endpoints (OBS/vMix overlay) ────────────────────
}

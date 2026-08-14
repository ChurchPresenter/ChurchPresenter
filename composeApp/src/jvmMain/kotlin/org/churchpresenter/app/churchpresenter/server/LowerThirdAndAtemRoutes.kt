package org.churchpresenter.app.churchpresenter.server

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.utils.CrashReporter

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
                get("/api/lowerthirds") {
                    if (!server.checkApiKey(call)) return@get
                    val items = server.atem.lowerThirdFiles().map { f ->
                        val dur = try { LottieRenderCache.lottieDurationMs(f.readText()) ?: 0L } catch (_: Exception) { 0L }
                        val nameJson = json.encodeToString(kotlinx.serialization.serializer<String>(), f.nameWithoutExtension)
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
                    val file = server.atem.lowerThirdFiles().firstOrNull { it.nameWithoutExtension.equals(rawName, ignoreCase = true) }
                    if (file == null) {
                        server.logRest("/api/lowerthirds/{name}/json", 404, "lower_third_not_found")
                        call.respond(HttpStatusCode.NotFound, """{"error":"lower third not found"}""")
                        return@get
                    }
                    val ltJson = try { file.readText() } catch (_: Exception) {
                        server.logRest("/api/lowerthirds/{name}/json", 500, "could_not_read_lottie_file")
                        call.respond(HttpStatusCode.InternalServerError, """{"error":"could not read lottie file"}""")
                        return@get
                    }
                    server.logRest("/api/lowerthirds/{name}/json", 200)
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
                post("/api/atem/still/{name}") {
                    if (!server.checkApiKey(call)) return@post
                    val name = call.parameters["name"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"name required"}""")
                        return@post
                    }
                    val file = server.atem.lowerThirdFiles().firstOrNull { it.nameWithoutExtension.equals(name, ignoreCase = true) }
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
                    val slot = if (slotParam != null) slotParam - 1 else atem.defaultStillSlot
                    // Optional key-on after upload: present key>0 ⇒ key it; absent/0 ⇒ upload only
                    val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
                    val meParam = call.request.queryParameters["me"]?.toIntOrNull()
                    val keyOn = keyParam != null && keyParam > 0
                    val useDsk = server.atem.resolveUseDsk(call, atem)
                    val mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect)
                    val keyer = if (keyParam != null && keyParam > 0) keyParam - 1
                        else if (useDsk) atem.dskIndex else atem.keyIndex
                    if (keyOn) server.atem.validateKeyTarget(atem, useDsk, mixEffect, keyer)?.let {
                        call.respond(HttpStatusCode.BadRequest, """{"error":${server.atem.jsonStr(it)}}""")
                        return@post
                    }
                    scope.launch {
                        val uploadId = AtemUploadStatus.begin(file.nameWithoutExtension, clip = false, slot = slot + 1)
                        try {
                            val lottieJson = file.readText()
                            val variant = LottieRenderCache.atemVariant(lottieJson, atem, clip = false)
                            val cached = LottieRenderCache.prepare(lottieJson, variant).await()
                            AtemConnectionManager.use(atem.host, atem.port, needsState = true) { client ->
                                LottieRenderCache.Reader(cached).use { reader ->
                                    client.uploadStillEncoded(
                                        slot, reader.nextAtemFrame(atem.renderWidth, atem.renderHeight),
                                        file.nameWithoutExtension
                                    ) { p ->
                                        AtemUploadStatus.progress(uploadId, p)
                                    }
                                }
                                if (keyOn) client.setKeyOnAir(useDsk, mixEffect, keyer, true)
                            }
                            AtemUploadStatus.complete(uploadId)
                            kotlinx.coroutines.delay(800)
                            AtemUploadStatus.clear(uploadId)
                        } catch (e: Exception) {
                            System.err.println("[CompanionServer] ATEM still upload failed for '$name': ${e.message}")
                            CrashReporter.reportWarning(
                                "ATEM still upload failed: $name",
                                throwable = e,
                                tags = mapOf("subsystem" to "atem")
                            )
                            AtemUploadStatus.fail(uploadId, e.message)
                        }
                    }
                    val keyInfo = when {
                        !keyOn -> ""
                        useDsk -> ""","dsk":${keyer + 1}"""
                        else -> ""","me":${mixEffect + 1},"key":${keyer + 1}"""
                    }
                    call.respondText(
                        """{"status":"uploading","type":"still","name":${server.atem.jsonStr(name)},"slot":${slot + 1}$keyInfo}""",
                        ContentType.Application.Json
                    )
                }

                // POST /api/atem/clip/{name}?slot=N&me=E&key=M
                // Renders the named lower third as a full animated clip and uploads it
                // to ATEM clip slot N (1-based; defaults to atemSettings.defaultClipSlot).
                // If ?key=M (M > 0) is provided, turns upstream key M on M/E E on after upload,
                // then off after the clip duration. Responds immediately; upload runs in background.
                post("/api/atem/clip/{name}") {
                    if (!server.checkApiKey(call)) return@post
                    val name = call.parameters["name"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"name required"}""")
                        return@post
                    }
                    val file = server.atem.lowerThirdFiles().firstOrNull { it.nameWithoutExtension.equals(name, ignoreCase = true) }
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
                    val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
                    val meParam = call.request.queryParameters["me"]?.toIntOrNull()
                    val keyOn = keyParam != null && keyParam > 0
                    val useDsk = server.atem.resolveUseDsk(call, atem)
                    val mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect)
                    val keyer = if (keyParam != null && keyParam > 0) keyParam - 1
                        else if (useDsk) atem.dskIndex else atem.keyIndex
                    if (keyOn) server.atem.validateKeyTarget(atem, useDsk, mixEffect, keyer)?.let {
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
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            """{"error":${server.atem.jsonStr("Clip is $frameCount frames but slot ${slot + 1} holds at most $clipCapacity frames (≈$secs s); use a shorter clip or lower fps")}}"""
                        )
                        return@post
                    }
                    scope.launch {
                        val uploadId = AtemUploadStatus.begin(file.nameWithoutExtension, clip = true, slot = slot + 1)
                        try {
                            val variant = LottieRenderCache.atemVariant(lottieJson, atem, clip = true, fps = fps)
                            val cached = LottieRenderCache.prepare(lottieJson, variant).await()
                            AtemConnectionManager.use(atem.host, atem.port, needsState = true) { client ->
                                LottieRenderCache.Reader(cached).use { reader ->
                                    client.uploadClipEncoded(slot, reader.frameCount, file.nameWithoutExtension,
                                        nextFrame = { reader.nextAtemFrame(atem.renderWidth, atem.renderHeight) }
                                    ) { p -> AtemUploadStatus.progress(uploadId, p) }
                                }
                                // Wait for the ATEM to finish ingesting the clip before keying, so
                                // the key never fires over a half-processed clip. Best-effort: key
                                // anyway if the device never reports ready within the timeout.
                                AtemUploadStatus.startProcessing(uploadId)
                                client.awaitClipReady(slot, frameCount) { p -> AtemUploadStatus.progress(uploadId, p) }
                                if (keyOn) client.setKeyOnAir(useDsk, mixEffect, keyer, true)
                            }
                            AtemUploadStatus.complete(uploadId)
                            kotlinx.coroutines.delay(800)
                            AtemUploadStatus.clear(uploadId)
                            // Wait for the clip to finish playing, then turn the key off automatically.
                            // Mutex is released between the two use() calls so other operations can proceed.
                            if (keyOn) {
                                val clipDurationMs = (frameCount.toLong() * 1000L) / fps.toLong()
                                kotlinx.coroutines.delay(clipDurationMs)
                                AtemConnectionManager.use(atem.host, atem.port, needsState = false) { client ->
                                    client.setKeyOnAir(useDsk, mixEffect, keyer, false)
                                }
                            }
                        } catch (e: Exception) {
                            System.err.println("[CompanionServer] ATEM clip upload failed for '$name': ${e.message}")
                            CrashReporter.reportWarning(
                                "ATEM clip upload failed: $name",
                                throwable = e,
                                tags = mapOf("subsystem" to "atem")
                            )
                            AtemUploadStatus.fail(uploadId, e.message)
                        }
                    }
                    val keyInfoClip = when {
                        !keyOn -> ""
                        useDsk -> ""","dsk":${keyer + 1}"""
                        else -> ""","me":${mixEffect + 1},"key":${keyer + 1}"""
                    }
                    call.respondText(
                        """{"status":"uploading","type":"clip","name":${server.atem.jsonStr(name)},"slot":${slot + 1}$keyInfoClip}""",
                        ContentType.Application.Json
                    )
                }

                // POST /api/atem/key/on?me=E&key=M  — turn upstream key M on M/E E on air (standalone)
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

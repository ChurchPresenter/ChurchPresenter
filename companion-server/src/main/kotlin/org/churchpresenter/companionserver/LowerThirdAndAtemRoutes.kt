package org.churchpresenter.companionserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.atem.AtemKey
import org.churchpresenter.atem.AtemUploadStatus
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.AtemSettings

private const val KEY_SETTLE_MS = 800L
private const val MILLIS_PER_SECOND = 1000L

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
                    if (!server.checkClientAllowed(call)) return@post
                    server.atem.handleLowerThirdTrigger(call, autoEnd = true)
                }

                post("/api/lowerthirds/{name}/show") {
                    if (!server.checkApiKey(call)) return@post
                    if (!server.checkClientAllowed(call)) return@post
                    server.atem.handleLowerThirdTrigger(call, autoEnd = false)
                }

                post("/api/lowerthirds/hide") {
                    if (!server.checkApiKey(call)) return@post
                    if (!server.checkClientAllowed(call)) return@post
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
                    if (!server.checkClientAllowed(call)) return@post
                    if (!server.checkClientAllowed(call)) return@post
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
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            """{"error":${server.atem.jsonStr(
                                "Clip is $frameCount frames but slot ${slot + 1} holds at most " +
                                    "$clipCapacity frames (≈$secs s); use a shorter clip or lower fps"
                            )}}"""
                        )
                        return@post
                    }
                    server.atem.trackUpload(
                        scope.launch {
                            uploadClipFrames(
                                file, atem, AtemDestination(slot, key, name),
                                AtemClipSpec(lottieJson, fps, frameCount), server.host.lottieRenderer,
                            )
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
                    if (!server.checkClientAllowed(call)) return@post
                    server.atem.handleKeyToggle(call, onAir = true)
                }

                // POST /api/atem/key/off?me=E&key=M  — turn upstream key M on M/E E off air (standalone)
                post("/api/atem/key/off") {
                    if (!server.checkApiKey(call)) return@post
                    if (!server.checkClientAllowed(call)) return@post
                    server.atem.handleKeyToggle(call, onAir = false)
                }

                // ── Browser Source Endpoints (OBS/vMix overlay) ────────────────────
}



private suspend fun handleAtemStillUpload(
    call: ApplicationCall,
    server: CompanionServer,
    scope: CoroutineScope,
) {
                if (!server.checkApiKey(call)) return
                val name = call.parameters["name"].orEmpty()
                val file = namedLowerThirdOrRespond(call, server, name) ?: return
                val atem = configuredAtemOrRespond(call, server) ?: return
                val slotParam = call.request.queryParameters["slot"]?.toIntOrNull()
                val slot = if (slotParam != null) slotParam - 1 else atem.defaultStillSlot
                // Optional key-on after upload: present key>0 ⇒ key it; absent/0 ⇒ upload only
                val key = atemKeyTarget(call, server, atem)
                if (key.on) server.atem.validateKeyTarget(atem, key.useDsk, key.mixEffect, key.keyer)?.let {
                    call.respond(HttpStatusCode.BadRequest, """{"error":${server.atem.jsonStr(it)}}""")
                    return
                }
                // Tracked, not dropped: the transfer outlives this response, so something has to
                // be able to stop it -- see AtemBridge.cancelUpload.
                server.atem.trackUpload(
                    scope.launch {
                        uploadStillFrame(file, atem, AtemDestination(slot, key, name), server.host.lottieRenderer)
                    }
                )
                val keyInfo = when {
                    !key.on -> ""
                    key.useDsk -> ""","dsk":${key.keyer + 1}"""
                    else -> ""","me":${key.mixEffect + 1},"key":${key.keyer + 1}"""
                }
                call.respondText(
                    """{"status":"uploading","type":"still","name":${server.atem.jsonStr(name)},"slot":${slot + 1}""" +
                        """$keyInfo}""",
                    ContentType.Application.Json
                )
}

/** Renders the named lower third to a single ATEM still and uploads it into [slot]. */
private suspend fun uploadStillFrame(
    file: java.io.File,
    atem: AtemSettings,
    to: AtemDestination,
    renderer: LottieFrameRenderer,
) {
    val (slot, key, name) = to
    val uploadId = AtemUploadStatus.begin(file.nameWithoutExtension, clip = false, slot = slot + 1)
    try {
        val lottieJson = file.readText()
        val variant = LottieRenderCache.atemVariant(lottieJson, atem, clip = false)
        val cached = LottieRenderCache.prepare(lottieJson, variant, renderer).await()
        AtemConnectionManager.use(atem.host, atem.port, needsState = true) { client ->
            LottieRenderCache.Reader(cached).use { reader ->
                client.uploadStillEncoded(
                    slot, reader.nextAtemFrame(atem.renderWidth, atem.renderHeight),
                    file.nameWithoutExtension
                ) { p ->
                    AtemUploadStatus.progress(uploadId, p)
                }
            }
            if (key.on) client.setKeyOnAir(AtemKey(key.useDsk, key.mixEffect, key.keyer), true)
        }
        AtemUploadStatus.complete(uploadId)
        delay(KEY_SETTLE_MS)
        AtemUploadStatus.clear(uploadId)
    } catch (e: CancellationException) {
        // Cancelled deliberately -- the ATEM was repointed, or the server is going down. Not a
        // failure, so leave no error banner behind, and let it propagate: swallowing it here would
        // report a cancelled upload as a broken one.
        AtemUploadStatus.clear(uploadId)
        throw e
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

/** Which keyer an upload should put on air afterwards, resolved from the query and settings. */
private data class AtemKeyTarget(val on: Boolean, val useDsk: Boolean, val mixEffect: Int, val keyer: Int)

/**
 * Where one lower third is going: which media-pool [slot], what to name it there, and whether to
 * cut a key over it once the transfer lands.
 *
 * The three always travel together — the route reads them from one request and both upload paths
 * pass all three straight through — so they are one parameter rather than three.
 */
private data class AtemDestination(val slot: Int, val key: AtemKeyTarget, val name: String)

private fun atemKeyTarget(call: ApplicationCall, server: CompanionServer, atem: AtemSettings): AtemKeyTarget {
    val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
    val meParam = call.request.queryParameters["me"]?.toIntOrNull()
    val useDsk = server.atem.resolveUseDsk(call.request.queryParameters["keytype"], atem)
    return AtemKeyTarget(
        on = keyParam != null && keyParam > 0,
        useDsk = useDsk,
        mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect),
        keyer = if (keyParam != null && keyParam > 0) keyParam - 1
            else if (useDsk) atem.dskIndex else atem.keyIndex,
    )
}

/** The animation being uploaded, at the frame rate and length the switcher slot was checked against. */
private data class AtemClipSpec(val lottieJson: String, val fps: Double, val frameCount: Int)

/** Renders the named lower third to an ATEM clip, uploads it, and keys it if asked. */
private suspend fun uploadClipFrames(
    file: java.io.File,
    atem: AtemSettings,
    to: AtemDestination,
    clip: AtemClipSpec,
    renderer: LottieFrameRenderer,
) {
    val (slot, key, name) = to
    val (lottieJson, fps, frameCount) = clip
    val uploadId = AtemUploadStatus.begin(file.nameWithoutExtension, clip = true, slot = slot + 1)
    try {
        val variant = LottieRenderCache.atemVariant(lottieJson, atem, clip = true, fps = fps)
        val cached = LottieRenderCache.prepare(lottieJson, variant, renderer).await()
        AtemConnectionManager.use(atem.host, atem.port, needsState = true) { client ->
            LottieRenderCache.Reader(cached).use { reader ->
                client.uploadClipEncoded(slot, reader.frameCount, file.nameWithoutExtension,
                    nextFrame = { reader.nextAtemFrame(atem.renderWidth, atem.renderHeight) }
                ) { p -> AtemUploadStatus.progress(uploadId, p) }
            }
            // Wait for the ATEM to finish ingesting the clip before keying, so the key never fires
            // over a half-processed clip. Best-effort: key anyway if the device never reports ready
            // within the timeout.
            AtemUploadStatus.startProcessing(uploadId)
            client.awaitClipReady(slot, frameCount) { p -> AtemUploadStatus.progress(uploadId, p) }
            if (key.on) client.setKeyOnAir(AtemKey(key.useDsk, key.mixEffect, key.keyer), true)
        }
        AtemUploadStatus.complete(uploadId)
        delay(KEY_SETTLE_MS)
        AtemUploadStatus.clear(uploadId)
        // Wait for the clip to finish playing, then turn the key off automatically. The mutex is
        // released between the two use() calls so other operations can proceed.
        if (key.on) {
            delay(if (fps > 0.0) ((frameCount.toDouble() * MILLIS_PER_SECOND) / fps).toLong() else 0L)
            AtemConnectionManager.use(atem.host, atem.port, needsState = false) { client ->
                client.setKeyOnAir(AtemKey(key.useDsk, key.mixEffect, key.keyer), false)
            }
        }
    } catch (e: CancellationException) {
        // Cancelled deliberately -- the ATEM was repointed, or the server is going down. Not a
        // failure, so leave no error banner behind, and let it propagate: swallowing it here would
        // report a cancelled upload as a broken one.
        AtemUploadStatus.clear(uploadId)
        throw e
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

/** The lower third named in the request, or null once the failure has been responded with. */
private suspend fun namedLowerThirdOrRespond(
    call: ApplicationCall,
    server: CompanionServer,
    name: String,
): java.io.File? {
    if (name.isBlank()) {
        call.respond(HttpStatusCode.BadRequest, """{"error":"name required"}""")
        return null
    }
    val file = server.atem.lowerThirdFiles()
        .firstOrNull { it.nameWithoutExtension.equals(name, ignoreCase = true) }
    if (file == null) {
        call.respond(HttpStatusCode.NotFound, """{"error":"lower third not found"}""")
        return null
    }
    return file
}

/** The ATEM settings, or null once "not configured" has been responded with. */
private suspend fun configuredAtemOrRespond(call: ApplicationCall, server: CompanionServer): AtemSettings? {
    val atem = server.atem._atemSettings
    if (atem == null || atem.host.isBlank()) {
        call.respond(HttpStatusCode.ServiceUnavailable, """{"error":"ATEM not configured"}""")
        return null
    }
    return atem
}

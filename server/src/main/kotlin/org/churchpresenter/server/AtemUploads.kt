package org.churchpresenter.server

import org.churchpresenter.lowerthird.render.LottieRenderCache
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
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
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.settings.AtemSettings
import java.io.IOException

private const val KEY_SETTLE_MS = 800L
private const val MILLIS_PER_SECOND = 1000L

internal suspend fun handleAtemStillUpload(
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
                server.atem.trackUpload(scope.launch { uploadStillFrame(file, atem, slot, key, name) })
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
    slot: Int,
    key: AtemKeyTarget,
    name: String,
) {
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
    } catch (e: IOException) {
        // The ATEM link (AtemProtocolException is one) or the render cache.
        atemUploadFailed("still", name, uploadId, e)
    } catch (e: IllegalArgumentException) {
        // A lower third whose JSON will not parse.
        atemUploadFailed("still", name, uploadId, e)
    } catch (e: IllegalStateException) {
        atemUploadFailed("still", name, uploadId, e)
    }
}

/** Which keyer an upload should put on air afterwards, resolved from the query and settings. */
internal data class AtemKeyTarget(val on: Boolean, val useDsk: Boolean, val mixEffect: Int, val keyer: Int)

internal fun atemKeyTarget(call: ApplicationCall, server: CompanionServer, atem: AtemSettings): AtemKeyTarget {
    val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
    val meParam = call.request.queryParameters["me"]?.toIntOrNull()
    val useDsk = server.atem.resolveUseDsk(call, atem)
    return AtemKeyTarget(
        on = keyParam != null && keyParam > 0,
        useDsk = useDsk,
        mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect),
        keyer = if (keyParam != null && keyParam > 0) keyParam - 1
            else if (useDsk) atem.dskIndex else atem.keyIndex,
    )
}

/** A lower third about to become an ATEM clip: its file, the name it was asked for by, and its timing. */
internal data class AtemClip(
    val file: java.io.File,
    val name: String,
    val lottieJson: String,
    val fps: Double,
    val frameCount: Int,
)

/** Renders the named lower third to an ATEM clip, uploads it, and keys it if asked. */
internal suspend fun uploadClipFrames(clip: AtemClip, atem: AtemSettings, slot: Int, key: AtemKeyTarget) {
    val uploadId = AtemUploadStatus.begin(clip.file.nameWithoutExtension, clip = true, slot = slot + 1)
    try {
        val variant = LottieRenderCache.atemVariant(clip.lottieJson, atem, clip = true, fps = clip.fps)
        val cached = LottieRenderCache.prepare(clip.lottieJson, variant).await()
        AtemConnectionManager.use(atem.host, atem.port, needsState = true) { client ->
            LottieRenderCache.Reader(cached).use { reader ->
                client.uploadClipEncoded(slot, reader.frameCount, clip.file.nameWithoutExtension,
                    nextFrame = { reader.nextAtemFrame(atem.renderWidth, atem.renderHeight) }
                ) { p -> AtemUploadStatus.progress(uploadId, p) }
            }
            // Wait for the ATEM to finish ingesting the clip before keying, so the key never fires
            // over a half-processed clip. Best-effort: key anyway if the device never reports ready
            // within the timeout.
            AtemUploadStatus.startProcessing(uploadId)
            client.awaitClipReady(slot, clip.frameCount) { p -> AtemUploadStatus.progress(uploadId, p) }
            if (key.on) client.setKeyOnAir(AtemKey(key.useDsk, key.mixEffect, key.keyer), true)
        }
        AtemUploadStatus.complete(uploadId)
        delay(KEY_SETTLE_MS)
        AtemUploadStatus.clear(uploadId)
        // Wait for the clip to finish playing, then turn the key off automatically. The mutex is
        // released between the two use() calls so other operations can proceed.
        if (key.on) {
            delay(if (clip.fps > 0.0) ((clip.frameCount.toDouble() * MILLIS_PER_SECOND) / clip.fps).toLong() else 0L)
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
    } catch (e: IOException) {
        // The ATEM link (AtemProtocolException is one) or the render cache.
        atemUploadFailed("clip", clip.name, uploadId, e)
    } catch (e: IllegalArgumentException) {
        // A lower third whose JSON will not parse.
        atemUploadFailed("clip", clip.name, uploadId, e)
    } catch (e: IllegalStateException) {
        atemUploadFailed("clip", clip.name, uploadId, e)
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

private fun atemUploadFailed(kind: String, name: String, uploadId: Long, e: Exception) {
    Log.warn("CompanionServer", "ATEM $kind upload failed for '$name': ${e.message}")
    CrashReporter.reportWarning(
        "ATEM $kind upload failed: $name",
        throwable = e,
        tags = mapOf("subsystem" to "atem")
    )
    AtemUploadStatus.fail(uploadId, e.message)
}

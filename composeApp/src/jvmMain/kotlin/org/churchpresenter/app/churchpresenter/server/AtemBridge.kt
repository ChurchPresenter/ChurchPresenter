package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.websocket.readText
import java.io.File
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.app.churchpresenter.viewmodel.isLottieFile
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.atem.AtemConnectionManager
import org.churchpresenter.atem.AtemKey

/**
 * Everything the companion API does with ATEM hardware and the lower-third folder: which files are
 * available, running a lower third, and driving the upstream/downstream keyer.
 *
 * Owns the ATEM configuration rather than leaving it as loose fields on `CompanionServer`. Field
 * names are unchanged from where this code used to live, so the bodies moved verbatim -- these
 * handlers are full of raw-string JSON replies whose content depends on their own indentation.
 */
internal class AtemBridge(private val json: Json) {

    @Volatile internal var _atemSettings: AtemSettings? = null
    @Volatile private var _lowerThirdFolder: String = ""

    /** Applies new ATEM settings, dropping the pooled connection when the endpoint moved. */
    fun updateConfig(atem: AtemSettings, lowerThirdFolder: String) {
        val prev = _atemSettings
        if (prev == null || prev.host != atem.host || prev.port != atem.port) {
            AtemConnectionManager.invalidate()
        }
        _atemSettings = atem
        _lowerThirdFolder = lowerThirdFolder
    }

    /** Lottie files in the configured lower-third folder. */
    internal fun lowerThirdFiles(): List<File> =
        File(_lowerThirdFolder).takeIf { _lowerThirdFolder.isNotEmpty() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
            ?.sortedBy { it.nameWithoutExtension.lowercase() } ?: emptyList()

    internal fun jsonStr(s: String): String =
        json.encodeToString(kotlinx.serialization.serializer<String>(), s)

    private data class TriggeredLowerThird(val file: File, val json: String, val durationMs: Long)

    /** The lower third named in the request, or null once the failure has been responded with. */
    private suspend fun loadTriggeredLowerThird(call: ApplicationCall, rawName: String): TriggeredLowerThird? {
        val file = lowerThirdFiles().firstOrNull { it.nameWithoutExtension.equals(rawName, ignoreCase = true) }
        if (file == null) {
            call.respond(HttpStatusCode.NotFound, """{"error":"lower third not found"}""")
            return null
        }
        val ltJson = runCatching { file.readText() }.getOrNull()
        val durationMs = ltJson?.let { LottieRenderCache.lottieDurationMs(it) }
        return when {
            ltJson == null -> {
                call.respond(HttpStatusCode.InternalServerError, """{"error":"could not read lottie file"}""")
                null
            }
            durationMs == null -> {
                call.respond(HttpStatusCode.UnprocessableEntity, """{"error":"lottie has no timing information"}""")
                null
            }
            else -> TriggeredLowerThird(file, ltJson, durationMs)
        }
    }

    /** Shared body of the run/show endpoints. */
    internal suspend fun handleLowerThirdTrigger(
        call: ApplicationCall,
        autoEnd: Boolean
    ) {
        val loaded = loadTriggeredLowerThird(call, call.parameters["name"] ?: "") ?: return
        val file = loaded.file
        val ltJson = loaded.json
        val durationMs = loaded.durationMs
        val atem = _atemSettings ?: AtemSettings()

        // Key target: USK (M/E + keyer) or DSK (?keytype / setting) from settings;
        // ?me=N&key=M (1-based) override; ?key=0 skips. For DSK ?key overrides the DSK index.
        val useDsk = resolveUseDsk(call, atem)
        val meParam = call.request.queryParameters["me"]?.toIntOrNull()
        val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
        val mixEffect: Int?
        val keyer: Int?
        if (keyParam == 0) {
            mixEffect = null; keyer = null
        } else {
            mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect)
            keyer = if (keyParam != null) keyParam - 1
                else if (useDsk) atem.dskIndex else atem.keyIndex
            validateKeyTarget(atem, useDsk, mixEffect, keyer)?.let {
                call.respond(HttpStatusCode.BadRequest, """{"error":${jsonStr(it)}}""")
                return
            }
        }

        val pause = call.request.queryParameters["pause"]?.toBooleanStrictOrNull() ?: false
        val pauseDurationMs = call.request.queryParameters["pauseDurationMs"]?.toLongOrNull() ?: 2000L

        val keyError = LowerThirdSequencer.run(
            name = file.nameWithoutExtension,
            json = ltJson,
            durationMs = durationMs,
            pauseAtFrame = pause,
            pauseDurationMs = pauseDurationMs,
            mixEffect = mixEffect,
            keyer = keyer,
            atem = atem,
            useDownstreamKey = useDsk,
            autoEnd = autoEnd
        )
        val totalMs = atem.keyPreRollMs + durationMs +
            (if (pause) pauseDurationMs else 0L) + atem.keyPostRollMs
        call.respondText(
            """{"status":"started","name":${jsonStr(file.nameWithoutExtension)},"durationMs":$durationMs,""" +
                """"totalMs":${if (autoEnd) totalMs else -1},"keyError":${keyError?.let { jsonStr(it) } ?: "null"}}""",
            ContentType.Application.Json
        )
    }

    /**
     * Standalone upstream-key on/off (POST /api/atem/key/on|off). Reuses the shared
     * keepalive connection when free; falls back to a short-lived connection when an
     * upload holds it, so a key cut never waits behind an upload. Synchronous 200/502.
     */
    internal suspend fun handleKeyToggle(call: ApplicationCall, onAir: Boolean) {
        val atem = _atemSettings
        if (atem == null || atem.host.isBlank()) {
            call.respond(HttpStatusCode.ServiceUnavailable, """{"error":"ATEM not configured"}""")
            return
        }
        val useDsk = resolveUseDsk(call, atem)
        val meParam = call.request.queryParameters["me"]?.toIntOrNull()
        val keyParam = call.request.queryParameters["key"]?.toIntOrNull()
        val mixEffect = if (useDsk) 0 else (if (meParam != null) meParam - 1 else atem.keyMixEffect)
        val keyer = if (keyParam != null) keyParam - 1
            else if (useDsk) atem.dskIndex else atem.keyIndex
        validateKeyTarget(atem, useDsk, mixEffect, keyer)?.let {
            call.respond(HttpStatusCode.BadRequest, """{"error":${jsonStr(it)}}""")
            return
        }
        try {
            val ran = AtemConnectionManager.tryRun(atem.host, atem.port) { client ->
                client.setKeyOnAir(AtemKey(useDsk, mixEffect, keyer), onAir)
            }
            if (!ran) AtemClient.cutKey(atem.host, atem.port, AtemKey(useDsk, mixEffect, keyer), onAir)
            val target = if (useDsk) """"dsk":${keyer + 1}""" else """"me":${mixEffect + 1},"key":${keyer + 1}"""
            call.respondText(
                """{"status":"${if (onAir) "on" else "off"}",$target}""",
                ContentType.Application.Json
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadGateway,
                """{"error":${jsonStr(e.message ?: "ATEM command failed")}}"""
            )
        }
    }

    /**
     * Validate a 0-based key target against the detected topology. Null = OK.
     * For a downstream key [keyer] is the DSK index and [mixEffect] is ignored.
     */
    internal fun validateKeyTarget(atem: AtemSettings, useDsk: Boolean, mixEffect: Int, keyer: Int): String? {
        if (useDsk) {
            val unknownDsk = atem.detectedDownstreamKeyers > 0 &&
                keyer !in 0 until atem.detectedDownstreamKeyers
            return "DSK ${keyer + 1} does not exist (available: 1-${atem.detectedDownstreamKeyers})"
                .takeIf { unknownDsk }
        }
        val keyers = atem.detectedKeyersPerMe.getOrNull(mixEffect)
        return when {
            atem.detectedMixEffects > 0 && mixEffect !in 0 until atem.detectedMixEffects ->
                "M/E ${mixEffect + 1} does not exist (available: 1-${atem.detectedMixEffects})"
            keyers != null && keyers > 0 && keyer !in 0 until keyers ->
                "Key ${keyer + 1} does not exist on M/E ${mixEffect + 1} (available: 1-$keyers)"
            else -> null
        }
    }

    /**
     * Resolves whether a request should drive a downstream key: `?keytype=dsk|usk` (or
     * `downstream|upstream`) overrides; otherwise the persisted [AtemSettings.useDownstreamKey].
     */
    internal fun resolveUseDsk(call: ApplicationCall, atem: AtemSettings): Boolean =
        when (call.request.queryParameters["keytype"]?.lowercase()) {
            "dsk", "downstream" -> true
            "usk", "upstream" -> false
            else -> atem.useDownstreamKey
        }
}

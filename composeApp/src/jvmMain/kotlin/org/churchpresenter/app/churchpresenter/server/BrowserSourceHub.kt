package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.BrowserSourceFrame
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger

/**
 * The OBS/vMix Browser Source outputs: which outputs exist, the frame stream each renderer
 * produces, and the live overlay sessions consuming them.
 *
 * Owns that state instead of leaving it on `CompanionServer`. Member names are unchanged from
 * where this code used to live so the bodies moved verbatim; [_apiKey] is the server's key flow,
 * passed in because an output can demand a key independently of the global setting.
 */
internal class BrowserSourceHub(
    private val scope: CoroutineScope,
    private val _apiKey: MutableStateFlow<String>,
) {

    @Volatile private var _browserSourceOutputs: List<ScreenAssignment> = emptyList()
    internal val _browserSourceFrameFlows = ConcurrentHashMap<Int, SharedFlow<BrowserSourceFrame>>()

    // Live WebSocket sessions per output index, so a renderer replacement can close them —
    // a session holds the flow it captured at connect time, and after re-registration that
    // old flow never emits again while the heartbeat keeps re-sending its stale last frame.
    // Closing forces the overlay page to reconnect (2s backoff) and reseed at the new stream.
    internal val _browserSourceSessions = ConcurrentHashMap<Int, MutableSet<DefaultWebSocketServerSession>>()

    internal fun updateBrowserSourceOutputs(outputs: List<ScreenAssignment>) {
        _browserSourceOutputs = outputs
        InstanceLinkLogger.log(
            InstanceLinkLogSide.PRIMARY,
            "state_updated",
            mapOf("type" to "browser_source_outputs", "count" to outputs.size)
        )
    }

    fun browserSourceOutput(index: Int): ScreenAssignment? = _browserSourceOutputs.getOrNull(index)


    /**
     * Registers (or replaces) the frame delta flow a given output's renderer produces.
     * Replacing an existing flow (renderer restarted, e.g. after a resolution/fps change)
     * closes that output's connected sessions so clients reconnect to the new stream.
     */
    fun registerBrowserSourceFrames(index: Int, frames: SharedFlow<BrowserSourceFrame>) {
        val previous = _browserSourceFrameFlows.put(index, frames)
        if (previous != null && previous !== frames) {
            val stranded = _browserSourceSessions.remove(index) ?: return
            scope.launch {
                stranded.forEach { session ->
                    try {
                        session.close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Renderer restarted"))
                    } catch (_: Exception) {
                        // already gone
                    }
                }
            }
        }
    }

    /** Pure check for the given output's independent Browser Source API-key requirement (separate from [_apiKeyEnabled]) — no response side effects, usable from both HTTP and WebSocket routes. */
    internal fun browserSourceApiKeyValid(call: ApplicationCall, output: ScreenAssignment): Boolean {
        if (!output.browserSourceApiKeyRequired || _apiKey.value.isEmpty()) return true
        val provided = call.request.headers[Constants.HEADER_API_KEY]
            ?: call.request.queryParameters[Constants.QUERY_PARAM_API_KEY]
            ?: ""
        return MessageDigest.isEqual(provided.toByteArray(), _apiKey.value.toByteArray())
    }

    /** Same check as [browserSourceApiKeyValid], but responds 401 on an HTTP route when invalid. */
    internal suspend fun checkBrowserSourceApiKey(call: ApplicationCall, output: ScreenAssignment): Boolean {
        if (browserSourceApiKeyValid(call, output)) return true
        call.respond(HttpStatusCode.Unauthorized, "Invalid API key")
        return false
    }

    internal fun encodeBrowserSourceFrameMessage(frame: BrowserSourceFrame): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(24 + frame.png.size)
        buf.putInt(frame.x)
        buf.putInt(frame.y)
        buf.putInt(frame.rectWidth)
        buf.putInt(frame.rectHeight)
        buf.putInt(frame.fullWidth)
        buf.putInt(frame.fullHeight)
        buf.put(frame.png)
        return buf.array()
    }
}

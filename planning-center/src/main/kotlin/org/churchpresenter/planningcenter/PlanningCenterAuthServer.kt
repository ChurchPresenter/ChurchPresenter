package org.churchpresenter.planningcenter

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.utils.Constants
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder

/**
 * One-shot local HTTP listener that catches the OAuth redirect from Planning Center's consent
 * screen. Bound to a fixed loopback port ([Constants.PLANNING_CENTER_OAUTH_PORT]) since PCO OAuth
 * apps require an exact pre-registered redirect URI, unlike the app's own Companion server, which
 * picks its port. Started fresh for each connect attempt and torn down once the callback lands (or
 * times out), rather than kept running.
 *
 * Served by the JDK's own `HttpServer` rather than Ktor on Netty, and that choice is the fix for
 * #678. Netty writes the last part of a response without flushing it and flushes on a later task,
 * so no hook Ktor offers means "the page has left": `ResponseSent` fired while the bytes were still
 * queued, `stop()` took the event loop away under them, and the browser got a truncated page -- the
 * suite saw it as `ClosedReadChannelException`. The JDK server writes a response synchronously on
 * the handler's own thread, so by the time the handler hands the result over, the page is already
 * with the operating system and nothing done to the server afterwards can cut it short.
 */
object PlanningCenterAuthServer {

    sealed interface CallbackResult {
        data class Success(val code: String) : CallbackResult
        data class Error(val message: String) : CallbackResult
        data object Timeout : CallbackResult
    }

    private const val CALLBACK_TIMEOUT_MS = 300_000L
    private const val CALLBACK_PATH = "/callback"
    private const val HTTP_OK = 200
    private const val PAGE =
        "<html><body>You can close this window and return to ChurchPresenter.</body></html>"

    suspend fun awaitAuthorizationCode(): CallbackResult {
        val deferred = CompletableDeferred<CallbackResult>()
        val server = try {
            HttpServer.create(InetSocketAddress("127.0.0.1", Constants.PLANNING_CENTER_OAUTH_PORT), 0)
        } catch (e: IOException) {
            // Binding 127.0.0.1:47850 is the only thing here that can fail, and it fails as an
            // IOException — a BindException when a stale listener (or a second ChurchPresenter)
            // still holds the port. Reported as an Error so the dialog can say so; anything else
            // is left to propagate rather than silently becoming "could not connect".
            return CallbackResult.Error(e.message ?: "Failed to start local callback server")
        }
        server.createContext(CALLBACK_PATH) { exchange ->
            val result = callbackResult(exchange.requestURI.rawQuery)
            // Answered in full before the result is handed over; see the class comment.
            try {
                exchange.use { respond(it) }
            } catch (_: IOException) {
                // The browser went away mid-page. The code it brought is still good, so it is
                // handed over all the same rather than lost with the page.
            }
            deferred.complete(result)
        }
        server.start()
        try {
            return withTimeoutOrNull(CALLBACK_TIMEOUT_MS) { deferred.await() } ?: CallbackResult.Timeout
        } finally {
            server.stop(0)
        }
    }

    private fun respond(exchange: HttpExchange) {
        val body = PAGE.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(HTTP_OK, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    /** What a callback whose query string is [rawQuery] means: a code, or why there is none. */
    internal fun callbackResult(rawQuery: String?): CallbackResult {
        val params = rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            decode(name) to decode(value)
        }
        val code = params["code"]
        return if (code != null) {
            CallbackResult.Success(code)
        } else {
            CallbackResult.Error(params["error"] ?: "No authorization code returned")
        }
    }

    private fun decode(part: String): String = URLDecoder.decode(part, Charsets.UTF_8)
}

package org.churchpresenter.planningcenter

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.utils.Constants
import java.io.IOException

/**
 * One-shot local HTTP listener that catches the OAuth redirect from Planning Center's consent
 * screen. Bound to a fixed loopback port ([Constants.PLANNING_CENTER_OAUTH_PORT]) since PCO OAuth
 * apps require an exact pre-registered redirect URI, unlike the app's own Companion server, which
 * picks its port. Started fresh for each connect attempt and torn down once the callback lands (or
 * times out), rather than kept running.
 */
object PlanningCenterAuthServer {

    sealed interface CallbackResult {
        data class Success(val code: String) : CallbackResult
        data class Error(val message: String) : CallbackResult
        data object Timeout : CallbackResult
    }

    private const val CALLBACK_TIMEOUT_MS = 300_000L

    /**
     * How long the callback page is given to reach the browser before the listener closes anyway.
     *
     * Not a guess at how slow loopback is — the wait ends on [ResponseSent] firing, and this only
     * caps the case where it never will because the browser went away. Seconds rather than
     * milliseconds so it is never the reason a slow machine sees a truncated page.
     */
    private const val RESPONSE_FLUSH_TIMEOUT_MS = 5_000L

    suspend fun awaitAuthorizationCode(): CallbackResult {
        val deferred = CompletableDeferred<CallbackResult>()
        // Completed once the callback page has actually gone out.
        //
        // `respondText` returns when the body reaches Netty's pipeline, not when it reaches the
        // browser, so completing `deferred` alone let `stop()` below race the final write — the
        // operator granted consent and then got a truncated or blank page, and the suite saw it as
        // `ClosedReadChannelException`. Waiting for this too means the connector is never torn down
        // before the response the user is still reading.
        //
        // Separate from `deferred` rather than replacing it: if the browser disconnects mid-send
        // this never fires, and folding the two together would throw away an authorization code
        // that had already arrived.
        val responseSent = CompletableDeferred<Unit>()

        val server = try {
            embeddedServer(Netty, configure = {
                connector {
                    host = "127.0.0.1"
                    port = Constants.PLANNING_CENTER_OAUTH_PORT
                }
            }) {
                install(createApplicationPlugin("OAuthCallbackSent") {
                    on(ResponseSent) { responseSent.complete(Unit) }
                })
                routing {
                    get("/callback") {
                        val code = call.request.queryParameters["code"]
                        val error = call.request.queryParameters["error"]
                        call.respondText(
                            "<html><body>You can close this window and return to ChurchPresenter.</body></html>",
                            ContentType.Text.Html
                        )
                        deferred.complete(
                            if (code != null) CallbackResult.Success(code)
                            else CallbackResult.Error(error ?: "No authorization code returned")
                        )
                    }
                }
            }.also { it.start(wait = false) }
        } catch (e: IOException) {
            // Binding 127.0.0.1:47850 is the only thing here that can fail, and it fails as an
            // IOException — a BindException when a stale listener (or a second ChurchPresenter)
            // still holds the port. Reported as an Error so the dialog can say so; anything else
            // is left to propagate rather than silently becoming "could not connect".
            return CallbackResult.Error(e.message ?: "Failed to start local callback server")
        }

        val result = withTimeoutOrNull(CALLBACK_TIMEOUT_MS) { deferred.await() } ?: CallbackResult.Timeout
        // Let the page finish going out before the connector is taken away. Bounded, and the code
        // is already in hand: a browser that disappears mid-send never fires [responseSent], and
        // waiting on it unconditionally would trade a truncated page for a hung dialog. Awaiting it
        // *after* the result is captured is what keeps that authorization code.
        if (result !is CallbackResult.Timeout) {
            withTimeoutOrNull(RESPONSE_FLUSH_TIMEOUT_MS) { responseSent.await() }
        }
        server.stop(gracePeriodMillis = 100, timeoutMillis = 1000)
        return result
    }
}

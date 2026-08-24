package org.churchpresenter.planningcenter

import io.ktor.http.ContentType
import io.ktor.server.application.call
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

    suspend fun awaitAuthorizationCode(): CallbackResult {
        val deferred = CompletableDeferred<CallbackResult>()

        val server = try {
            embeddedServer(Netty, configure = {
                connector {
                    host = "127.0.0.1"
                    port = Constants.PLANNING_CENTER_OAUTH_PORT
                }
            }) {
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

        // `finally`, not a plain sequence: the caller is a dialog, and closing it cancels this
        // coroutine mid-await. Without this the `stop` is skipped, Netty keeps 127.0.0.1:47850, and
        // every later connect attempt in the same run fails to bind — for the life of the app,
        // because nothing ever hands the port back.
        return try {
            withTimeoutOrNull(CALLBACK_TIMEOUT_MS) { deferred.await() } ?: CallbackResult.Timeout
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1000)
        }
    }
}

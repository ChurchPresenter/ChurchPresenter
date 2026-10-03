package org.churchpresenter.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.security.MessageDigest
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants

/*
 * The checks a request passes before a route acts on it: a blocked device, the API key, and the
 * operator's approval for the presentation remote, the Q&A admin page and the musician view.
 *
 * Functions of [CompanionServer], kept beside it rather than in it so no one file holds all of
 * its behaviour; they read and write the server's own state.
 */

/** Blank ids are anonymous clients, which cannot be blocked (there is nothing to block). */
internal fun CompanionServer.isClientBlocked(clientId: String): Boolean =
    clientId.isNotBlank() && clientId in blockedClientIds

internal suspend fun CompanionServer.checkApiKey(call: ApplicationCall): Boolean {
    if (!_apiKeyEnabled.value || _apiKey.value.isEmpty()) return true
    val provided = call.request.headers[Constants.HEADER_API_KEY]
        ?: call.request.queryParameters[Constants.QUERY_PARAM_API_KEY]
        ?: ""
    return if (MessageDigest.isEqual(provided.toByteArray(), _apiKey.value.toByteArray())) {
        true
    } else {
        call.respond(HttpStatusCode.Unauthorized, "Invalid API key")
        false
    }
}

internal suspend fun CompanionServer.checkPresentationRemoteAuth(call: ApplicationCall): Boolean {
    if (!presentationRemoteEnabled) {
        call.respond(HttpStatusCode.Forbidden, """{"error":"remote control is disabled"}""")
        return false
    }
    val pw = presentationRemotePassword
    val provided = call.request.headers[Constants.HEADER_PRESENTATION_PASSWORD]
        ?: call.request.queryParameters["password"]
        ?: ""
    return if (pw.isEmpty() || MessageDigest.isEqual(provided.toByteArray(), pw.toByteArray())) {
        true
    } else {
        call.respond(HttpStatusCode.Unauthorized, """{"error":"Invalid password"}""")
        false
    }
}

/**
 * Asks the desktop operator to approve/deny this device connecting to the presentation
 * remote, exactly like any other remote action (add to schedule, QA moderation, etc.).
 * Only called from the initial /auth handshake — not on every subsequent action —
 * so an approved or session-approved device is never re-prompted mid-session.
 */
internal suspend fun CompanionServer.checkPresentationRemoteConnect(call: ApplicationCall): Boolean {
    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
    val pending = PendingConnectionRequest(clientId)
    onPresentationRemoteConnect.emit(pending)
    val approved = pending.decision.await()
    if (!approved) {
        call.respond(HttpStatusCode.Forbidden, """{"error":"connection denied"}""")
    }
    return approved
}

/** `internal` rather than private so the extracted [qaRoutes] group can call it. */
internal suspend fun CompanionServer.checkQaAdmin(call: ApplicationCall): Boolean {
    val pw = qaAdminPassword
    if (pw.isEmpty()) return true
    val provided = call.request.headers["X-QA-Password"]
        ?: call.request.queryParameters["password"]
        ?: ""
    return if (MessageDigest.isEqual(provided.toByteArray(), pw.toByteArray())) {
        true
    } else {
        call.respond(HttpStatusCode.Unauthorized, """{"error":"Invalid admin password"}""")
        false
    }
}

/**
 * Asks the desktop operator to approve/deny this device connecting to the Q&A admin panel,
 * exactly like the presentation remote's initial connection handshake.
 * Only called from the initial /api/qa/auth handshake — not on every subsequent action —
 * so an approved or session-approved device is never re-prompted mid-session.
 *
 * `internal` rather than private so the extracted [qaRoutes] group can call it.
 */
internal suspend fun CompanionServer.checkQaAdminConnect(call: ApplicationCall): Boolean {
    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
    val pending = PendingConnectionRequest(clientId)
    onQaAdminConnect.emit(pending)
    val approved = pending.decision.await()
    if (!approved) {
        call.respond(HttpStatusCode.Forbidden, """{"error":"connection denied"}""")
    }
    return approved
}

/**
 * Asks the desktop operator to approve a tablet using a Browser Source page's transpose
 * buttons, exactly as the presentation remote's handshake does. Called once per page load,
 * never per press.
 */
internal suspend fun CompanionServer.checkMusicianConnect(call: ApplicationCall): Boolean {
    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
    val pending = PendingConnectionRequest(clientId)
    onMusicianConnect.emit(pending)
    val approved = pending.decision.await()
    if (approved) {
        browserSource.approveMusician(clientId)
    } else {
        call.respond(HttpStatusCode.Forbidden, """{"error":"connection denied"}""")
    }
    return approved
}

/**
 * The Browser Source overlay page for [index]. The page itself is
 * [browserSourceOverlayPage] in BrowserSourcePage.kt; this reads the API-key state it
 * needs so callers do not have to.
 */
internal fun CompanionServer.browserSourceOverlayPageHtml(
    index: Int,
    output: ScreenAssignment,
    bgOverride: String? = null,
): String = browserSourceOverlayPage(
    index, output, _apiKeyEnabled.value, _apiKey.value, bgOverride
)

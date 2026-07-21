package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.app.churchpresenter.utils.Constants
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The link an operator is sent to when connecting a Planning Center account.
 *
 * This URL is the whole of the app's side of the OAuth handshake: it names the account it wants,
 * the permissions it is asking for, and — critically — where Planning Center should send the
 * browser back to. Every part is a string built by hand, and the failure is the same whichever part
 * is wrong: Planning Center shows its own error page instead of a consent screen, and the operator
 * has no way to tell which field was at fault.
 *
 * The redirect in particular has to survive being a query parameter. It contains `://` and a port
 * separator, all of which have to arrive escaped or Planning Center reads the URL as truncated —
 * and it has to match the callback the app actually listens on, exactly, or the handshake is
 * rejected as a redirect mismatch.
 *
 * The rest of this client (token exchange, the Services API, downloads) talks to
 * `api.planningcenteronline.com` through a private Ktor client with hard-coded URLs, so it cannot
 * be pointed anywhere else from a test; the import flow that consumes it is covered by
 * [org.churchpresenter.app.churchpresenter.viewmodel.PlanningCenterImportViewModelTest].
 */
class PlanningCenterClientTest {

    private val authorizationUrl = PlanningCenterClient.buildAuthorizationUrl("cp-client-id")

    /** The query parameters of [authorizationUrl], still encoded. */
    private val query: Map<String, String> =
        authorizationUrl.substringAfter('?').split("&").associate {
            it.substringBefore('=') to it.substringAfter('=')
        }

    private fun decoded(name: String) = URLDecoder.decode(query.getValue(name), "UTF-8")

    // ── Where the browser comes back to ─────────────────────────────────────────

    @Test
    fun `the callback is on the loopback port the app listens on`() {
        assertEquals(
            "http://127.0.0.1:${Constants.PLANNING_CENTER_OAUTH_PORT}/callback",
            PlanningCenterClient.redirectUri(),
            "this has to match both the app's own listener and the value registered with Planning Center",
        )
    }

    @Test
    fun `the callback is on loopback rather than a hostname`() {
        // 127.0.0.1 rather than localhost: OAuth providers treat them as different redirect URIs,
        // and a machine where localhost resolves to ::1 would never reach the listener.
        assertTrue(PlanningCenterClient.redirectUri().startsWith("http://127.0.0.1:"))
    }

    // ── The authorization link ──────────────────────────────────────────────────

    @Test
    fun `the link points at planning center's consent page`() {
        assertTrue(
            authorizationUrl.startsWith("https://api.planningcenteronline.com/oauth/authorize?"),
            authorizationUrl,
        )
    }

    @Test
    fun `the link names the account the operator configured`() {
        assertEquals("cp-client-id", decoded("client_id"))
    }

    @Test
    fun `the link asks for an authorization code`() {
        assertEquals("code", query.getValue("response_type"), "the app exchanges a code, not an implicit token")
    }

    @Test
    fun `the link asks for the permissions the import needs`() {
        assertEquals(
            "people services",
            decoded("scope"),
            "people identifies the account, services is where plans and songs live",
        )
    }

    @Test
    fun `the redirect is sent escaped`() {
        assertFalse(
            query.getValue("redirect_uri").contains("://"),
            "an unescaped redirect truncates the URL at the first slash: $authorizationUrl",
        )
        assertEquals(
            PlanningCenterClient.redirectUri(),
            decoded("redirect_uri"),
            "it has to decode back to exactly what the app listens on, or the handshake is rejected",
        )
    }

    @Test
    fun `the scope is sent escaped`() {
        assertFalse(
            query.getValue("scope").contains(" "),
            "a raw space ends the URL for some clients: $authorizationUrl",
        )
    }

    @Test
    fun `every parameter the handshake needs is present`() {
        assertEquals(
            setOf("client_id", "redirect_uri", "response_type", "scope"),
            query.keys,
            "a missing parameter is refused with a generic error page",
        )
    }

    /**
     * Documents CURRENT behaviour: the client id is put into the URL exactly as configured, without
     * escaping. Planning Center's ids are hexadecimal, so this is not reachable with a real one —
     * but a mistyped value containing `&` would silently add a parameter rather than being rejected.
     */
    @Test
    fun `the client id is used exactly as configured`() {
        val url = PlanningCenterClient.buildAuthorizationUrl("abc 123")

        assertTrue(url.contains("client_id=abc 123"), url)
    }
}

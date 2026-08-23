package org.churchpresenter.companionserver

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Routes for the CA certificate downloads, deliberately outside the API key check.
 *
 * Mobile devices need to download and install the CA certificate BEFORE they can make
 * authenticated API calls. These two endpoints must therefore be accessible without an API key.
 *
 * Trust-on-first-use flow:
 *  1. The companion app (or the user's browser) fetches GET /ca.crt.
 *     iOS: opening the URL in Safari triggers a "Download certificate profile" dialog; the user
 *          then goes to Settings ▸ VPN & Device Management.
 *     Android: the companion app installs the cert via the system Certificate Installer API or
 *          includes it in NetworkSecurityConfig.
 *  2. The user verifies the SHA-256 fingerprint shown in ChurchPresenter's UI.
 *  3. After one-time installation all HTTPS API calls succeed transparently.
 *
 * Depends on nothing but [SslCertificateManager], which is why this group is a plain top-level
 * extension rather than a member of `CompanionServer`.
 */
internal fun Route.certificateRoutes() {

    /**
     * GET /ca.crt
     * DER-encoded CA certificate (binary X.509).
     * The MIME type `application/x-x509-ca-cert` causes iOS Safari / Chrome to present the
     * system "Install Profile" dialog automatically.
     */
    get("/ca.crt") {
        val bytes = SslCertificateManager.getCaCertBytes()
        if (bytes == null) {
            call.respond(
                HttpStatusCode.NotFound,
                "CA certificate is not available (server may be running in plain-HTTP fallback mode)"
            )
            return@get
        }
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            """attachment; filename="ChurchPresenter-CA.crt""""
        )
        call.respondBytes(bytes, ContentType("application", "x-x509-ca-cert"))
    }

    /**
     * GET /ca.pem
     * PEM-encoded CA certificate (Base64 text).
     * Used by:
     *  • Android NetworkSecurityConfig — embed in `res/raw/ca.pem` and reference via
     *    `<certificates src="@raw/ca"/>` in `network_security_config.xml`.
     *  • OpenSSL / curl verification:  `curl --cacert ca.pem https://…`
     *  • Any tool that expects PEM rather than DER format.
     */
    get("/ca.pem") {
        val pem = SslCertificateManager.getCaCertPem()
        if (pem == null) {
            call.respond(
                HttpStatusCode.NotFound,
                "CA certificate is not available (server may be running in plain-HTTP fallback mode)"
            )
            return@get
        }
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            """attachment; filename="ChurchPresenter-CA.pem""""
        )
        call.respondText(pem, ContentType("application", "x-pem-file"))
    }
}

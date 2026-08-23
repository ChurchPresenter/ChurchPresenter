package org.churchpresenter.companionserver

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The CA download endpoints, including the "no certificate yet" replies.
 *
 * `SslCertificateManager` resolves its directory from `user.home` in a `private val` at object
 * init, so it latches once per JVM and there is no seam to point it at an empty temp dir. Whether
 * a cert exists is then a property of the developer's machine, which would make these two branches
 * pass or fail by accident. Mocking the object is the only way to assert both deterministically.
 */
class CertificateRoutesTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeTest
    fun startServer() {
        server = CompanionServer()
        server.start(port = testPort(39_733))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) {
                    kotlinx.coroutines.delay(25)
                }
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
        client = HttpClient(CIO)
        mockkObject(SslCertificateManager)
    }

    @AfterTest
    fun stopServer() {
        unmockkObject(SslCertificateManager)
        runCatching { client.close() }
        runCatching { server.stop() }
    }

    @Test
    fun `the der certificate is served when one exists`() = runBlocking {
        every { SslCertificateManager.getCaCertBytes() } returns byteArrayOf(1, 2, 3)
        val response = client.get("http://127.0.0.1:$port/ca.crt")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.headers["Content-Disposition"]?.contains("ChurchPresenter-CA.crt") == true,
            "the browser must be offered a file to install, not shown bytes"
        )
    }

    @Test
    fun `asking for the der certificate before one exists explains why there is none`() = runBlocking {
        every { SslCertificateManager.getCaCertBytes() } returns null
        val response = client.get("http://127.0.0.1:$port/ca.crt")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("plain-HTTP fallback"), response.bodyAsText())
    }

    @Test
    fun `the pem certificate is served when one exists`() = runBlocking {
        every { SslCertificateManager.getCaCertPem() } returns "-----BEGIN CERTIFICATE-----"
        val response = client.get("http://127.0.0.1:$port/ca.pem")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().startsWith("-----BEGIN CERTIFICATE-----"))
    }

    @Test
    fun `asking for the pem certificate before one exists explains why there is none`() = runBlocking {
        every { SslCertificateManager.getCaCertPem() } returns null
        val response = client.get("http://127.0.0.1:$port/ca.pem")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("plain-HTTP fallback"), response.bodyAsText())
    }

    @Test
    fun `the certificate endpoints are reachable without an api key`() = runBlocking {
        // Deliberately outside the API-key check: a device cannot authenticate until it has
        // installed the CA, so gating these would be a chicken-and-egg lockout.
        server.updateApiKey(enabled = true, key = "secret")
        every { SslCertificateManager.getCaCertBytes() } returns byteArrayOf(1)
        assertEquals(HttpStatusCode.OK, client.get("http://127.0.0.1:$port/ca.crt").status)
    }
}

package org.churchpresenter.app.churchpresenter.utils

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * The network half of the update check, driven against a local JDK HttpServer rather than
 * api.github.com — so it exercises the real HttpURLConnection fetch (status handling, body read,
 * connection failure) without touching the internet. HttpServer.create binds the socket
 * synchronously, so there is no start-up race to wait on.
 */
class UpdateCheckerFetchTest {

    private var server: HttpServer? = null

    @AfterTest
    fun stop() {
        server?.stop(0)
    }

    private fun start(status: Int, body: String): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/releases") { ex: HttpExchange ->
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}/releases"
    }

    private val oneNewInstallableRelease = """[
        {"tag_name":"v9999.0.0","draft":false,"prerelease":false,"html_url":"https://example.org/rel","body":"notes","assets":[
            {"browser_download_url":"https://example.org/app.msi"},
            {"browser_download_url":"https://example.org/app-arm64.dmg"},
            {"browser_download_url":"https://example.org/app.dmg"},
            {"browser_download_url":"https://example.org/app.deb"}
        ]}
    ]"""

    @Test
    fun `checkForUpdate offers a newer release fetched over http`() {
        val url = start(200, oneNewInstallableRelease)
        val result = runBlocking { UpdateChecker.checkForUpdate(includePrereleases = false, apiUrl = url) }
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("9999.0.0", available.info.latestVersion)
        assertNotNull(available.info.downloadUrl)
    }

    @Test
    fun `an empty release list fetched over http reads as up to date`() {
        val url = start(200, "[]")
        val result = runBlocking { UpdateChecker.checkForUpdate(includePrereleases = false, apiUrl = url) }
        assertIs<UpdateCheckResult.UpToDate>(result)
    }

    @Test
    fun `a non-200 response reads as up to date`() {
        val url = start(500, "upstream broke")
        val result = runBlocking { UpdateChecker.checkForUpdate(includePrereleases = false, apiUrl = url) }
        assertIs<UpdateCheckResult.UpToDate>(result)
    }

    @Test
    fun `an unreachable host reads as up to date`() {
        val deadPort = ServerSocket(0).use { it.localPort }
        val result = runBlocking {
            UpdateChecker.checkForUpdate(includePrereleases = false, apiUrl = "http://127.0.0.1:$deadPort/releases")
        }
        assertIs<UpdateCheckResult.UpToDate>(result)
    }

    @Test
    fun `fetchReleasesFrom returns the raw body on success and null on error`() {
        val okUrl = start(200, oneNewInstallableRelease)
        assertNotNull(UpdateChecker.fetchReleasesFrom(okUrl))

        val deadPort = ServerSocket(0).use { it.localPort }
        assertEquals(null, UpdateChecker.fetchReleasesFrom("http://127.0.0.1:$deadPort/releases"))
    }
}

package org.churchpresenter.updater

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The count-only beacon sent when an in-app update download starts, driven against a local JDK
 * HttpServer rather than churchpresenter.org. The beacon returns the job that sends it, and each
 * test joins that job, so nothing here waits on a clock; the retry pause is zero.
 */
class DownloadBeaconTest {

    private var server: HttpServer? = null

    @AfterTest
    fun stop() {
        server?.stop(0)
    }

    private class Hit(val method: String, val query: String, val userAgent: String?, val contentType: String?)

    private fun start(status: Int, hits: MutableList<Hit>): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/api/download") { ex ->
            hits += Hit(
                ex.requestMethod,
                ex.requestURI.rawQuery.orEmpty(),
                ex.requestHeaders.getFirst("User-Agent"),
                ex.requestHeaders.getFirst("Content-Type"),
            )
            ex.sendResponseHeaders(status, -1)
            ex.close()
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}/api/download"
    }

    private val release = UpdaterIdentity(appVersion = "26.9.1", isRelease = true)

    @Test
    fun `a release build counts the download once, naming the platform, the source and the version`() {
        val hits = CopyOnWriteArrayList<Hit>()
        val url = start(200, hits)

        val job = UpdateChecker.reportDownloadStarted("26.9.2", release, url, Duration.ZERO)
        runBlocking { assertNotNull(job).join() }

        val hit = hits.single()
        assertEquals("POST", hit.method)
        assertEquals("platform=${currentPlatformId()}&source=app&version=26.9.2", hit.query)
        assertEquals("ChurchPresenter/26.9.1", hit.userAgent)
        assertEquals("application/json", hit.contentType, "the site's CSRF check refuses form posts")
    }

    @Test
    fun `an error status is an answer, so the beacon is not sent again`() {
        val hits = CopyOnWriteArrayList<Hit>()
        val url = start(500, hits)

        runBlocking { assertNotNull(UpdateChecker.reportDownloadStarted("26.9.2", release, url, Duration.ZERO)).join() }

        assertEquals(1, hits.size)
    }

    @Test
    fun `an unreachable endpoint is tried three times and then given up on`() {
        val deadPort = ServerSocket(0).use { it.localPort }

        val job = assertNotNull(
            UpdateChecker.reportDownloadStarted(
                "26.9.2",
                release,
                "http://127.0.0.1:$deadPort/api/download",
                Duration.ZERO,
            ),
        )
        runBlocking { job.join() }

        assertTrue(job.isCompleted, "a failed beacon ends quietly rather than throwing")
    }

    @Test
    fun `a dev build sends nothing`() {
        val hits = CopyOnWriteArrayList<Hit>()
        val url = start(200, hits)

        val devBuild = UpdaterIdentity(isRelease = false)

        assertNull(UpdateChecker.reportDownloadStarted("26.9.2", devBuild, url, Duration.ZERO))
        assertEquals(0, hits.size)
    }
}

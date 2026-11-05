package org.churchpresenter.signage.smugmug

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The JDK client against a local server: replies read, busy answers tried again, files saved whole. */
@OptIn(ExperimentalPathApi::class)
class JdkSmugMugHttpTest {
    private val root: Path = createTempDirectory("smugmug-http")
    private val busyLeft = AtomicInteger(0)
    private val pauses = mutableListOf<Long>()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange ->
            val (status, body) = when (exchange.requestURI.path) {
                "/json" -> if (busyLeft.getAndDecrement() > 0) 429 to "" else 200 to """{"Response":{"Name":"ok"}}"""
                "/text" -> 200 to "<html>not json</html>"
                "/photo.jpg" -> 200 to "photo bytes"
                else -> 404 to "{}"
            }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val http = JdkSmugMugHttp(pause = { pauses += it })

    @AfterTest
    fun cleanUp() {
        server.stop(0)
        root.deleteRecursively()
    }

    @Test
    fun `a reply is read, after busy answers are waited out`() = runTest {
        busyLeft.set(2)
        val reply = http.json("$base/json")
        assertEquals(listOf(2_000L, 4_000L), pauses)
        assertEquals("ok", (reply["Response"] as JsonObject)["Name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a server that stays busy, a missing page and a page that is not JSON fail`() = runTest {
        busyLeft.set(10)
        assertEquals(429, assertFailsWith<SmugMugStatusException> { http.json("$base/json") }.status)
        assertEquals(404, assertFailsWith<SmugMugStatusException> { http.json("$base/missing") }.status)
        assertFailsWith<IOException> { http.json("$base/text") }
    }

    @Test
    fun `a file is saved whole, and a failed one leaves nothing`() = runTest {
        val photo = root.resolve("photo.jpg")
        http.download("$base/photo.jpg", photo)
        assertEquals("photo bytes", photo.readText())
        val missing = root.resolve("missing.jpg")
        assertFailsWith<SmugMugStatusException> { http.download("$base/missing.jpg", missing) }
        assertFalse(missing.exists())
        assertTrue(root.listDirectoryEntries().none { it.fileName.toString().endsWith(".part") })
    }
}

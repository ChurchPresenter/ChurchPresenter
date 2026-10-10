package org.churchpresenter.server

import io.ktor.server.routing.HttpHeaderRouteSelector
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.PathSegmentOptionalParameterRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.PathSegmentTailcardRouteSelector
import io.ktor.server.routing.RoutingNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `COMPANION_API.md` is the contract companion apps, Bitfocus modules and scripts are written
 * against, so it has to name everything the server answers. This reads the routes from the running
 * server's own routing tree and every WebSocket event and command constant, and fails on any the
 * document does not mention — a new route ships with its documentation or not at all.
 *
 * A route is documented as `` `METHOD /path` ``, exactly as Ktor registers it; a WebSocket route as
 * `` `WS /path` ``. An event or command is documented as its value in backticks.
 */
class ApiDocumentedTest {

    private lateinit var server: CompanionServer

    private val doc: String by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "COMPANION_API.md") }
            .first { it.isFile }
            .readText()
    }

    @BeforeTest
    fun startServer() {
        server = CompanionServer(shutdownGraceMs = 0)
        server.start(port = testPort(39_617))
        runBlocking {
            withTimeoutOrNull(10_000) { while (!server.isRunning.value) delay(10) }
        } ?: error("server did not start")
    }

    @AfterTest
    fun stopServer() {
        server.stop()
    }

    /** `GET /api/songs/{identifier}`, or `WS /ws` for a WebSocket upgrade route. */
    private fun RoutingNode.signature(): String {
        val nodes = generateSequence(this) { it.parent }.toList().reversed()
        val path = nodes.mapNotNull { node ->
            when (val selector = node.selector) {
                is PathSegmentConstantRouteSelector -> selector.value
                is PathSegmentParameterRouteSelector -> "{${selector.name}}"
                is PathSegmentOptionalParameterRouteSelector -> "{${selector.name}?}"
                is PathSegmentTailcardRouteSelector -> "{...}"
                else -> null
            }
        }.joinToString("/", prefix = "/")
        val webSocket = nodes.any { it.selector is HttpHeaderRouteSelector }
        val method = nodes.firstNotNullOf { (it.selector as? HttpMethodRouteSelector)?.method?.value }
        return if (webSocket) "WS $path" else "$method $path"
    }

    @Test
    fun `every route the server registers is in the API reference`() {
        val routes = server.registeredRoutes().map { it.signature() }.distinct()
        assertTrue(routes.size > 50, "the routing tree was not read: $routes")

        val missing = routes.filter { "`$it`" !in doc }

        assertTrue(missing.isEmpty(), "COMPANION_API.md does not document:\n${missing.joinToString("\n")}")
    }

    @Test
    fun `every websocket event and command is in the API reference`() {
        val values = Constants::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) }
            .filter { it.name.startsWith("WS_EVENT_") || it.name.startsWith("WS_CMD_") }
            .map { it.isAccessible = true; it.name to it.get(null) as String }
        assertTrue(values.size > 20, "the constants were not read: $values")

        val missing = values.filter { (_, value) -> "`$value`" !in doc }

        assertTrue(missing.isEmpty(), "COMPANION_API.md does not document:\n${missing.joinToString("\n")}")
    }
}

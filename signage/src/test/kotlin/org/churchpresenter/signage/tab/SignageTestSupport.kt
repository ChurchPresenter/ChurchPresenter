package org.churchpresenter.signage.tab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.sha256Hex
import org.churchpresenter.signage.hub.AgentUpdates
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.signage.hub.SignageHub
import java.io.IOException
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

/**
 * A signage folder, a hub with one paired screen, and a controller over them, in a temporary folder
 * the test owns. Player releases come from memory, signed with a key made for the test.
 */
@OptIn(ExperimentalPathApi::class)
class SignageWorld(
    /** The folder the world lives in, deleted on close. A screenshot pins it: the tab shows its path. */
    val root: Path = createTempDirectory("signage-tab"),
) : AutoCloseable {
    val folder: Path = root.resolve("Signage").createDirectories()
    val scope = CoroutineScope(Job())
    val release = SignedRelease()

    /** The hub's clock; set it back to make a report look old. */
    @Volatile
    var now: Long = System.currentTimeMillis()

    val hub = SignageHub(
        root.resolve("hub"),
        "Main PC",
        scope,
        clock = { now },
        updates = release.updates(root.resolve("builds")),
    )

    /** This ChurchPresenter's analytics setting, as the app hands it to the controller. */
    val diagnostics = MutableStateFlow(true)

    val controller = SignageController(
        hub,
        root.resolve("store"),
        folder,
        scope,
        pollMs = POLL_MS,
        diagnostics = diagnostics,
    )

    /** Puts a file in the signage folder (a picture or video by its extension). */
    fun file(relative: String, text: String = relative): Path = folder.resolve(relative).also {
        it.parent.createDirectories()
        it.writeText(text)
    }

    /**
     * Pairs a screen that runs [version] and can do [features], connected now. Loads the registry
     * first unless [load] is false: a started controller has loaded it already, and loading it again
     * would read the file before the pairing reached it.
     */
    suspend fun pairScreen(
        id: String = "player-1",
        version: String = "26.0.8",
        features: List<String> = listOf(Features.WIPE, Features.PREVIEW, Features.UPDATE),
        load: Boolean = true,
    ) {
        if (load) hub.registry.load()
        hub.links.opened(id, "10.0.0.5", null, features)
        hub.registry.pair(PendingScreen(id, "4821", Platform.LINUX_X64, version, 1920, 1080, "10.0.0.5"), "Lobby")
    }

    /**
     * Starts the controller and pairs a screen once it has loaded the registry. Pairing before then
     * races its load, which can land after the pairing and read the file back without it.
     */
    suspend fun startAndPairScreen() {
        controller.start()
        withTimeout(WAIT_MS) { controller.state.first { it.ready } }
        pairScreen(load = false)
    }

    /**
     * The next message [id] is sent that [match]es, within a few seconds. Listening starts at once,
     * before this returns: messages are not replayed, so call it before what sends one.
     */
    fun <T : ServerMessage> CoroutineScope.expect(
        id: String = "player-1",
        match: (ServerMessage) -> T?,
    ): Deferred<T> =
        async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(WAIT_MS) {
                hub.links.messagesFor(id).first { match(it) != null }.let { checkNotNull(match(it)) }
            }
        }

    override fun close() {
        controller.stop()
        runBlocking { scope.coroutineContext.job.children.toList().forEach { it.cancelAndJoinQuietly() } }
        scope.cancel()
        root.deleteRecursively()
    }

    private suspend fun Job.cancelAndJoinQuietly() {
        cancel()
        join()
    }

    companion object {
        const val POLL_MS = 20L
        const val WAIT_MS = 5_000L
    }
}

/** A player release in memory, its index signed with a key made for the test. */
class SignedRelease(val version: String = "26.0.9") {
    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val build = "the linux build".toByteArray()
    private val index =
        """{"version":"$version","protocolMin":1,"protocolMax":1,"assets":""" +
            """{"linux_x64":{"name":"b.tar.gz","size":${build.size},"sha256":"${sha256Hex(build)}"}}}"""
    private val signature = Base64.getEncoder().encodeToString(
        Signature.getInstance("Ed25519").run {
            initSign(keys.private)
            update(index.toByteArray())
            sign()
        },
    )
    private val files = mapOf(
        "signage-release.json" to index.toByteArray(),
        "signage-release.json.sig" to signature.toByteArray(),
        "b.tar.gz" to build,
    )

    fun updates(cache: Path) = AgentUpdates(
        cache,
        baseUrl = "https://example.test",
        publicKey = Base64.getEncoder().encodeToString(keys.public.encoded),
    ) { url, consume ->
        val bytes = files[url.substringAfterLast('/')] ?: throw IOException("404")
        bytes.inputStream().use(consume)
    }
}

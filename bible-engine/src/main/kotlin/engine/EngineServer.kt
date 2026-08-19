package engine

import engine.bible.SpbLoader
import engine.detection.BookResolver
import engine.engine.DetectionEngine
import engine.engine.DetectionLogger
import engine.socket.Broadcaster
import engine.socket.SttSocketClient
import engine.socket.bibleEngineSocket
import engine.version.VersionCorpus
import engine.version.VersionCorpusLoader
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * Handle to a running engine instance; call [stop] to shut it (and its STT client) down.
 * [boundPort] is the port the WS server actually bound (may differ from the requested one when that
 * was taken) — local clients connect there.
 */
class EngineHandle internal constructor(val boundPort: Int, private val stopFn: () -> Unit) {
    fun stop() { runCatching { stopFn() } }
}

/**
 * Starts the Bible Lookup Engine in-process, **non-blocking**, and returns a handle (or null on a
 * fatal config error). Used by [main] for standalone runs and by ChurchPresenter, which starts the
 * engine in-process when STT connects and talks to it over the WebSocket.
 */
object EngineServer {
    fun start(sttUrl: String, bibleRoot: String, port: Int, bibleFiles: List<String> = emptyList()): EngineHandle? {
        if (bibleRoot.isBlank()) {
            System.err.println("bible-engine: bible root not configured")
            return null
        }
        Config.bibleRoot = bibleRoot
        Config.sttServerUrl = sttUrl

        // Book names are registered from every SPB (cheap header scan), but full verse data + BM25
        // index are built only for the requested bibles (ChurchPresenter's primary + secondary).
        BookResolver.register(SpbLoader.scanAllBookManifests())
        val translations = SpbLoader.loadSelected(bibleFiles)
        if (translations.isEmpty()) {
            System.err.println("bible-engine: no translations loaded from $bibleRoot")
            return null
        }
        Config.loadedBibles = translations.map { it.id }

        // Version detection recognizes translations beyond the two indexed above — the reader's
        // bible is often not one the operator loaded. Built on a background thread because it scans
        // every .spb in the folder; until it lands no version is reported, which costs nothing.
        val versionCorpus = AtomicReference(VersionCorpus.EMPTY)
        if (Config.versionDetectionEnabled) {
            Thread({
                runCatching {
                    VersionCorpusLoader.load(
                        priorityFiles = bibleFiles,
                        onSkip = { name, reason ->
                            System.err.println("bible-engine: version corpus skipped $name — $reason")
                        },
                    )
                }
                    .onSuccess { versionCorpus.set(it); System.err.println(versionCorpusReport(it.labels)) }
                    .onFailure { System.err.println("bible-engine: version corpus failed to build — ${it.message}") }
            }, "ble-version-corpus").apply { isDaemon = true }.start()
        }
        val broadcaster = Broadcaster()
        val detectionEngine = DetectionEngine(
            translations,
            versionCorpus = versionCorpus::get,
            onVersionChanged = { verdict ->
                broadcaster.broadcastVersion(
                    if (verdict == null) {
                        """{"type":"version_detected","version":null,"versionId":null,"confidence":null}"""
                    }
                    else """{"type":"version_detected","version":"${jsonEscape(verdict.label)}",""" +
                        """"versionId":"${jsonEscape(verdict.id)}","confidence":${verdict.confidence}}"""
                )
            },
        )
        // Grow a labeled corpus for free: every emitted detection + its triggering text is appended
        // here, so each live service becomes regression data without manual annotation.
        DetectionLogger.path = File(bibleRoot, "detection-log.jsonl").absolutePath

        // ALL detection-state mutation (DetectionEngine.utterances, Stabilizer maps, Config
        // tuning) is confined to this one thread — the invariant that makes the engine safe
        // with multiple WS clients + the Socket.IO STT thread without any locking.
        val detectionExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "ble-detection").apply { isDaemon = true }
        }
        val detectionDispatcher = detectionExecutor.asCoroutineDispatcher()
        val detectionScope = CoroutineScope(SupervisorJob() + detectionDispatcher)

        fun statusJson(sttConnected: Boolean): String =
            """{"type":"engine_status","sttConnected":$sttConnected,"sttConfigured":${sttUrl.isNotBlank()}}"""

        // Bind the WS server BEFORE connecting the STT client (so we never leak a detecting-but-
        // unreachable STT client). The requested port may be taken — most commonly because it
        // collides with ChurchPresenter's Companion server — so try a small range and use the first
        // free port. Local clients learn the actual port via EngineHandle.boundPort.
        val bound = (port until port + 10).firstNotNullOfOrNull { p ->
            runCatching {
                embeddedServer(Netty, port = p) {
                    install(WebSockets) {
                        pingPeriodMillis = 30_000
                        timeoutMillis = 60_000
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }
                    routing { bibleEngineSocket(detectionEngine, broadcaster, detectionDispatcher) }
                }.start(wait = false)
            }.getOrNull()?.let { it to p }
        }
        if (bound == null) {
            System.err.println("bible-engine: failed to bind WS server on ports $port..${port + 9}")
            return null
        }
        val (server, boundPort) = bound
        if (boundPort != port) System.err.println("bible-engine: port $port busy — bound on $boundPort instead")
        Config.outputPort = boundPort
        // Initial status (also replayed to every late-joining client by the Broadcaster):
        // not connected to STT yet; sttConfigured=false tells consumers a blank STT url is a
        // deliberate WS-input-only setup, not an error.
        broadcaster.broadcastStatus(statusJson(sttConnected = false))

        val sttClient: SttSocketClient? =
            try {
                if (sttUrl.isNotBlank()) {
                    SttSocketClient(
                        sttUrl, detectionEngine, broadcaster, detectionScope,
                        onSttStatus = { connected -> broadcaster.broadcastStatus(statusJson(connected)) }
                    ).also { it.connect() }
                } else null
            } catch (e: Exception) {
                System.err.println("bible-engine: failed to connect STT client — ${e.message}")
                runCatching { server.stop(500, 1000) }
                runCatching { broadcaster.close() }
                runCatching { detectionScope.cancel() }
                runCatching { detectionExecutor.shutdown() }
                return null
            }

        return EngineHandle(boundPort) {
            runCatching { sttClient?.disconnect() }
            runCatching { server.stop(500, 1000) }
            runCatching { broadcaster.close() }
            runCatching { detectionScope.cancel() }
            runCatching { detectionExecutor.shutdown() }
            runCatching { detectionEngine.shutdown() }
        }
    }

    /**
     * One startup line saying whether version detection can actually produce an answer.
     *
     * Without it the feature is indistinguishable from broken on a folder that cannot support it:
     * [engine.version.VersionScorer] needs two renderings of a verse to weigh one against the other,
     * so a corpus below that reports nothing, forever, and says so nowhere. Counting the corpus is
     * necessary but not sufficient — the two must also be in the language being read, which only the
     * scorer can know — hence the wording of the size-1 case.
     */
    internal fun versionCorpusReport(labels: List<String>): String = when (labels.size) {
        0 -> "bible-engine: version detection inactive — no bibles could be indexed from ${Config.bibleRoot}"
        1 -> "bible-engine: version detection inactive — corpus holds only ${labels[0]}; " +
            "reporting a version needs at least two DIFFERENT translations in the language being read"
        else -> "bible-engine: version corpus ready — ${labels.size} translations: ${labels.joinToString(", ")}"
    }

    /** Minimal escaping for the two version fields, which are Bible abbreviations and derived ids. */
    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}

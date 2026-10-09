package org.churchpresenter.obs

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.diagnostics.CrashReporter
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.io.IOException

private const val OP_REQUEST = 6
private const val OP_EVENT = 5
private const val OP_REQUEST_RESPONSE = 7
private const val GET_SCENE_LIST = "GetSceneList"

/** The events after which OBS's scene list is not what it was. */
private val SCENE_LIST_EVENTS = setOf("SceneCreated", "SceneRemoved", "SceneNameChanged", "SceneListChanged")

/**
 * The scene names in [frame], if it is OBS's answer to GetSceneList -- top of its list first,
 * where OBS sends them bottom first.
 */
internal fun sceneListIn(frame: String): List<String>? {
    val d = obsFrame(frame, OP_REQUEST_RESPONSE) ?: return null
    if (d["requestType"]?.jsonPrimitive?.contentOrNull != GET_SCENE_LIST) return null
    val scenes = (d["responseData"] as? JsonObject)?.get("scenes") as? JsonArray ?: return null
    return scenes.mapNotNull { (it as? JsonObject)?.get("sceneName")?.jsonPrimitive?.contentOrNull }.reversed()
}

/** Whether [frame] is an event after which OBS's scene list has changed. */
internal fun changesSceneList(frame: String): Boolean =
    obsFrame(frame, OP_EVENT)?.get("eventType")?.jsonPrimitive?.contentOrNull in SCENE_LIST_EVENTS

/** The data of [frame] when it is an obs-websocket message with opcode [op]; null for anything else. */
private fun obsFrame(frame: String, op: Int): JsonObject? {
    val obj = runCatching { Json.parseToJsonElement(frame) }.getOrNull() as? JsonObject ?: return null
    if ((obj["op"] as? JsonPrimitive)?.intOrNull != op) return null
    return obj["d"] as? JsonObject
}

class OBSWebSocketManager {

    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private val _status = mutableStateOf(ConnectionStatus.DISCONNECTED)
    val status: State<ConnectionStatus> = _status

    private val _errorMessage = mutableStateOf("")
    val errorMessage: State<String> = _errorMessage

    private val _scenes = mutableStateOf<List<String>>(emptyList())

    /** OBS's scenes, in the order its scene list shows them -- empty until [requestScenes] is answered. */
    val scenes: State<List<String>> = _scenes

    /** Whether the scenes were asked for, so a change to them in OBS is asked for again. */
    @Volatile private var scenesWanted = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionJob: Job? = null

    @Volatile private var activeSession: DefaultClientWebSocketSession? = null

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    fun connect(host: String, port: Int, password: String) {
        connectionJob?.cancel()
        activeSession = null
        _status.value = ConnectionStatus.CONNECTING
        _errorMessage.value = ""
        connectionJob = scope.launch {
            try {
                client.webSocket(host = host, port = port, path = "/") {
                    activeSession = this

                    val helloText = (incoming.receive() as? Frame.Text)?.readText()
                        ?: error("Expected Hello frame")
                    val hello = Json.parseToJsonElement(helloText).jsonObject
                    check(hello["op"]?.jsonPrimitive?.int == 0) { "Expected Hello opcode from OBS" }

                    send(buildIdentify(hello["d"]?.jsonObject ?: error("Hello frame has no data"), password))

                    val identifiedText = (incoming.receive() as? Frame.Text)?.readText()
                        ?: error("Expected Identified frame")
                    val identified = Json.parseToJsonElement(identifiedText).jsonObject
                    check(identified["op"]?.jsonPrimitive?.int == 2) {
                        "Authentication failed — check your OBS password"
                    }

                    withContext(Dispatchers.Main) { _status.value = ConnectionStatus.CONNECTED }
                    CrashReporter.breadcrumb("OBS connected ($host:$port)", category = "integration")

                    while (true) {
                        val frame = incoming.receiveCatching().getOrNull() ?: break
                        (frame as? Frame.Text)?.readText()?.let { onFrame(it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Unreachable, refused, timed out, or not speaking HTTP.
                connectFailed(e)
            } catch (e: IllegalStateException) {
                // A handshake that is not OBS's (the checks above), or Ktor's WebSocketException.
                connectFailed(e)
            } catch (e: IllegalArgumentException) {
                // A frame that is not JSON.
                connectFailed(e)
            } catch (e: ClosedReceiveChannelException) {
                // OBS hung up mid-handshake.
                connectFailed(e)
            } finally {
                activeSession = null
                withContext(Dispatchers.Main) {
                    if (_status.value == ConnectionStatus.CONNECTED ||
                        _status.value == ConnectionStatus.CONNECTING) {
                        _status.value = ConnectionStatus.DISCONNECTED
                    }
                }
            }
        }
    }

    private suspend fun connectFailed(e: Exception) {
        withContext(Dispatchers.Main) {
            // Order is load-bearing: the message is written first so that ERROR is never
            // observable beside an empty or stale one. Anything watching the status — the
            // settings chip, a test — reads both, and the two writes are separate, so
            // setting the status first leaves a window showing "failed" with no reason.
            _errorMessage.value = e.message ?: "Connection failed"
            _status.value = ConnectionStatus.ERROR
        }
    }

    fun disconnect() {
        if (_status.value != ConnectionStatus.DISCONNECTED) {
            CrashReporter.breadcrumb("OBS disconnected", category = "integration")
        }
        connectionJob?.cancel()
        connectionJob = null
        activeSession = null
        scenesWanted = false
        _status.value = ConnectionStatus.DISCONNECTED
        _errorMessage.value = ""
        _scenes.value = emptyList()
    }

    fun setScene(sceneName: String) {
        sendRequest("SetCurrentProgramScene", buildJsonObject { put("sceneName", sceneName) })
    }

    /** Asks OBS for its scenes; [scenes] holds them once it answers, and follows them as they change. */
    fun requestScenes() {
        scenesWanted = true
        sendRequest(GET_SCENE_LIST)
    }

    /** What OBS sent after the handshake: the scene list asked for, or word that it changed. */
    private suspend fun onFrame(text: String) {
        sceneListIn(text)?.let { names -> withContext(Dispatchers.Main) { _scenes.value = names } }
        if (scenesWanted && changesSceneList(text)) sendRequest(GET_SCENE_LIST)
    }

    private fun sendRequest(type: String, data: JsonObject? = null) {
        val sess = activeSession ?: return
        scope.launch {
            try {
                sess.send(buildJsonObject {
                    put("op", OP_REQUEST)
                    put("d", buildJsonObject {
                        put("requestType", type)
                        put("requestId", UUID.randomUUID().toString())
                        data?.let { put("requestData", it) }
                    })
                }.toString())
            } catch (_: Exception) { }
        }
    }

    private fun buildIdentify(helloData: JsonObject, password: String): String {
        return buildJsonObject {
            put("op", 1)
            put("d", buildJsonObject {
                put("rpcVersion", 1)
                if (helloData["authentication"] != null && password.isNotEmpty()) {
                    val auth = helloData["authentication"]!!.jsonObject
                    put("authentication", computeAuth(
                        password,
                        auth["salt"]!!.jsonPrimitive.content,
                        auth["challenge"]!!.jsonPrimitive.content
                    ))
                }
            })
        }.toString()
    }

    private fun computeAuth(password: String, salt: String, challenge: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val secret = Base64.getEncoder().encodeToString(
            sha256.digest((password + salt).toByteArray(Charsets.UTF_8))
        )
        sha256.reset()
        return Base64.getEncoder().encodeToString(
            sha256.digest((secret + challenge).toByteArray(Charsets.UTF_8))
        )
    }
}

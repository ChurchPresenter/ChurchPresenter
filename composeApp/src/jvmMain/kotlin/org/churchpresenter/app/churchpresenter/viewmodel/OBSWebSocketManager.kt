package org.churchpresenter.app.churchpresenter.viewmodel

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

class OBSWebSocketManager {

    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private val _status = mutableStateOf(ConnectionStatus.DISCONNECTED)
    val status: State<ConnectionStatus> = _status

    private val _errorMessage = mutableStateOf("")
    val errorMessage: State<String> = _errorMessage

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

                    send(buildIdentify(hello["d"]!!.jsonObject, password))

                    val identifiedText = (incoming.receive() as? Frame.Text)?.readText()
                        ?: error("Expected Identified frame")
                    val identified = Json.parseToJsonElement(identifiedText).jsonObject
                    check(identified["op"]?.jsonPrimitive?.int == 2) { "Authentication failed — check your OBS password" }

                    withContext(Dispatchers.Main) { _status.value = ConnectionStatus.CONNECTED }
                    CrashReporter.breadcrumb("OBS connected ($host:$port)", category = "integration")

                    for (frame in incoming) { /* drain event notifications */ }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _status.value = ConnectionStatus.ERROR
                    _errorMessage.value = e.message ?: "Connection failed"
                }
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

    fun disconnect() {
        if (_status.value != ConnectionStatus.DISCONNECTED) {
            CrashReporter.breadcrumb("OBS disconnected", category = "integration")
        }
        connectionJob?.cancel()
        connectionJob = null
        activeSession = null
        _status.value = ConnectionStatus.DISCONNECTED
        _errorMessage.value = ""
    }

    fun setScene(sceneName: String) {
        val sess = activeSession ?: return
        scope.launch {
            try {
                sess.send(buildJsonObject {
                    put("op", 6)
                    put("d", buildJsonObject {
                        put("requestType", "SetCurrentProgramScene")
                        put("requestId", UUID.randomUUID().toString())
                        put("requestData", buildJsonObject {
                            put("sceneName", sceneName)
                        })
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

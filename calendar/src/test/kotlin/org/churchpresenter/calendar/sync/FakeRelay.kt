package org.churchpresenter.calendar.sync

import kotlinx.serialization.json.Json

/**
 * The relay's behaviour as the desktop sees it, in memory: register once, tokens, revisions,
 * `If-Match`, sealed records it cannot read. What the tests drive [RelayClient] and
 * [SyncCoordinator] against instead of the network.
 */
class FakeRelay(
    private val instanceId: String = "inst",
    private val clientKey: String = "",
) : RelayTransport {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }

    var desktopToken: String? = null
    var rev = 0L
    val records = LinkedHashMap<String, SealedRecord>()
    val tombstones = LinkedHashMap<String, RemoteTombstone>()
    var presetsBox = ""
    val devices = LinkedHashMap<String, RemoteDevice>()
    var lastDesktopInstall = ""
    val calls = mutableListOf<String>()

    /** What the next call answers instead of the real behaviour, once. */
    var nextReply: RelayReply? = null

    /** Something a phone wrote, sealed by the test with the instance key. */
    fun phoneWrote(record: SealedRecord, deviceId: String = "phone-1") {
        rev += 1
        records[record.id] = record.copy(updatedAt = "2026-09-2${rev}T00:00:00Z", updatedBy = deviceId, rev = rev)
    }

    fun phoneDeleted(id: String) {
        rev += 1
        records.remove(id)
        tombstones[id] = RemoteTombstone(id, "2026-09-2${rev}T00:00:00Z")
    }

    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): RelayReply {
        calls += "$method ${url.substringAfter("/i/$instanceId")}"
        val forced = nextReply
        if (forced != null) {
            nextReply = null
            return forced
        }
        val path = url.substringAfter("/i/$instanceId/").substringBefore("?")
        val auth = headers["Authorization"]?.removePrefix("Bearer ")
        return when {
            clientKey.isNotEmpty() && headers["X-Client-Key"] != clientKey ->
                RelayReply(401, """{"error":"client_key"}""")
            path == "register" -> register()
            auth != desktopToken -> RelayReply(401, """{"error":"unauthorized"}""")
            else -> route(method, path, url, headers, body)
        }
    }

    private fun register(): RelayReply {
        if (desktopToken != null) return RelayReply(409, """{"error":"conflict"}""")
        desktopToken = "desk-token"
        return RelayReply(201, """{"desktopToken":"desk-token"}""")
    }

    private fun route(
        method: String,
        path: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): RelayReply =
        when {
            path == "changes" -> {
                val since = url.substringAfter("since=").toLong()
                RelayReply(
                    200,
                    json.encodeToString(
                        ChangesResponse.serializer(),
                        ChangesResponse(
                            rev = rev,
                            records = records.values.filter { it.rev > since },
                            tombstones = tombstones.values.toList(),
                            devices = devices.values.toList(),
                            lastDesktopInstall = lastDesktopInstall,
                        ),
                    ),
                )
            }
            path == "state" && method == "PUT" && headers["If-Match"]?.toLongOrNull() != rev ->
                RelayReply(412, """{"error":"precondition_failed"}""")
            path == "state" && method == "PUT" -> {
                val state = json.decodeFromString(StateRequest.serializer(), body!!)
                records.clear()
                tombstones.clear()
                state.records.forEach { r ->
                    rev += 1
                    records[r.id] = r.copy(updatedAt = "2026-09-30T00:00:00Z", updatedBy = "desktop", rev = rev)
                }
                state.tombstones.forEach { t -> rev += 1; tombstones[t.id] = t }
                rev += 1
                presetsBox = state.presetsBox
                lastDesktopInstall = headers["X-Install"].orEmpty()
                RelayReply(200, """{"rev":$rev}""")
            }
            path.startsWith("devices/") && method == "PUT" -> {
                val id = path.removePrefix("devices/")
                val request = json.decodeFromString(EnrollRequest.serializer(), body!!)
                devices[id] = RemoteDevice(id, request.nameBox, "2026-09-20T00:00:00Z")
                RelayReply(200, """{"ok":true}""")
            }
            path.startsWith("devices/") && method == "DELETE" -> {
                devices.remove(path.removePrefix("devices/"))
                RelayReply(200, """{"ok":true}""")
            }
            else -> RelayReply(404, """{"error":"not_found"}""")
        }
}

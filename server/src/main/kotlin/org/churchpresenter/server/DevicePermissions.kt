package org.churchpresenter.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants

/** One thing the operator can let a trusted device do, or not. */
enum class DevicePermission {
    /** Put sections, slides, pictures and verses up, step through them, run the media, clear. */
    CONTROL_OUTPUT,

    ADD_TO_SCHEDULE,
    REMOVE_FROM_SCHEDULE,

    /** Project an item — put it live straight away. */
    GO_LIVE,

    UPLOAD_FILES,

    /** Whether what the device does shows a toast on the desktop. Not a gate: nothing is refused. */
    SHOW_TOASTS,
}

/**
 * What one device on the Allowed list may do: everything but what the operator switched off.
 *
 * Kept as the switched-off set, so a device with no entry — every device trusted before these
 * existed — may do everything, and a permission added later starts on for every device.
 */
@Serializable
data class DevicePermissions(val denied: Set<DevicePermission> = emptySet()) {
    fun allows(permission: DevicePermission): Boolean = permission !in denied

    fun with(permission: DevicePermission, allowed: Boolean): DevicePermissions =
        DevicePermissions(if (allowed) denied - permission else denied + permission)

    companion object {
        val ALL = DevicePermissions()
    }
}

/**
 * Whether [clientId] may do [permission]: not when it is blocked, and not when the operator
 * switched it off for that device. Anything else may — a device with no entry, and an anonymous
 * one, which is what a Bitfocus trigger URL is.
 */
fun CompanionServer.permits(clientId: String, permission: DevicePermission): Boolean = when {
    clientId.isBlank() -> true
    isClientBlocked(clientId) -> false
    else -> devicePermissions[clientId]?.allows(permission) != false
}

/** [clientId]'s permissions as `GET /api/status` reports them. */
internal fun CompanionServer.permissionsDtoFor(clientId: String) = DevicePermissionsDto(
    canPresent = permits(clientId, DevicePermission.CONTROL_OUTPUT),
    canAddToSchedule = permits(clientId, DevicePermission.ADD_TO_SCHEDULE),
    canRemoveFromSchedule = permits(clientId, DevicePermission.REMOVE_FROM_SCHEDULE),
    canGoLive = permits(clientId, DevicePermission.GO_LIVE),
    canUploadFiles = _fileUploadEnabled.value && permits(clientId, DevicePermission.UPLOAD_FILES),
    maxMediaUploadMb = _maxMediaUploadMb.value,
)

/**
 * The REST side of [permits]: answers 403 — `blocked`, or `not_permitted` — and returns false when
 * the device in the call's [Constants.HEADER_DEVICE_ID] may not do [permission].
 */
internal suspend fun ApplicationCall.requirePermission(server: CompanionServer, permission: DevicePermission): Boolean {
    val clientId = request.headers[Constants.HEADER_DEVICE_ID].orEmpty()
    if (server.permits(clientId, permission)) return true
    val reason = if (server.isClientBlocked(clientId)) REASON_BLOCKED else REASON_NOT_PERMITTED
    respond(HttpStatusCode.Forbidden, """{"ok":false,"reason":"$reason"}""")
    return false
}

/** The API key, then [requirePermission]: whether the call may go ahead. Either answers it when not. */
internal suspend fun ApplicationCall.allowedTo(server: CompanionServer, permission: DevicePermission): Boolean =
    server.checkApiKey(this) && requirePermission(server, permission)

/** The WebSocket side: acks [msg] as refused and returns true when the device may not send it. */
internal suspend fun DefaultWebSocketServerSession.refusedByPermission(
    msg: WebSocketMessage,
    server: CompanionServer,
    wsClientId: String,
    json: Json,
): Boolean {
    val needed = commandPermission(msg.type) ?: return false
    if (server.permits(wsClientId, needed)) return false
    sendCommandAck(msg.commandId, ok = false, reason = REASON_NOT_PERMITTED, json = json)
    return true
}

/** What a WebSocket command needs the device to be allowed, or null for one anyone may send. */
internal fun commandPermission(type: String): DevicePermission? = when (type) {
    Constants.WS_CMD_ADD_TO_SCHEDULE, Constants.WS_CMD_ADD_BATCH_TO_SCHEDULE -> DevicePermission.ADD_TO_SCHEDULE
    Constants.WS_CMD_REMOVE_FROM_SCHEDULE -> DevicePermission.REMOVE_FROM_SCHEDULE
    Constants.WS_CMD_PROJECT -> DevicePermission.GO_LIVE
    in OUTPUT_COMMANDS -> DevicePermission.CONTROL_OUTPUT
    else -> null
}

/** Every command that changes what is on screen. */
private val OUTPUT_COMMANDS = setOf(
    Constants.WS_CMD_NEXT_PICTURE,
    Constants.WS_CMD_PREVIOUS_PICTURE,
    Constants.WS_CMD_NEXT_SLIDE,
    Constants.WS_CMD_PREVIOUS_SLIDE,
    Constants.WS_CMD_MEDIA_PLAY_PAUSE,
    Constants.WS_CMD_MEDIA_STOP,
    Constants.WS_CMD_MEDIA_SEEK_FORWARD,
    Constants.WS_CMD_MEDIA_SEEK_BACKWARD,
    Constants.WS_CMD_MEDIA_MUTE_TOGGLE,
    Constants.WS_CMD_MEDIA_SEEK_TO,
    Constants.WS_CMD_MEDIA_SET_VOLUME,
    Constants.WS_CMD_SELECT_SONG,
    Constants.WS_CMD_SELECT_PICTURE,
    Constants.WS_CMD_SELECT_SONG_SECTION,
    Constants.WS_CMD_SELECT_SLIDE,
    Constants.WS_CMD_SELECT_BIBLE_VERSE,
    Constants.WS_CMD_CLEAR,
    Constants.WS_CMD_TAKE,
    Constants.WS_CMD_BIBLE_HOLD,
    Constants.WS_CMD_MESSAGE,
    Constants.WS_CMD_PROP,
    Constants.WS_CMD_MACRO,
)

private const val REASON_BLOCKED = "blocked"
private const val REASON_NOT_PERMITTED = "not_permitted"

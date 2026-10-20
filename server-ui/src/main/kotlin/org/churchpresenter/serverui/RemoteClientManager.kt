package org.churchpresenter.serverui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.server.DevicePermission
import org.churchpresenter.server.DevicePermissions
import java.io.File

@Serializable
data class RemoteClientLists(
    val allowedClients: Set<String> = emptySet(),
    val blockedClients: Set<String> = emptySet(),
    val clientLabels: Map<String, String> = emptyMap(),
    /** What each allowed device may do; a device with no entry may do everything. */
    val permissions: Map<String, DevicePermissions> = emptyMap(),
)

/**
 * Manages the persistent permanent-allow and permanent-block lists for remote companion clients.
 * Lists are stored in ~/.churchpresenter/remote_clients.json.
 *
 * Compose-observable: [allowedClients] and [blockedClients] are Compose State properties so
 * any composable that reads them will recompose when the lists change.
 */
class RemoteClientManager {
    private val file = RemoteClientsFile()

    private var _lists by mutableStateOf(file.load())

    val allowedClients: Set<String> get() = _lists.allowedClients
    val blockedClients: Set<String> get() = _lists.blockedClients
    val clientLabels: Map<String, String> get() = _lists.clientLabels
    val permissions: Map<String, DevicePermissions> get() = _lists.permissions

    fun isAllowed(clientId: String): Boolean =
        clientId.isNotBlank() && clientId in _lists.allowedClients

    fun isBlocked(clientId: String): Boolean =
        clientId.isNotBlank() && clientId in _lists.blockedClients

    /** Adds to permanent allow list and removes from block list if present. */
    fun allowPermanently(clientId: String) {
        if (clientId.isBlank()) return
        _lists = _lists.copy(
            allowedClients = _lists.allowedClients + clientId,
            blockedClients = _lists.blockedClients - clientId
        )
        file.save(_lists)
    }

    /** Adds to permanent block list and removes from allow list -- and its permissions -- if present. */
    fun blockPermanently(clientId: String) {
        if (clientId.isBlank()) return
        _lists = _lists.copy(
            blockedClients = _lists.blockedClients + clientId,
            allowedClients = _lists.allowedClients - clientId,
            permissions = _lists.permissions - clientId,
        )
        file.save(_lists)
    }

    fun removeAllowed(clientId: String) {
        _lists = _lists.copy(
            allowedClients = _lists.allowedClients - clientId,
            permissions = _lists.permissions - clientId,
        )
        file.save(_lists)
    }

    /** Switches one of an allowed device's permissions on or off. */
    fun setPermission(clientId: String, permission: DevicePermission, allowed: Boolean) {
        if (!isAllowed(clientId)) return
        val updated = permissionsFor(clientId).with(permission, allowed)
        _lists = _lists.copy(
            permissions = if (updated == DevicePermissions.ALL) {
                _lists.permissions - clientId
            } else {
                _lists.permissions + (clientId to updated)
            },
        )
        file.save(_lists)
    }

    fun removeBlocked(clientId: String) {
        _lists = _lists.copy(blockedClients = _lists.blockedClients - clientId)
        file.save(_lists)
    }

    /** Returns the human-readable label for the given device ID, or empty string if none set. */
    fun getLabel(clientId: String): String = _lists.clientLabels[clientId] ?: ""

    /** Saves (or clears when blank) the human-readable label for a device ID. */
    fun setLabel(clientId: String, label: String) {
        if (clientId.isBlank()) return
        val trimmed = label.trim()
        _lists = _lists.copy(
            clientLabels = if (trimmed.isEmpty()) _lists.clientLabels - clientId
                           else _lists.clientLabels + (clientId to trimmed)
        )
        file.save(_lists)
    }

    fun reload() { _lists = file.load() }
}

/** Returns true if the client is in either the permanent allow or permanent block list. */
fun RemoteClientManager.isKnown(clientId: String): Boolean = isAllowed(clientId) || isBlocked(clientId)

/** What [clientId] may do: everything, unless the operator has switched something off. */
fun RemoteClientManager.permissionsFor(clientId: String): DevicePermissions =
    permissions[clientId] ?: DevicePermissions.ALL

/** Where the lists are kept between runs: `~/.churchpresenter/remote_clients.json`. */
private class RemoteClientsFile {
    private val appDataDir = File(System.getProperty("user.home"), ".churchpresenter")
    private val clientsFile = File(appDataDir, "remote_clients.json")

    private val jsonFormat = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): RemoteClientLists = try {
        if (clientsFile.exists()) jsonFormat.decodeFromString(clientsFile.readText())
        else RemoteClientLists()
    } catch (_: Exception) { RemoteClientLists() }

    fun save(lists: RemoteClientLists) {
        try {
            appDataDir.mkdirs()
            clientsFile.writeText(jsonFormat.encodeToString(lists))
        } catch (_: Exception) {}
    }
}

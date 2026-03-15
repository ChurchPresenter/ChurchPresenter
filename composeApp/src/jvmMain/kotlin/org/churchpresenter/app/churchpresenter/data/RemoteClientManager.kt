package org.churchpresenter.app.churchpresenter.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class RemoteClientLists(
    val allowedClients: Set<String> = emptySet(),
    val blockedClients: Set<String> = emptySet(),
    val clientLabels: Map<String, String> = emptyMap()
)

/**
 * Manages the persistent permanent-allow and permanent-block lists for remote companion clients.
 * Lists are stored in ~/.churchpresenter/remote_clients.json.
 *
 * Compose-observable: [allowedClients] and [blockedClients] are Compose State properties so
 * any composable that reads them will recompose when the lists change.
 */
class RemoteClientManager {
    private val appDataDir = File(System.getProperty("user.home"), ".churchpresenter")
    private val clientsFile = File(appDataDir, "remote_clients.json")

    private val jsonFormat = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var _lists by mutableStateOf(load())

    val allowedClients: Set<String> get() = _lists.allowedClients
    val blockedClients: Set<String> get() = _lists.blockedClients
    val clientLabels: Map<String, String> get() = _lists.clientLabels

    private fun load(): RemoteClientLists = try {
        if (clientsFile.exists()) jsonFormat.decodeFromString(clientsFile.readText())
        else RemoteClientLists()
    } catch (_: Exception) { RemoteClientLists() }

    private fun save() {
        try {
            appDataDir.mkdirs()
            clientsFile.writeText(jsonFormat.encodeToString(_lists))
        } catch (_: Exception) {}
    }

    fun isAllowed(clientId: String): Boolean =
        clientId.isNotBlank() && clientId in _lists.allowedClients

    fun isBlocked(clientId: String): Boolean =
        clientId.isNotBlank() && clientId in _lists.blockedClients

    /** Returns true if the client is in either the permanent allow or permanent block list. */
    fun isKnown(clientId: String): Boolean = isAllowed(clientId) || isBlocked(clientId)

    /** Adds to permanent allow list and removes from block list if present. */
    fun allowPermanently(clientId: String) {
        if (clientId.isBlank()) return
        _lists = _lists.copy(
            allowedClients = _lists.allowedClients + clientId,
            blockedClients = _lists.blockedClients - clientId
        )
        save()
    }

    /** Adds to permanent block list and removes from allow list if present. */
    fun blockPermanently(clientId: String) {
        if (clientId.isBlank()) return
        _lists = _lists.copy(
            blockedClients = _lists.blockedClients + clientId,
            allowedClients = _lists.allowedClients - clientId
        )
        save()
    }

    fun removeAllowed(clientId: String) {
        _lists = _lists.copy(allowedClients = _lists.allowedClients - clientId)
        save()
    }

    fun removeBlocked(clientId: String) {
        _lists = _lists.copy(blockedClients = _lists.blockedClients - clientId)
        save()
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
        save()
    }

    fun reload() { _lists = load() }
}

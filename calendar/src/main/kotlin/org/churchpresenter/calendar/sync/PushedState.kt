package org.churchpresenter.calendar.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.io.writeTextAtomically
import java.io.File

/**
 * What this desktop last pushed with `PUT /state`: the instance it went to, a hash of the unsealed
 * picture, and when. The boxes themselves cannot tell two pushes apart -- every seal takes a fresh
 * nonce -- so this is what lets [SyncCoordinator] see that nothing changed.
 */
@Serializable
data class PushedState(val instanceId: String = "", val hash: String = "", val at: String = "")

/** `state-sync.json` beside `calendar.json`: the memory that keeps an idle round from rewriting the relay. */
class PushedStateStore(private val folder: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    internal val file: File = File(folder, STATE_SYNC_FILE)

    fun load(): PushedState {
        if (!file.isFile) return PushedState()
        return runCatching { json.decodeFromString(PushedState.serializer(), file.readText()) }
            .getOrDefault(PushedState())
    }

    fun save(state: PushedState) {
        folder.mkdirs()
        file.writeTextAtomically(json.encodeToString(PushedState.serializer(), state))
    }

    private companion object {
        const val STATE_SYNC_FILE = "state-sync.json"
    }
}

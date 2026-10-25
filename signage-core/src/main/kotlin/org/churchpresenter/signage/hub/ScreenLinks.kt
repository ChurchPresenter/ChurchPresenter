package org.churchpresenter.signage.hub

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.ScreenOverride
import org.churchpresenter.signage.core.ServerMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** A paired screen's open connection, and what it last reported. */
data class ScreenLink(
    val address: String,
    val connectedAtMs: Long,
    val manifestVersion: String?,
    val status: PlayerMessage.Status? = null,
    val statusAtMs: Long? = null,
    /** Which connection this is, so a stale one closing does not drop a newer one. */
    val session: Long,
    /** What the player said it can do, from its hello. */
    val features: List<String> = emptyList(),
    /** The last picture of what it shows, while the Signage tab asks for them. */
    val preview: PlayerMessage.Preview? = null,
    /** When [preview] arrived, by this computer's clock: a picture that stops coming is not shown as current. */
    val previewAtMs: Long? = null,
    /** How an update is going: ChurchPresenter's download first, then the player's own steps. */
    val update: PlayerMessage.UpdateProgress? = null,
)

/**
 * The paired screens connected now, and the messages on their way to them. A manual switch made
 * while a screen is away waits here and goes out when it reconnects; anything else is only for a
 * screen that is listening.
 */
class ScreenLinks(private val clock: () -> Long = System::currentTimeMillis) {
    private val _links = MutableStateFlow<Map<String, ScreenLink>>(emptyMap())
    val links: StateFlow<Map<String, ScreenLink>> = _links.asStateFlow()

    private val outbox = MutableSharedFlow<Pair<String, ServerMessage>>(
        extraBufferCapacity = OUTBOX_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val heldOverrides = ConcurrentHashMap<String, HeldOverride>()
    private val sessions = AtomicLong()

    private data class HeldOverride(val override: ScreenOverride?)

    /** Records [id]'s new connection and returns its session, for [closed]. */
    fun opened(id: String, address: String, manifestVersion: String?, features: List<String> = emptyList()): Long {
        val session = sessions.incrementAndGet()
        _links.update {
            it + (id to ScreenLink(address, clock(), manifestVersion, session = session, features = features))
        }
        return session
    }

    fun closed(id: String, session: Long) {
        _links.update { links -> if (links[id]?.session == session) links - id else links }
    }

    /** Keeps what [id] last reported: its status, a picture of its screen, or how an update goes. */
    fun reported(id: String, message: PlayerMessage) = _links.edit(id) { link ->
        when (message) {
            is PlayerMessage.Status -> link.copy(status = message, statusAtMs = clock())
            is PlayerMessage.Preview -> link.copy(preview = message, previewAtMs = clock())
            is PlayerMessage.UpdateProgress -> link.copy(update = message)
            is PlayerMessage.Hello -> link
        }
    }

    fun synced(id: String, manifestVersion: String) = _links.edit(id) { it.copy(manifestVersion = manifestVersion) }

    /** What [id] is sent: subscribe before [opened], so nothing sent in between is missed. */
    fun messagesFor(id: String): Flow<ServerMessage> = outbox.filter { it.first == id }.map { it.second }

    /** Sends [message] to [id] if it is connected; false when it is not. */
    fun send(id: String, message: ServerMessage): Boolean {
        if (id !in _links.value) return false
        return outbox.tryEmit(id to message)
    }

    /** Sends a manual switch now, or keeps it for when [id] next connects. */
    fun setOverride(id: String, override: ScreenOverride?) {
        if (!send(id, ServerMessage.SetOverride(override))) heldOverrides[id] = HeldOverride(override)
        else heldOverrides.remove(id)
    }

    /** The manual switch made while [id] was away, taken once; null when there is none. */
    fun takeHeldOverride(id: String): ServerMessage.SetOverride? =
        heldOverrides.remove(id)?.let { ServerMessage.SetOverride(it.override) }

    fun forget(id: String) {
        heldOverrides.remove(id)
    }

    private companion object {
        const val OUTBOX_CAPACITY = 64
    }
}

/** Changes [id]'s link if it is connected; a screen that is not has nothing to change. */
private fun MutableStateFlow<Map<String, ScreenLink>>.edit(id: String, change: (ScreenLink) -> ScreenLink) =
    update { links ->
        val link = links[id] ?: return@update links
        links + (id to change(link))
    }

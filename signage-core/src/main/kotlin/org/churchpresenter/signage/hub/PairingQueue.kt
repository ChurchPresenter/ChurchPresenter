package org.churchpresenter.signage.hub

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import java.util.concurrent.ConcurrentHashMap

/** A player waiting on the TV with [code] for someone to approve it in the Signage tab. */
data class PendingScreen(
    val playerId: String,
    val code: String,
    val platform: Platform,
    val agentVersion: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val address: String,
)

/** What the Signage tab decided about a [PendingScreen]. */
sealed interface PairingDecision {
    /** Paired: the player is sent [token] and reconnects with it. */
    data class Approved(val token: String) : PairingDecision

    data object Rejected : PairingDecision
}

/**
 * The players waiting to be paired. A player is listed only while its connection is open and
 * waiting, so approving one always reaches it.
 */
class PairingQueue {
    private val _pending = MutableStateFlow<List<PendingScreen>>(emptyList())
    val pending: StateFlow<List<PendingScreen>> = _pending.asStateFlow()

    private val waiting = ConcurrentHashMap<String, CompletableDeferred<PairingDecision>>()

    /** Lists the player that sent [hello] and suspends until the tab decides, or the connection ends. */
    suspend fun await(hello: PlayerMessage.Hello, address: String): PairingDecision {
        val decision = CompletableDeferred<PairingDecision>()
        waiting.put(hello.playerId, decision)?.cancel()
        val entry = PendingScreen(
            playerId = hello.playerId,
            code = hello.pairingCode.orEmpty(),
            platform = hello.platform,
            agentVersion = hello.agentVersion,
            screenWidth = hello.screenWidth,
            screenHeight = hello.screenHeight,
            address = address,
        )
        _pending.update { list -> list.filterNot { it.playerId == hello.playerId } + entry }
        try {
            return decision.await()
        } finally {
            waiting.remove(hello.playerId, decision)
            _pending.update { list -> list.filterNot { it.playerId == hello.playerId && waiting[it.playerId] == null } }
        }
    }

    /** Settles [playerId]'s wait; false when it is no longer waiting. */
    fun decide(playerId: String, decision: PairingDecision): Boolean = waiting[playerId]?.complete(decision) == true
}

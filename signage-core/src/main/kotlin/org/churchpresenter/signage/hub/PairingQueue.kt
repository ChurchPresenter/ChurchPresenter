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
 * A removed screen this computer will not take back for a while, so another ChurchPresenter can.
 * [address] is the IP it last connected from, knocked on if someone presses Pair here.
 */
data class HeldScreen(val playerId: String, val name: String, val untilMs: Long, val address: String? = null)

/** A held screen someone asked back with Pair here: knocked on at [address] until it comes, or [untilMs]. */
data class Invite(val playerId: String, val address: String, val untilMs: Long)

/**
 * The players waiting to be paired. A player is listed only while its connection is open and
 * waiting, so approving one always reaches it.
 */
class PairingQueue(private val clock: () -> Long = System::currentTimeMillis) {
    private val _held = MutableStateFlow<List<HeldScreen>>(emptyList())

    /** The removed screens being held back; one past its time stays listed until [isHeld] or [hold] next runs. */
    val held: StateFlow<List<HeldScreen>> = _held.asStateFlow()

    private val _invites = MutableStateFlow<List<Invite>>(emptyList())

    /** The screens asked back with Pair here, still on their way; read with [activeInvites]. */
    val invites: StateFlow<List<Invite>> = _invites.asStateFlow()

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

    /** Holds [playerId] back for [HOLD_MS]: [isHeld] says so, and its connection is turned away. */
    fun hold(playerId: String, name: String, address: String? = null) {
        val entry = HeldScreen(playerId, name, clock() + HOLD_MS, address)
        _held.update { list -> list.filterNot { it.playerId == playerId || it.untilMs <= clock() } + entry }
    }

    /** Takes [playerId] off hold, so it can be approved here; false when it was not held. */
    fun release(playerId: String): Boolean {
        val had = _held.value.any { it.playerId == playerId }
        _held.update { list -> list.filterNot { it.playerId == playerId } }
        return had
    }

    /** Whether [playerId] was removed from this computer recently enough to be turned away. */
    fun isHeld(playerId: String): Boolean {
        val now = clock()
        _held.update { list -> list.filter { it.untilMs > now } }
        return _held.value.any { it.playerId == playerId }
    }

    /**
     * Pair here: takes [playerId] off hold and, when its address is known, has it knocked on for
     * [INVITE_MS] so a player waiting on another ChurchPresenter comes here. False when it was not held.
     */
    fun invite(playerId: String): Boolean {
        val held = _held.value.firstOrNull { it.playerId == playerId } ?: return false
        release(playerId)
        val address = held.address ?: return true
        val invite = Invite(playerId, address, clock() + INVITE_MS)
        _invites.update { list -> list.filterNot { it.playerId == playerId } + invite }
        return true
    }

    /** The invites still running; ones past their time are dropped. */
    fun activeInvites(): List<Invite> {
        val now = clock()
        _invites.update { list -> list.filter { it.untilMs > now } }
        return _invites.value
    }

    /** [playerId] has connected: it needs no more knocking. */
    fun arrived(playerId: String) = _invites.update { list -> list.filterNot { it.playerId == playerId } }

    /** Settles [playerId]'s wait; false when it is no longer waiting. */
    fun decide(playerId: String, decision: PairingDecision): Boolean = waiting[playerId]?.complete(decision) == true

    companion object {
        /** How long a removed screen is held back: long enough for it to look elsewhere, short enough to re-add. */
        const val HOLD_MS = 5 * 60 * 1000L

        /** How long a screen asked back with Pair here is knocked on before giving up. */
        const val INVITE_MS = 2 * 60 * 1000L
    }
}

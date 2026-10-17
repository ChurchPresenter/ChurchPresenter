package org.churchpresenter.server

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.churchpresenter.calendar.sync.AddComputerRequest
import org.churchpresenter.calendar.sync.JoinedComputer
import org.churchpresenter.calendar.sync.PromoteRequest
import org.churchpresenter.calendar.sync.RelayFailure
import org.churchpresenter.calendar.sync.RemoteRole
import org.churchpresenter.calendar.sync.addComputer
import org.churchpresenter.calendar.sync.promote
import org.churchpresenter.calendar.sync.removeComputer
import org.churchpresenter.settings.CalendarSyncSettings
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The other computers on this calendar. On the main computer: adding one (a [CalendarJoin] to hand
 * over), removing one, making one the main computer. On a computer that joined: joining, leaving,
 * and taking over as the main computer once it has been silent for a week.
 *
 * Every action answers null or false when [enabled] is off -- the feature is behind dev mode, and a
 * saved pairing must not keep working unseen when it is.
 */
class CalendarComputers internal constructor(
    private val relay: CalendarRelayAccess,
    private val lock: Mutex,
    private val enabled: () -> Boolean,
    private val onFailure: (RelayFailure) -> Unit,
    private val syncNow: suspend () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Instant = Instant::now,
) {
    private val settings: () -> CalendarSyncSettings get() = relay.settings
    private val saveSettings: (CalendarSyncSettings) -> Unit get() = relay.saveSettings

    private val _list = MutableStateFlow<List<JoinedComputer>>(emptyList())

    /** The computers that joined, as the main computer's last round listed them. */
    val list: StateFlow<List<JoinedComputer>> = _list.asStateFlow()

    internal fun update(computers: List<JoinedComputer>) {
        _list.value = computers
    }

    /**
     * Adds a computer as [role] under [name], returning what to hand it. [mainName] is what this
     * computer is called, for the other one's status line.
     */
    suspend fun invite(role: String, name: String, mainName: String): CalendarJoin? = lock.withLock {
        if (!enabled() || !settings().enabled || settings().isJoined) return@withLock null
        withContext(io) {
            relayCall {
                val desktopToken = relay.withClientKey { relay.ensureRegistered() }
                val current = settings()
                val id = UUID.randomUUID().toString()
                val token = CalendarRelayAccess.newDeviceToken()
                val nameBox = name.take(NAME_CHARS).takeIf { it.isNotBlank() }
                    ?.let { relay.sealing().sealText(it, id) }.orEmpty()
                val request = AddComputerRequest(CalendarRelayAccess.sha256Hex(token), role, nameBox)
                relay.client().addComputer(desktopToken, id, request)
                _list.value = _list.value + JoinedComputer(id, role, name, now().toString(), "")
                CalendarJoin(
                    relay = relay.relayUrl(),
                    instance = current.instanceId,
                    desktop = id,
                    token = token,
                    key = current.instanceKey,
                    role = role,
                    mainName = mainName.take(NAME_CHARS),
                    expires = now().plus(JOIN_LIFETIME).toString(),
                )
            }
        }
    }

    suspend fun remove(id: String) = lock.withLock {
        if (!enabled() || settings().isJoined) return@withLock
        withContext(io) {
            relayCall { relay.client().removeComputer(relay.token, id) }
                ?.let { _list.value = _list.value.filterNot { it.id == id } }
        }
    }

    /**
     * Makes computer [id] the main one. This computer stays on as a follower under a fresh id, named
     * [ownName], and from its next round sends only what it changes.
     */
    suspend fun promote(id: String, ownName: String): Boolean {
        val done = lock.withLock {
            if (!enabled() || settings().isJoined) return@withLock false
            withContext(io) {
                val formerId = UUID.randomUUID().toString()
                val nameBox = ownName.take(NAME_CHARS).takeIf { it.isNotBlank() }
                    ?.let { relay.sealing().sealText(it, formerId) }.orEmpty()
                val promoted = _list.value.firstOrNull { it.id == id }?.name.orEmpty()
                relayCall {
                    relay.client().promote(relay.token, id, PromoteRequest(keepFormer = true, formerId, nameBox))
                    val current = settings()
                    saveSettings(
                        current.copy(
                            role = CalendarSyncSettings.ROLE_FOLLOWER,
                            computerId = formerId,
                            computerToken = current.desktopToken,
                            desktopToken = "",
                            mainName = promoted,
                        ),
                    )
                    _list.value = emptyList()
                } != null
            }
        }
        if (done) syncNow()
        return done
    }

    /**
     * A follower making itself the main computer, for when the old one is gone. The relay allows it
     * only after the main computer has been silent for a week; the old main token stops working.
     */
    suspend fun takeOver(): Boolean {
        val done = lock.withLock {
            val current = settings()
            if (!enabled() || current.role != CalendarSyncSettings.ROLE_FOLLOWER) return@withLock false
            withContext(io) {
                relayCall {
                    val request = PromoteRequest(keepFormer = false)
                    relay.client().promote(current.computerToken, current.computerId, request)
                    saveSettings(current.asMain())
                } != null
            }
        }
        if (done) syncNow()
        return done
    }

    /**
     * Joins the calendar [join] belongs to, in place of whatever this computer synced with before,
     * and runs the first round. The relay counts the computer as claimed from that round on.
     */
    suspend fun join(join: CalendarJoin): Boolean {
        if (!enabled()) return false
        val sameRelay = join.relay.trimEnd('/') == relay.builtInRelayUrl.trimEnd('/')
        saveSettings(
            settings().copy(
                enabled = true,
                relayUrl = if (sameRelay) "" else join.relay,
                instanceId = join.instance,
                instanceKey = join.key,
                desktopToken = "",
                role = join.role,
                computerId = join.desktop,
                computerToken = join.token,
                mainName = join.mainName,
                cursor = 0L,
                lastSyncAt = "",
            ),
        )
        return syncNow()
    }

    /** Stops syncing with another computer's calendar; the services already here stay. */
    fun leave() {
        saveSettings(
            settings().copy(
                instanceId = "", instanceKey = "", role = "", computerId = "", computerToken = "",
                mainName = "", cursor = 0L, lastSyncAt = "",
            ),
        )
        _list.value = emptyList()
    }

    /** [block]'s result, or null with the failure shown where the sync's own are. */
    private inline fun <T> relayCall(block: () -> T): T? = try {
        block()
    } catch (e: RelayFailure) {
        onFailure(e)
        null
    }

    private companion object {
        const val NAME_CHARS = 120
        val JOIN_LIFETIME: Duration = Duration.ofHours(24)
    }
}

/** This joined computer's pairing as the main computer's: its own token becomes the desktop token. */
internal fun CalendarSyncSettings.asMain(): CalendarSyncSettings =
    copy(desktopToken = computerToken, role = "", computerToken = "", computerId = "", mainName = "")

/**
 * The settings as the relay's answer on a round says this computer now is: promoted by the main
 * computer, it takes the desktop token; made a follower or viewer by another's promotion or the main
 * computer's change, it keeps its token in the joined computer's place and stops pushing.
 */
internal fun CalendarSyncSettings.withRelayRole(relayRole: String): CalendarSyncSettings {
    val joinedRole = relayRole == RemoteRole.FOLLOWER || relayRole == RemoteRole.VIEWER
    return when {
        relayRole == RemoteRole.MAIN && isJoined -> asMain()
        joinedRole && !isJoined -> copy(computerToken = desktopToken, desktopToken = "", role = relayRole)
        joinedRole && relayRole != role -> copy(role = relayRole)
        else -> this
    }
}

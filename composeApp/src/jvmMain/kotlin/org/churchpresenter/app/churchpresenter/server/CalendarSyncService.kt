package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.churchpresenter.calendar.CalendarFileWatcher
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.sync.HttpRelayTransport
import org.churchpresenter.calendar.sync.EnrollRequest
import org.churchpresenter.calendar.sync.RelayClient
import org.churchpresenter.calendar.sync.RelayFailure
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.calendar.sync.Sealing
import org.churchpresenter.calendar.sync.Envelope
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.calendar.sync.SyncCoordinator
import org.churchpresenter.calendar.sync.SyncOutcome
import org.churchpresenter.calendar.sync.fetchClientKey
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** What a newly enrolled phone is handed, as the QR the desktop shows after the operator approved it. */
data class CalendarEnrollment(val relayUrl: String, val instanceId: String, val deviceToken: String, val instanceKey: String) {
    val qrContent: String
        get() = "churchpresenter://calendar-enroll?relay=${relayUrl.trimEnd('/')}&instance=$instanceId&token=$deviceToken&key=$instanceKey"
}

/** Where the desktop stands with the relay, for the settings card and the calendar window's banner. */
sealed class CalendarSyncStatus {
    data object Off : CalendarSyncStatus()
    data object Unpaired : CalendarSyncStatus()
    data object Syncing : CalendarSyncStatus()
    data class Synced(val at: String, val outcome: SyncOutcome) : CalendarSyncStatus()
    data class Failed(val message: String) : CalendarSyncStatus()
    /** The startup round ran out of time; the app went on with the local file. */
    data object TimedOut : CalendarSyncStatus()
    /** The relay no longer accepts this desktop's token; the instance has to be paired again. */
    data object Unauthorized : CalendarSyncStatus()
    /** Another installation has pushed to this instance; ours stops pushing until somebody decides. */
    data class OtherDesktop(val installId: String) : CalendarSyncStatus()
}

/**
 * The desktop's end of calendar sync: registers this instance once, keeps `calendar.json` and the
 * relay in step, and hands the settings card what it shows. [settings] and [saveSettings] are
 * the one place the sync block of `settings.json` is read and written.
 */
class CalendarSyncService(
    private val folder: File,
    private val songFolder: File?,
    private val settings: () -> CalendarSyncSettings,
    private val saveSettings: (CalendarSyncSettings) -> Unit,
    private val transport: RelayTransport = HttpRelayTransport(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _status = MutableStateFlow<CalendarSyncStatus>(CalendarSyncStatus.Off)
    val status: StateFlow<CalendarSyncStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val watcher = CalendarFileWatcher(folder, io)
    private val lock = Mutex()

    @Volatile
    private var songCache: List<SongItem>? = null

    /** The startup round — pull, merge, push — within [timeoutMs]. Returns whether it completed. */
    suspend fun syncOnStartup(timeoutMs: Long = STARTUP_TIMEOUT_MS): Boolean {
        if (!settings().enabled) {
            _status.value = CalendarSyncStatus.Off
            return false
        }
        return try {
            withTimeout(timeoutMs) { registerIfNeeded() && syncNow() }
        } catch (_: TimeoutCancellationException) {
            _status.value = CalendarSyncStatus.TimedOut
            false
        }
    }

    /** Keeps syncing until cancelled: pushes every local save, and pulls on a timer while the app is open. */
    suspend fun run() = coroutineScope {
        if (!settings().enabled) return@coroutineScope
        launch { watcher.run(onChanged = { round { it.pushLocal(token(), cursor()) } }) }
        launch {
            while (coroutineContext.isActive) {
                delay(PULL_INTERVAL_MS)
                syncNow()
            }
        }
    }

    suspend fun syncNow(): Boolean = round { it.sync(token(), cursor()) }

    /** Enrolls a phone the operator has just approved, returning what the desktop shows as a QR. */
    suspend fun enroll(deviceId: String, deviceName: String): CalendarEnrollment? = lock.withLock {
        withContext(io) {
            try {
                val desktopToken = withClientKey { ensureRegistered() }
                val current = settings()
                val deviceToken = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))
                val nameBox = sealing().let { s ->
                    val clean = deviceName.take(NAME_CHARS)
                    if (clean.isBlank()) "" else s.sealText(clean, deviceId)
                }
                client().enrollDevice(desktopToken, deviceId, EnrollRequest(tokenHash = sha256Hex(deviceToken), nameBox = nameBox))
                CalendarEnrollment(
                    relayUrl = current.relayUrl,
                    instanceId = current.instanceId,
                    deviceToken = deviceToken,
                    instanceKey = current.instanceKey,
                )
            } catch (e: RelayFailure) {
                _status.value = failure(e)
                null
            }
        }
    }

    /** Registers with the relay when sync is on and this desktop never has; nothing to do otherwise. */
    suspend fun registerIfNeeded(): Boolean = lock.withLock {
        if (!settings().enabled || settings().isPaired) return@withLock settings().isPaired
        withContext(io) {
            try {
                withClientKey { ensureRegistered() }
                _status.value = CalendarSyncStatus.Synced(Instant.now().toString(), SyncOutcome(0L, 0, 0, 0))
                true
            } catch (e: RelayFailure) {
                _status.value = failure(e)
                false
            }
        }
    }

    suspend fun revokeDevice(deviceId: String) = lock.withLock {
        withContext(io) {
            runCatching { client().revokeDevice(token(), deviceId) }
                .onSuccess { _devices.value = _devices.value.filterNot { it.id == deviceId } }
                .onFailure { if (it is RelayFailure) _status.value = failure(it) }
        }
    }

    /** Forgets the pairing on this side; the next enrollment registers a fresh instance with a fresh key. */
    fun unpair() {
        saveSettings(settings().copy(instanceId = "", desktopToken = "", instanceKey = "", cursor = 0L, lastSyncAt = ""))
        _status.value = CalendarSyncStatus.Unpaired
    }

    private suspend fun round(work: (SyncCoordinator) -> SyncOutcome): Boolean = lock.withLock {
        if (!settings().isPaired) {
            _status.value = CalendarSyncStatus.Unpaired
            return@withLock false
        }
        _status.value = CalendarSyncStatus.Syncing
        withContext(io) {
            try {
                val outcome = withClientKey { work(coordinator()) }
                val at = Instant.now().toString()
                saveSettings(settings().copy(cursor = outcome.cursor, lastSyncAt = at))
                if (outcome.devices.isNotEmpty() || outcome.phoneChanges > 0) _devices.value = outcome.devices
                _status.value = if (outcome.otherDesktop.isNotEmpty()) {
                    CalendarSyncStatus.OtherDesktop(outcome.otherDesktop)
                } else {
                    CalendarSyncStatus.Synced(at, outcome)
                }
                true
            } catch (e: RelayFailure) {
                _status.value = failure(e)
                false
            }
        }
    }

    /** Runs [block] with a client key, fetching one first if none is cached and once more if the relay refuses it. */
    private fun <T> withClientKey(block: () -> T): T {
        if (settings().clientKey.isEmpty()) refreshClientKey()
        return try {
            block()
        } catch (_: RelayFailure.ClientKey) {
            if (!refreshClientKey()) throw RelayFailure.ClientKey()
            block()
        }
    }

    private fun refreshClientKey(): Boolean {
        val key = fetchClientKey(CalendarSyncSettings.CLIENT_KEY_URL, transport) ?: return false
        saveSettings(settings().copy(clientKey = key))
        return true
    }

    private fun failure(e: RelayFailure): CalendarSyncStatus = when (e) {
        is RelayFailure.Unauthorized -> CalendarSyncStatus.Unauthorized
        else -> CalendarSyncStatus.Failed(e.message.orEmpty())
    }

    /** Registers with a fresh instance id, trying again once if the relay already knows the id. */
    private fun ensureRegistered(): String {
        val current = settings()
        if (current.isPaired) return current.desktopToken
        var instanceId = current.instanceId.ifBlank { UUID.randomUUID().toString() }
        val installId = current.installId.ifBlank { UUID.randomUUID().toString() }
        val key = Envelope.encodeKey(Envelope.newKey())
        repeat(REGISTER_ATTEMPTS) {
            try {
                val token = RelayClient(current.relayUrl, instanceId, installId, transport, settings().clientKey).register()
                saveSettings(
                    current.copy(instanceId = instanceId, desktopToken = token, instanceKey = key, installId = installId, cursor = 0L),
                )
                return token
            } catch (_: RelayFailure.Taken) {
                instanceId = UUID.randomUUID().toString()
            }
        }
        throw RelayFailure.Taken()
    }

    private fun client(): RelayClient {
        val current = settings()
        return RelayClient(current.relayUrl, current.instanceId, installId(), transport, current.clientKey)
    }

    private fun coordinator(): SyncCoordinator = SyncCoordinator(
        store = CalendarStore(folder),
        presetStore = PresetStore(folder),
        client = client(),
        sealing = sealing(),
        installId = installId(),
        songs = ::songs,
        onSaved = watcher::savedHere,
    )

    private fun sealing(): Sealing {
        val current = settings()
        val key = Envelope.decodeKey(current.instanceKey) ?: throw RelayFailure.Unauthorized()
        return Sealing(Envelope(key), current.instanceId)
    }

    private fun installId(): String {
        val current = settings()
        if (current.installId.isNotBlank()) return current.installId
        val fresh = UUID.randomUUID().toString()
        saveSettings(current.copy(installId = fresh))
        return fresh
    }

    private fun token(): String = settings().desktopToken
    private fun cursor(): Long = settings().cursor

    /** The library, read once per process: a phone names songs by id and title, and both come from here. */
    private fun songs(): List<SongItem> {
        songCache?.let { return it }
        val loaded = songFolder?.let { runCatching { SongLibrary(it).load() }.getOrDefault(emptyList()) }.orEmpty()
        songCache = loaded
        return loaded
    }

    private companion object {
        val random = SecureRandom()
        const val TOKEN_BYTES = 32
        const val NAME_CHARS = 120
        fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        const val STARTUP_TIMEOUT_MS = 5_000L
        const val PULL_INTERVAL_MS = 3 * 60 * 1_000L
        const val REGISTER_ATTEMPTS = 2
    }
}

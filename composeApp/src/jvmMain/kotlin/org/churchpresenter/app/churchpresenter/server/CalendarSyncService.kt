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
import org.churchpresenter.calendar.sync.HttpRelayTransport
import org.churchpresenter.calendar.sync.EnrollRequest
import org.churchpresenter.calendar.sync.RelayFailure
import org.churchpresenter.calendar.sync.RelayTransport
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.calendar.sync.SyncCoordinator
import org.churchpresenter.calendar.sync.SyncOutcome
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEventStore
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
import java.util.UUID
import java.time.Instant
import kotlin.coroutines.coroutineContext

/** What a newly enrolled phone is handed, as the QR the desktop shows -- after an approval, or as an invite. */
data class CalendarEnrollment(
    val relayUrl: String,
    val instanceId: String,
    val deviceId: String,
    val deviceToken: String,
    val instanceKey: String,
) {
    val qrContent: String
        get() = "churchpresenter://calendar-enroll?relay=${relayUrl.trimEnd('/')}" +
            "&instance=$instanceId&device=$deviceId&token=$deviceToken&key=$instanceKey"
}

/** What an invite came to: the QR to scan, or why there is none. */
sealed class CalendarInvite {
    data class Ready(val enrollment: CalendarEnrollment) : CalendarInvite()
    data class Failed(val status: CalendarSyncStatus) : CalendarInvite()
}

/** An enrollment as an invite, or -- when there is none -- the service's status as the reason. */
fun CalendarEnrollment?.asInvite(sync: CalendarSyncService): CalendarInvite =
    if (this != null) CalendarInvite.Ready(this) else CalendarInvite.Failed(sync.status.value)

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
    /** How long a song usually runs here -- the app's duration log -- for the catalog the phones plan with. */
    private val typicalSeconds: (SongItem) -> Int? = { null },
    private val usage: UsageEventStore = UsageEvents,
    private val endpoints: RelayEndpoints = RelayEndpoints.BUILT_IN,
) {
    private val _status = MutableStateFlow<CalendarSyncStatus>(CalendarSyncStatus.Off)
    val status: StateFlow<CalendarSyncStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    private val watcher = CalendarFileWatcher(folder, io)
    private val lock = Mutex()
    private val relay =
        CalendarRelayAccess(
            folder, songFolder, settings, saveSettings, transport, watcher::savedHere, typicalSeconds, endpoints,
        )

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

    /**
     * Keeps syncing until cancelled: pushes every local save, pulls on a timer while the app is
     * open, and keeps the songbooks on the relay current. The timer also registers a desktop that
     * could not be registered when sync was switched on -- the relay unreachable for that first
     * second -- so a bad moment is not permanent.
     */
    suspend fun run(pullIntervalMs: Long = PULL_INTERVAL_MS) = coroutineScope {
        if (!settings().enabled) return@coroutineScope
        launch { watcher.run(onChanged = { round { it.pushLocal(relay.token, relay.cursor) } }) }
        launch {
            // The songbooks straight away -- the startup round did not wait for them -- then with
            // every pull; a library that has not changed costs a hash and nothing else.
            pushCatalog()
            while (coroutineContext.isActive) {
                delay(pullIntervalMs)
                if (registerIfNeeded()) {
                    syncNow()
                    pushCatalog()
                }
            }
        }
    }

    /** The songbooks the phones plan with, written only where they changed; never in a round's way. */
    suspend fun pushCatalog(): Int {
        if (!settings().enabled || !settings().isPaired) return 0
        return withContext(io) {
            // Background work over a library this code did not write: whatever it throws is a
            // report, never the app going down. A relay failure is shown where the calendar's own
            // are; the next good round clears it.
            runCatching { relay.withClientKey { relay.catalog().push(relay.token) } }.getOrElse { e ->
                if (e is RelayFailure) {
                    _status.value = failure(e)
                } else {
                    CrashReporter.reportException(e, "Pushing songbooks")
                }
                0
            }
        }
    }

    suspend fun syncNow(): Boolean = round { it.sync(relay.token, relay.cursor) }

    /** Enrolls a phone the operator has just approved, returning what the desktop shows as a QR. */
    suspend fun enroll(deviceId: String, deviceName: String): CalendarEnrollment? = lock.withLock {
        if (!settings().enabled) return@withLock null
        withContext(io) {
            try {
                val desktopToken = relay.withClientKey { relay.ensureRegistered() }
                val current = settings()
                val deviceToken = CalendarRelayAccess.newDeviceToken()
                val nameBox = relay.sealing().let { s ->
                    val clean = deviceName.take(NAME_CHARS)
                    if (clean.isBlank()) "" else s.sealText(clean, deviceId)
                }
                val request = EnrollRequest(tokenHash = CalendarRelayAccess.sha256Hex(deviceToken), nameBox = nameBox)
                relay.client().enrollDevice(desktopToken, deviceId, request)
                CalendarEnrollment(
                    relayUrl = relay.relayUrl(),
                    instanceId = current.instanceId,
                    deviceId = deviceId,
                    deviceToken = deviceToken,
                    instanceKey = current.instanceKey,
                )
            } catch (e: RelayFailure) {
                _status.value = failure(e)
                null
            }
        }
    }

    /**
     * An invite: a fresh device, enrolled now with no name, as a QR anyone may scan wherever they
     * are. The relay forgets a device that never checks in within its first minutes, so a code that
     * was photographed or never scanned dies by itself; the phone that does scan it names itself.
     */
    suspend fun invitePhone(): CalendarEnrollment? =
        enroll(UUID.randomUUID().toString(), deviceName = "")?.also { usage.record(UsageEvent.CALENDAR_PHONE_INVITED) }

    /** Registers with the relay when sync is on and this desktop never has; nothing to do otherwise. */
    suspend fun registerIfNeeded(): Boolean = lock.withLock {
        if (!settings().enabled || settings().isPaired) return@withLock settings().isPaired
        withContext(io) {
            try {
                relay.withClientKey { relay.ensureRegistered() }
                _status.value = CalendarSyncStatus.Synced(Instant.now().toString(), SyncOutcome(0L, 0, 0, 0))
                true
            } catch (e: RelayFailure) {
                _status.value = failure(e)
                false
            }
        }
    }

    suspend fun revokeDevice(deviceId: String) = lock.withLock {
        if (!settings().enabled) return@withLock
        withContext(io) {
            runCatching { relay.client().revokeDevice(relay.token, deviceId) }
                .onSuccess { _devices.value = _devices.value.filterNot { it.id == deviceId } }
                .onFailure { if (it is RelayFailure) _status.value = failure(it) }
        }
    }

    /** Forgets the pairing on this side; the next enrollment registers a fresh instance with a fresh key. */
    fun unpair() {
        saveSettings(
            settings().copy(instanceId = "", desktopToken = "", instanceKey = "", cursor = 0L, lastSyncAt = ""),
        )
        _status.value = CalendarSyncStatus.Unpaired
    }

    private suspend fun round(work: (SyncCoordinator) -> SyncOutcome): Boolean = lock.withLock {
        if (!settings().enabled) {
            _status.value = CalendarSyncStatus.Off
            return@withLock false
        }
        if (!settings().isPaired) {
            _status.value = CalendarSyncStatus.Unpaired
            return@withLock false
        }
        _status.value = CalendarSyncStatus.Syncing
        withContext(io) {
            try {
                val outcome = relay.withClientKey { work(relay.coordinator()) }
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

    private companion object {
        const val NAME_CHARS = 120
        const val STARTUP_TIMEOUT_MS = 5_000L
        const val PULL_INTERVAL_MS = 5 * 60 * 1_000L
    }
}

/** The status a relay failure reads as: the token refused is its own state, anything else a message. */
private fun failure(e: RelayFailure): CalendarSyncStatus = when (e) {
    is RelayFailure.Unauthorized -> CalendarSyncStatus.Unauthorized
    else -> CalendarSyncStatus.Failed(e.message.orEmpty())
}

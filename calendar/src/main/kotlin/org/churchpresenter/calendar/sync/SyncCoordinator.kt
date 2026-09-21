package org.churchpresenter.calendar.sync

import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.storedInstant
import org.churchpresenter.core.models.songs.SongItem
import java.time.Instant
import java.time.LocalDate

/** What one round with the relay did, for the settings screen and the calendar window's banner. */
class SyncOutcome(
    /** The relay revision everything is now in step with; the next pull starts here. */
    val cursor: Long,
    /** Services that arrived changed by a phone since the last round. */
    val phoneChanges: Int,
    /** Rows kept but not matched to anything here, across every service that arrived. */
    val unresolvedRows: Int,
    /** Rows discarded as unacceptable, across every service that arrived. */
    val droppedRows: Int,
    /** The other desktop that has pushed to this instance, if one has; ours was held back. */
    val otherDesktop: String = "",
    /** The phones paired to this instance, as the relay listed them on this round, names opened. */
    val devices: List<PairedDevice> = emptyList(),
    /** Records that did not open under this instance's key. */
    val unreadableRecords: Int = 0,
)

/** A paired phone as the settings card lists it. */
class PairedDevice(val id: String, val name: String, val pairedAt: String, val lastSeen: String)

/**
 * One round of keeping `calendar.json` and the relay in step: pull what changed, rebuild it through
 * the [Resolver], merge it into the local file as a shared folder would, save, push the merged
 * picture back.
 *
 * [onSaved] is called after every write of `calendar.json` — see `CalendarFileWatcher.savedHere`.
 */
class SyncCoordinator(
    private val store: CalendarStore,
    private val presetStore: PresetStore?,
    private val client: RelayClient,
    private val sealing: Sealing,
    private val installId: String,
    private val songs: () -> List<SongItem>,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> Instant = Instant::now,
    private val onSaved: () -> Unit = {},
) {

    /** Pull, merge, save and push. Retries the push when the relay moved on mid-round. */
    fun sync(token: String, cursor: Long): SyncOutcome {
        var since = cursor
        repeat(MAX_ROUNDS) {
            val changes = client.changes(token, since)
            val merged = absorb(changes)
            val devices = changes.devices.map {
                PairedDevice(it.id, sealing.openDeviceName(it), it.pairedAt, it.lastSeen)
            }
            if (changes.lastDesktopInstall.isNotEmpty() && changes.lastDesktopInstall != installId) {
                return merged.outcome(changes.rev, devices, otherDesktop = changes.lastDesktopInstall)
            }
            try {
                val rev = push(token, changes.rev, merged.document)
                return merged.outcome(rev, devices)
            } catch (_: RelayFailure.Conflict) {
                since = changes.rev
            }
        }
        throw RelayFailure.Conflict()
    }

    /** Pushes the local file as it stands; a phone edit in the meantime turns this into a full [sync]. */
    fun pushLocal(token: String, cursor: Long): SyncOutcome = try {
        SyncOutcome(
            cursor = push(token, cursor, store.load().document),
            phoneChanges = 0,
            unresolvedRows = 0,
            droppedRows = 0,
        )
    } catch (_: RelayFailure.Conflict) {
        sync(token, cursor)
    }

    private fun push(token: String, ifRev: Long, document: CalendarDocument): Long {
        val presets = presetStore?.load()?.presets.orEmpty()
        val state = StateRequest(
            records = Projection.services(document, today()).map(sealing::seal),
            tombstones = Projection.tombstones(document),
            presetsBox = sealing.sealPresets(PresetIndex(Projection.presets(presets))),
        )
        return client.putState(token, state, ifRev)
    }

    /** What the relay sent, rebuilt and merged into the local file. */
    private fun absorb(changes: ChangesResponse): Absorbed {
        val local = store.load().document
        val resolver = Resolver(songs(), presetStore?.load()?.presets.orEmpty(), today())
        var phoneChanges = 0
        var unresolved = 0
        var dropped = 0
        var unreadable = 0
        val services = changes.records.mapNotNull { record ->
            val remote = sealing.open(record)
            if (remote == null) {
                unreadable++
                return@mapNotNull null
            }
            val resolved = resolver.resolve(remote, local.serviceById(remote.id)) ?: return@mapNotNull null
            if (remote.updatedBy != Projection.DESKTOP) phoneChanges++
            unresolved += resolved.unresolved.size
            dropped += resolved.dropped
            resolved.service
        }
        val tombstones = changes.tombstones
            .filter { Sanitize.isId(it.id) }
            .associate { tombstone ->
                val deletedAt = runCatching { Instant.parse(tombstone.deletedAt) }.getOrNull() ?: now()
                tombstone.id to storedInstant(deletedAt)
            }
        val remote = CalendarDocument(services = services, deletedServices = tombstones)
        val merged = local.mergedWith(remote, now())
        if (merged != local) {
            store.save(merged)
            onSaved()
        }
        return Absorbed(merged, phoneChanges, unresolved, dropped, unreadable)
    }

    private class Absorbed(
        val document: CalendarDocument,
        val phoneChanges: Int,
        val unresolved: Int,
        val dropped: Int,
        val unreadable: Int,
    ) {
        fun outcome(cursor: Long, devices: List<PairedDevice>, otherDesktop: String = "") =
            SyncOutcome(cursor, phoneChanges, unresolved, dropped, otherDesktop, devices, unreadable)
    }

    private companion object {
        const val MAX_ROUNDS = 3
    }
}

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
            val (changes, merged) = pull(token, since)
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

    /**
     * Every page of what changed since [since], each merged as it arrives. The last page is returned:
     * its revision is the one to push against, and it carries the device list.
     */
    private fun pull(token: String, since: Long): Pair<ChangesResponse, Absorbed> {
        var changes = client.changes(token, since)
        var merged = absorb(changes)
        var pages = 1
        while (changes.more) {
            if (pages >= MAX_PAGES) throw RelayFailure.Rejected(0, "relay has more changes than one round will read")
            changes = client.changes(token, changes.rev)
            merged = merged.plus(absorb(changes))
            pages++
        }
        return changes to merged
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
            records = (Projection.services(document, today()) + Projection.deletions(document)).map(sealing::seal),
            presetsBox = sealing.sealPresets(PresetIndex(Projection.presets(presets))),
        )
        return client.putState(token, state, ifRev)
    }

    /** What the relay sent, rebuilt and merged into the local file. */
    private fun absorb(changes: ChangesResponse): Absorbed {
        val local = store.load().document
        val resolver = Resolver(songs(), presetStore?.load()?.presets.orEmpty(), today(), now())
        val deletions = HashMap<String, RemoteService>()
        var phoneChanges = 0
        var unresolved = 0
        var dropped = 0
        var unreadable = 0
        val services = changes.records.mapNotNull { record ->
            // The songbooks are ours (see CatalogSync); they come back only because a pull is
            // everything since the cursor, and the cursor is never moved past them on purpose.
            if (record.id.startsWith(CATALOG_PREFIX)) return@mapNotNull null
            val remote = sealing.open(record)
            if (remote == null) {
                unreadable++
                return@mapNotNull null
            }
            if (remote.deleted) {
                if (remote.version >= (deletions[remote.id]?.version ?: -1L)) deletions[remote.id] = remote
                return@mapNotNull null
            }
            val resolved = resolver.resolve(remote, local.serviceById(remote.id)) ?: return@mapNotNull null
            if (remote.updatedBy != Projection.DESKTOP) phoneChanges++
            unresolved += resolved.unresolved.size
            dropped += resolved.dropped
            resolved.service
        }
        // The relay's own plaintext tombstones are ignored: a deletion is believed only when it
        // arrives sealed, as a record that opens under this instance's key.
        val remote = CalendarDocument(
            services = services,
            deletedServices = deletions.mapValues { (_, deletion) ->
                resolver.editedAt(deletion.editedAt).ifEmpty { storedInstant(now()) }
            },
            deletedVersions = deletions.mapValues { (_, d) -> d.version.coerceAtLeast(0L) },
        )
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

        /** This page's counts added to the earlier ones; the document is the later, fuller merge. */
        fun plus(next: Absorbed) = Absorbed(
            next.document,
            phoneChanges + next.phoneChanges,
            unresolved + next.unresolved,
            dropped + next.dropped,
            unreadable + next.unreadable,
        )
    }

    private companion object {
        const val MAX_ROUNDS = 3

        /** 2,000 rows a page: far beyond the 5,500 the relay holds, so this only stops a runaway. */
        const val MAX_PAGES = 50
    }
}

package org.churchpresenter.calendar.sync

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.storedInstant
import org.churchpresenter.core.models.songs.SongItem
import java.security.MessageDigest
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
    /** What the relay took this computer for on this round -- see [RemoteRole]; empty from an older relay. */
    val role: String = "",
    /** When the main computer was last heard from, on a joined computer's round. */
    val mainLastSeen: String = "",
    /** The computers that joined this instance, on the main computer's round. */
    val computers: List<JoinedComputer> = emptyList(),
    /** Services a joined computer sent; ones the relay had moved on from wait for the next round. */
    val sentServices: Int = 0,
)

/** A paired phone as the settings card lists it. */
class PairedDevice(val id: String, val name: String, val pairedAt: String, val lastSeen: String)

/** A follower or viewer computer as the main computer's settings card lists it. */
class JoinedComputer(val id: String, val role: String, val name: String, val pairedAt: String, val lastSeen: String)

/** What this computer is to the instance it syncs with. */
enum class SyncRole {
    /** The source of truth: pushes the whole picture. */
    MAIN,

    /** Joined another's calendar: sends the services it changed, as a phone does. */
    FOLLOWER,

    /** Joined another's calendar to read it: never writes. */
    VIEWER,
}

/**
 * One round of keeping `calendar.json` and the relay in step: pull what changed, rebuild it through
 * the [Resolver], merge it into the local file as a shared folder would, save, push the merged
 * picture back.
 *
 * The push is skipped when it would only repeat the last one: nothing arrived from the relay and
 * the unsealed picture hashes the same as what [pushed] remembers for this instance. A timer round
 * then costs the relay one read and no writes. It still pushes at least once a day, so any
 * drift between the two sides heals and the relay keeps seeing this desktop.
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
    /** What was last pushed; without one, every round pushes. */
    private val pushed: PushedStateStore? = null,
    private val role: SyncRole = SyncRole.MAIN,
    /** A joined computer's copies of what the relay holds -- see [FollowerPush]. */
    private val shadow: FollowerShadowStore? = null,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false; classDiscriminator = "type" }

    /** Pull, merge, save and push. Retries the push when the relay moved on mid-round. */
    fun sync(token: String, cursor: Long): SyncOutcome {
        if (role != SyncRole.MAIN) return syncJoined(token, cursor)
        var since = cursor
        repeat(MAX_ROUNDS) {
            val (changes, merged) = pull(token, since)
            val devices = changes.devices.map {
                PairedDevice(it.id, sealing.openDeviceName(it), it.pairedAt, it.lastSeen)
            }
            val computers = changes.desktops.map {
                JoinedComputer(it.id, it.role, sealing.openName(it.nameBox, it.id), it.pairedAt, it.lastSeen)
            }
            if (changes.lastDesktopInstall.isNotEmpty() && changes.lastDesktopInstall != installId) {
                val other = changes.lastDesktopInstall
                return merged.outcome(changes, changes.rev, devices, computers, otherDesktop = other)
            }
            try {
                val rev = push(token, changes.rev, merged.document, relayChanged = merged.arrived > 0)
                return merged.outcome(changes, rev, devices, computers)
            } catch (_: RelayFailure.Conflict) {
                since = changes.rev
            }
        }
        throw RelayFailure.Conflict()
    }

    /**
     * A joined computer's round: pull and merge as the main computer does, then -- a follower --
     * write each service it changed as a record of its own. Never the whole picture, never the
     * songbooks or the presets: those are the main computer's. The cursor stays at what was pulled,
     * so the next round reads this computer's own writes back with the revisions they got.
     */
    private fun syncJoined(token: String, cursor: Long): SyncOutcome {
        val (changes, merged) = pull(token, cursor)
        val sends = role == SyncRole.FOLLOWER && changes.role != RemoteRole.MAIN
        val sent = if (sends) sendChanged(token, merged.document) else 0
        return merged.outcome(changes, changes.rev, emptyList(), emptyList(), sent = sent)
    }

    /** Writes every service this follower changed; one the relay moved on from waits for the next round. */
    private fun sendChanged(token: String, document: CalendarDocument): Int {
        val store = shadow ?: return 0
        val copies = HashMap(store.load(sealing.instanceId))
        val outgoing = FollowerPush.outgoing(document, copies, resolver(), today(), now())
        var sent = 0
        for (item in outgoing) {
            try {
                val rev = client.putRecord(token, sealing.seal(item.service), item.ifRev)
                copies[item.service.id] = item.service.copy(rev = rev)
                sent++
            } catch (_: RelayFailure.Conflict) {
                // Somebody else wrote this service since our copy; the next pull merges theirs first.
            }
        }
        if (sent > 0) store.save(sealing.instanceId, copies)
        return sent
    }

    private fun resolver(): Resolver = Resolver(
        songs(),
        presetStore?.load()?.presets.orEmpty(),
        today(),
        now(),
        keepForeignRows = role != SyncRole.MAIN,
    )

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
    fun pushLocal(token: String, cursor: Long): SyncOutcome {
        if (role != SyncRole.MAIN) return syncJoined(token, cursor)
        return try {
            SyncOutcome(
                cursor = push(token, cursor, store.load().document, relayChanged = false),
                phoneChanges = 0,
                unresolvedRows = 0,
                droppedRows = 0,
            )
        } catch (_: RelayFailure.Conflict) {
            sync(token, cursor)
        }
    }

    /**
     * The picture as it stands, unless it is what was last pushed and nothing has come from the
     * relay since — then the relay already holds it, and [ifRev] is where it stands.
     */
    private fun push(token: String, ifRev: Long, document: CalendarDocument, relayChanged: Boolean): Long {
        val records = Projection.services(document, today()) + Projection.deletions(document)
        val presets = PresetIndex(Projection.presets(presetStore?.load()?.presets.orEmpty()))
        val hash = fingerprint(records, presets)
        val at = now()
        if (!relayChanged && alreadyPushed(hash, at)) return ifRev
        val state = StateRequest(
            records = records.map(sealing::seal),
            presetsBox = sealing.sealPresets(presets),
        )
        val rev = client.putState(token, state, ifRev)
        pushed?.save(PushedState(instanceId = sealing.instanceId, hash = hash, at = at.toString()))
        return rev
    }

    /** Whether [hash] went to this instance within the last day. */
    private fun alreadyPushed(hash: String, at: Instant): Boolean {
        val last = pushed?.load() ?: return false
        if (last.instanceId != sealing.instanceId || last.hash != hash) return false
        val lastAt = runCatching { Instant.parse(last.at) }.getOrNull() ?: return false
        return lastAt.plusMillis(REPUSH_AFTER_MS) > at
    }

    /** A hash of the unsealed state: equal exactly when a push would carry the same picture. */
    private fun fingerprint(records: List<RemoteService>, presets: PresetIndex): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(json.encodeToString(ListSerializer(RemoteService.serializer()), records).toByteArray())
        digest.update(json.encodeToString(PresetIndex.serializer(), presets).toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** What the relay sent, rebuilt and merged into the local file. */
    private fun absorb(changes: ChangesResponse): Absorbed {
        val local = store.load().document
        val resolver = resolver()
        val copies = if (role != SyncRole.MAIN) shadow?.load(sealing.instanceId)?.let(::HashMap) else null
        val deletions = HashMap<String, RemoteService>()
        var phoneChanges = 0
        var unresolved = 0
        var dropped = 0
        var unreadable = 0
        // Everything on the relay that is not ours to ignore, whether or not it opens or merges:
        // the relay no longer holds what this desktop last pushed, so the next push is not a repeat.
        val arrived = changes.records.count { !it.id.startsWith(CATALOG_PREFIX) } + changes.tombstones.size
        val services = changes.records.mapNotNull { record ->
            // The songbooks are ours (see CatalogSync); they come back only because a pull is
            // everything since the cursor, and the cursor is never moved past them on purpose.
            if (record.id.startsWith(CATALOG_PREFIX)) return@mapNotNull null
            val remote = sealing.open(record)
            if (remote == null) {
                unreadable++
                return@mapNotNull null
            }
            copies?.put(remote.id, remote)
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
        if (copies != null && arrived > 0) shadow?.save(sealing.instanceId, copies)
        return Absorbed(merged, phoneChanges, unresolved, dropped, unreadable, arrived)
    }

    private class Absorbed(
        val document: CalendarDocument,
        val phoneChanges: Int,
        val unresolved: Int,
        val dropped: Int,
        val unreadable: Int,
        /** Records and tombstones the pull carried, songbooks aside. */
        val arrived: Int,
    ) {
        fun outcome(
            changes: ChangesResponse,
            cursor: Long,
            devices: List<PairedDevice>,
            computers: List<JoinedComputer>,
            otherDesktop: String = "",
            sent: Int = 0,
        ) = SyncOutcome(
            cursor, phoneChanges, unresolved, dropped, otherDesktop, devices, unreadable,
            role = changes.role, mainLastSeen = changes.mainLastSeen, computers = computers, sentServices = sent,
        )

        /** This page's counts added to the earlier ones; the document is the later, fuller merge. */
        fun plus(next: Absorbed) = Absorbed(
            next.document,
            phoneChanges + next.phoneChanges,
            unresolved + next.unresolved,
            dropped + next.dropped,
            unreadable + next.unreadable,
            arrived + next.arrived,
        )
    }

    private companion object {
        const val MAX_ROUNDS = 3

        /** 2,000 rows a page: far beyond the 5,500 the relay holds, so this only stops a runaway. */
        const val MAX_PAGES = 50

        /** How long an unchanged picture goes without being pushed again. */
        const val REPUSH_AFTER_MS = 24L * 60L * 60L * 1_000L
    }
}

package org.churchpresenter.calendar.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What a computer that joined another's calendar last received of each service, as the relay held
 * it: the rows exactly as they travelled, and the record's revision to write against.
 */
@Serializable
data class FollowerShadow(val instanceId: String = "", val services: Map<String, RemoteService> = emptyMap())

/** `follower-sync.json` beside `calendar.json`: the joined computer's memory of the relay's copies. */
class FollowerShadowStore(private val folder: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
    internal val file: File = File(folder, FOLLOWER_SYNC_FILE)

    /** The copies for [instanceId]; none when the file is missing, unreadable, or for another instance. */
    fun load(instanceId: String): Map<String, RemoteService> {
        if (!file.isFile) return emptyMap()
        val shadow = runCatching { json.decodeFromString(FollowerShadow.serializer(), file.readText()) }.getOrNull()
        return if (shadow?.instanceId == instanceId) shadow.services else emptyMap()
    }

    fun save(instanceId: String, services: Map<String, RemoteService>) {
        folder.mkdirs()
        file.writeTextAtomically(json.encodeToString(FollowerShadow.serializer(), FollowerShadow(instanceId, services)))
    }

    private companion object {
        const val FOLLOWER_SYNC_FILE = "follower-sync.json"
    }
}

/** One service a follower sends, and the record revision its copy was edited from (0 for a new one). */
class OutgoingService(val service: RemoteService, val ifRev: Long)

/**
 * What a follower computer writes to the relay: each service it changed since the copy it last
 * received, one record at a time, the way a phone does. The main computer takes these in and stays
 * the source of truth.
 *
 * A follower rebuilds rows against its own library, and the main computer's videos, pictures,
 * presets and songs it does not have come out as flagged placeholders. A row the follower did not
 * change therefore goes back exactly as it arrived -- never as the placeholder -- so the main
 * computer never loses a row because a follower could not open it.
 */
object FollowerPush {

    fun outgoing(
        document: CalendarDocument,
        shadow: Map<String, RemoteService>,
        resolver: Resolver,
        today: LocalDate,
        now: Instant,
    ): List<OutgoingService> {
        val earliest = today.minusDays(WireLimits.RETENTION_DAYS).toString()
        val changed = document.services
            .filter { it.date >= earliest }
            .take(WireLimits.SERVICES_PER_PUSH)
            .mapNotNull { service -> changedService(service, shadow[service.id], resolver, now) }
        return changed + deletions(document, shadow)
    }

    private fun changedService(
        service: PlannedService,
        base: RemoteService?,
        resolver: Resolver,
        now: Instant,
    ): OutgoingService? {
        val baseResolved = base?.takeUnless { it.deleted }?.let { resolver.resolve(it, service)?.service }
        val projected = Projection.service(service)
        if (baseResolved != null && stamped(projected) == stamped(Projection.service(baseResolved))) return null
        val rows = if (base != null && baseResolved != null) {
            originalRows(projected.rows, service, base, baseResolved)
        } else {
            projected.rows
        }
        val version = maxOf(service.version, (base?.version ?: -1L) + 1)
        return OutgoingService(
            service = projected.copy(
                rows = rows,
                version = version,
                editedAt = service.updatedAt.ifEmpty { now.toString() },
                updatedAt = "",
                updatedBy = "",
            ),
            ifRev = base?.rev ?: 0L,
        )
    }

    /**
     * Each row as it should travel: the copy that arrived when this computer left it as it was, or
     * when this computer only ever had a placeholder for it; the follower's own version otherwise.
     */
    private fun originalRows(
        rows: List<RemoteRow>,
        service: PlannedService,
        base: RemoteService,
        baseResolved: PlannedService,
    ): List<RemoteRow> {
        val arrived = base.rows.associateBy { it.id }
        val unchanged = baseResolved.items.associate { it.id to Projection.row(it) }
        val local = service.items.associateBy { it.id }
        return rows.map { row ->
            val original = arrived[row.id] ?: return@map row
            val placeholder = (original is RemoteRow.Ref || original is RemoteRow.Preset) &&
                local[row.id] is ScheduleItem.MinistryItem
            if (placeholder || unchanged[row.id] == row) original else row
        }
    }

    /** A deletion for each service this computer removed that the relay still holds. */
    private fun deletions(document: CalendarDocument, shadow: Map<String, RemoteService>): List<OutgoingService> {
        val live = document.services.mapTo(HashSet()) { it.id }
        return document.deletedServices.entries
            .filter { (id, _) -> id !in live && shadow[id]?.deleted == false }
            .take(WireLimits.DELETIONS_PER_PUSH)
            .map { (id, at) ->
                val base = shadow.getValue(id)
                OutgoingService(
                    service = RemoteService(
                        id = id,
                        date = runCatching { Instant.parse(at).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                            .getOrDefault(base.date),
                        startTime = "",
                        name = "",
                        version = maxOf(document.deletedVersions[id] ?: 0L, base.version + 1),
                        editedAt = at,
                        deleted = true,
                    ),
                    ifRev = base.rev,
                )
            }
    }

    /** [service] without what changes on every write, so two copies compare by what they hold. */
    private fun stamped(service: RemoteService): RemoteService =
        service.copy(version = 0L, editedAt = "", updatedAt = "", updatedBy = "", rev = 0L)
}

package org.churchpresenter.calendar.sync

import kotlinx.serialization.json.Json
import java.time.LocalDate

private const val MAX_BOX_CHARS = 256 * 1024

/** Seals and opens the records the relay carries. */
class Sealing(private val envelope: Envelope, private val instanceId: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun seal(service: RemoteService): SealedRecord {
        val plain = service.copy(updatedAt = "", updatedBy = "", rev = 0L)
        val bytes = json.encodeToString(RemoteService.serializer(), plain).toByteArray()
        val keepUntil = runCatching { LocalDate.parse(service.date).plusDays(WireLimits.RETENTION_DAYS) }
            .getOrDefault(LocalDate.now().plusDays(WireLimits.RETENTION_DAYS))
        return SealedRecord(
            id = service.id,
            keepUntil = keepUntil.toString(),
            box = envelope.seal(bytes, instanceId, service.id),
        )
    }

    fun sealPresets(index: PresetIndex): String {
        val bytes = json.encodeToString(PresetIndex.serializer(), index).toByteArray()
        return envelope.seal(bytes, instanceId, PRESETS_RECORD)
    }

    /** The service inside a record, with the relay's stamps on it, or null when it does not open cleanly. */
    fun open(record: SealedRecord): RemoteService? {
        if (record.box.length > MAX_BOX_CHARS) return null
        val bytes = envelope.open(record.box, instanceId, record.id) ?: return null
        val service = try {
            json.decodeFromString(RemoteService.serializer(), bytes.decodeToString())
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (service.id != record.id) return null
        return service.copy(updatedAt = record.updatedAt, updatedBy = record.updatedBy, rev = record.rev)
    }

    /** A short text sealed under [recordId] — how a phone's name travels. */
    fun sealText(text: String, recordId: String): String = envelope.seal(text.toByteArray(), instanceId, recordId)

    /** A phone's sealed name, or empty when it does not open. */
    fun openDeviceName(device: RemoteDevice): String {
        if (device.nameBox.isEmpty() || device.nameBox.length > MAX_BOX_CHARS) return ""
        val bytes = envelope.open(device.nameBox, instanceId, device.id) ?: return ""
        return Sanitize.cleanText(bytes.decodeToString(), WireLimits.NAME_CHARS)
    }
}

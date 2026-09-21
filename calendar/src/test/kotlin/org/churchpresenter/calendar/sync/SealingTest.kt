package org.churchpresenter.calendar.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SealingTest {

    private val envelope = Envelope(ByteArray(Envelope.KEY_BYTES) { 1 })
    private val sealing = Sealing(envelope, "inst")
    private val service = RemoteService(
        id = "svc",
        date = "2026-09-27",
        startTime = "10:00",
        name = "Sunday",
        rows = listOf(RemoteRow.Song("r", "Amazing Grace", "H::1")),
        updatedAt = "should-not-travel",
        updatedBy = "phone",
        rev = 9,
    )

    @Test
    fun `a sealed record carries the id, keep-until and the relay's stamps on the way back`() {
        val record = sealing.seal(service)

        assertEquals("svc", record.id)
        assertEquals("2026-12-26", record.keepUntil)
        val opened = sealing.open(record.copy(updatedAt = "2026-09-21T00:00:00Z", updatedBy = "phone-1", rev = 12))!!
        assertEquals("Sunday", opened.name)
        assertEquals("2026-09-21T00:00:00Z", opened.updatedAt)
        assertEquals("phone-1", opened.updatedBy)
        assertEquals(12, opened.rev)
    }

    @Test
    fun `the relay's stamps inside the box are not trusted`() {
        val plain = envelope.open(sealing.seal(service).box, "inst", "svc")!!.decodeToString()

        assertEquals(false, "should-not-travel" in plain)
        assertEquals(false, "\"rev\":9" in plain)
    }

    @Test
    fun `a record whose id disagrees with its content does not open`() {
        val record = sealing.seal(service)

        assertNull(sealing.open(record.copy(id = "other")))
    }

    @Test
    fun `a box that holds something else does not open`() {
        val box = envelope.seal("[]".toByteArray(), "inst", "svc")

        assertNull(sealing.open(SealedRecord("svc", "2026-12-26", box)))
    }

    @Test
    fun `presets and names round trip`() {
        val index = PresetIndex(listOf(RemotePreset("p", "Countdown", RemoteKind.TIMER)))
        val box = sealing.sealPresets(index)
        val nameBox = sealing.sealText("Anna's iPhone", "dev-1")

        assertEquals("Anna's iPhone", sealing.openDeviceName(RemoteDevice("dev-1", nameBox)))
        assertEquals("", sealing.openDeviceName(RemoteDevice("dev-2", nameBox)))
        assertEquals("", sealing.openDeviceName(RemoteDevice("dev-1", "")))
        assertEquals(true, envelope.open(box, "inst", PRESETS_RECORD) != null)
    }

    @Test
    fun `an unreadable date still gets a keep-until`() {
        val record = sealing.seal(service.copy(date = "garbage"))

        assertEquals(10, record.keepUntil.length)
    }
}

package org.churchpresenter.app.churchpresenter.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Decoding what an ATEM switcher reports about itself: its video mode, its media pool, and its
 * keyer topology.
 *
 * This is byte-offset work against a protocol with no schema — the layouts are recorded in comments
 * as "verified against hardware", which is the only specification there is. A wrong offset does not
 * fail loudly: it names the wrong media slot, and the operator overwrites a graphic that is live in
 * another scene. A wrong frame rate silently mis-times every clip upload.
 *
 * The parsing is pure — `Map<String, List<ByteArray>>` in, data out — so all of it is reachable
 * without a switcher. Only the UDP transport that fills that map is not, and its own suite
 * (`AtemClientProtocolTest`) already covers the outbound payload builders.
 *
 * Payloads here are built from the layouts documented on each parser, so the tests double as an
 * executable copy of the format.
 */
class AtemStateParsingTest {

    private val client = AtemClient(host = "10.0.0.9")

    private fun u16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())

    /** MPfe: pool(1) pad(1) index(2) used(1) hash(16) pad(2) nameLen(1) name(n). */
    private fun stillSlot(index: Int, used: Boolean, name: String, pool: Int = 0): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val p = ByteArray(24 + nameBytes.size)
        p[0] = pool.toByte()
        u16(index).copyInto(p, 2)
        p[4] = if (used) 1 else 0
        p[23] = nameBytes.size.toByte()
        nameBytes.copyInto(p, 24)
        return p
    }

    /** MPCS: index(1) used(1) name(64, null-terminated) frames(2). */
    private fun clipSlot(index: Int, used: Boolean, name: String, frames: Int = 0): ByteArray {
        val p = ByteArray(68)
        p[0] = index.toByte()
        p[1] = if (used) 1 else 0
        name.toByteArray(Charsets.UTF_8).copyInto(p, 2)
        u16(frames).copyInto(p, 66)
        return p
    }

    // ── Still slots ─────────────────────────────────────────────────────────────

    @Test
    fun `a used still slot yields its index and name`() {
        val slots = client.parseStillSlots(mapOf("MPfe" to listOf(stillSlot(3, true, "Welcome"))))

        assertEquals(1, slots.size)
        assertEquals(3, slots[0].index)
        assertEquals("Welcome", slots[0].name)
        assertTrue(slots[0].isUsed)
    }

    @Test
    fun `an unused slot reports no name even if bytes linger in the buffer`() {
        // The name field is not cleared when a slot is freed, so trusting it would show the operator
        // a graphic that is no longer there.
        val slots = client.parseStillSlots(mapOf("MPfe" to listOf(stillSlot(1, false, "Stale"))))

        assertEquals("", slots[0].name)
        assertTrue(!slots[0].isUsed)
    }

    @Test
    fun `slots from other media pools are ignored`() {
        // Byte 0 selects the pool; only the still store (0) belongs in this list.
        val slots = client.parseStillSlots(
            mapOf("MPfe" to listOf(stillSlot(0, true, "Still"), stillSlot(1, true, "Clip", pool = 1))),
        )

        assertEquals(listOf("Still"), slots.map { it.name })
    }

    @Test
    fun `slots come back in index order however the device sent them`() {
        val slots = client.parseStillSlots(
            mapOf("MPfe" to listOf(stillSlot(5, true, "E"), stillSlot(1, true, "A"), stillSlot(3, true, "C"))),
        )

        // The dialog lists them in this order; unsorted, slot 5 would appear above slot 1.
        assertEquals(listOf(1, 3, 5), slots.map { it.index })
    }

    @Test
    fun `a truncated slot payload is skipped rather than misread`() {
        val slots = client.parseStillSlots(mapOf("MPfe" to listOf(ByteArray(10))))

        assertTrue(slots.isEmpty(), "a short payload has no readable name length or index")
    }

    @Test
    fun `a name length longer than the payload is clamped`() {
        val p = stillSlot(0, true, "Hi")
        p[23] = 99   // device claims 99 bytes of name in a payload that has 2
        val slots = client.parseStillSlots(mapOf("MPfe" to listOf(p)))

        // Clamped rather than throwing: one malformed slot must not take the whole dialog down.
        assertEquals("Hi", slots[0].name)
    }

    @Test
    fun `no MPfe at all yields no slots`() {
        assertTrue(client.parseStillSlots(emptyMap()).isEmpty())
    }

    // ── Clip slots ──────────────────────────────────────────────────────────────

    @Test
    fun `a used clip slot yields its index and null-terminated name`() {
        val slots = client.parseClipSlots(mapOf("MPCS" to listOf(clipSlot(2, true, "Opener", frames = 300))))

        assertEquals(2, slots[0].index)
        assertEquals("Opener", slots[0].name, "the name stops at the null, not at the field's end")
        assertTrue(slots[0].isUsed)
    }

    @Test
    fun `an unused clip slot reports no name`() {
        val slots = client.parseClipSlots(mapOf("MPCS" to listOf(clipSlot(0, false, "Garbage"))))

        assertEquals("", slots[0].name)
    }

    @Test
    fun `a clip payload shorter than the fixed layout is skipped`() {
        assertTrue(client.parseClipSlots(mapOf("MPCS" to listOf(ByteArray(67)))).isEmpty())
    }

    // ── Media pool settings ─────────────────────────────────────────────────────

    @Test
    fun `capacity is read per clip bank with the unassigned remainder`() {
        val p = ByteArray(10)
        u16(600).copyInto(p, 0)
        u16(500).copyInto(p, 2)
        u16(400).copyInto(p, 4)
        u16(300).copyInto(p, 6)
        u16(120).copyInto(p, 8)

        val (maxFrames, unassigned) = client.parseMediaPoolSettings(mapOf("MPSp" to listOf(p)))

        assertEquals(listOf(600, 500, 400, 300), maxFrames)
        assertEquals(120, unassigned)
    }

    @Test
    fun `a device reporting fewer clip banks yields only those`() {
        val p = ByteArray(10)
        u16(600).copyInto(p, 0)
        u16(500).copyInto(p, 2)
        // _mpl byte 1 = clip bank count
        val mpl = byteArrayOf(0, 2)

        val (maxFrames, _) = client.parseMediaPoolSettings(mapOf("MPSp" to listOf(p), "_mpl" to listOf(mpl)))

        assertEquals(listOf(600, 500), maxFrames)
    }

    @Test
    fun `firmware without MPSp leaves capacity unknown rather than guessing`() {
        val (maxFrames, unassigned) = client.parseMediaPoolSettings(emptyMap())

        // Empty means "unknown", which is what stops the dialog blocking an upload it cannot size.
        assertTrue(maxFrames.isEmpty())
        assertEquals(0, unassigned)
    }

    // ── Video mode and topology ─────────────────────────────────────────────────

    @Test
    fun `each video mode maps to its exact frame rate`() {
        // Fractional NTSC rates must not be rounded: a clip uploaded at 30 instead of 29.97 drifts.
        fun fpsFor(mode: Int) = client.parseAtemState(mapOf("VidM" to listOf(byteArrayOf(mode.toByte())))).fps

        assertEquals(25.0, fpsFor(10), "1080p25")
        assertEquals(24.0, fpsFor(9), "1080p24")
        assertEquals(50.0, fpsFor(12), "1080p50")
        assertEquals(30000.0 / 1001.0, fpsFor(11), "1080p29.97 is 30000/1001, not 30")
        assertEquals(60000.0 / 1001.0, fpsFor(13), "1080p59.94 is 60000/1001, not 60")
        assertEquals(24000.0 / 1001.0, fpsFor(8), "1080p23.98 is 24000/1001")
    }

    @Test
    fun `the video mode name comes back alongside the rate`() {
        val state = client.parseAtemState(mapOf("VidM" to listOf(byteArrayOf(10))))

        assertEquals("1080p25", state.videoMode)
    }

    @Test
    fun `an unrecognised video mode falls back rather than throwing`() {
        val state = client.parseAtemState(mapOf("VidM" to listOf(byteArrayOf(99.toByte()))))

        // A newer switcher with a mode this build predates must still connect.
        assertEquals("Unknown", state.videoMode)
        assertEquals(30.0, state.fps)
    }

    @Test
    fun `topology reports the mix effect and downstream keyer counts`() {
        // _top byte 0 = M/E buses, byte 2 = downstream keyers.
        val top = byteArrayOf(2, 0, 3)
        val state = client.parseAtemState(mapOf("_top" to listOf(top)))

        assertEquals(2, state.mixEffectCount)
        assertEquals(3, state.downstreamKeyers)
    }

    @Test
    fun `upstream keyers are reported per mix effect, in bus order`() {
        val top = byteArrayOf(2, 0, 1)
        // _MeC: byte 0 = M/E index, byte 1 = keyer count — sent out of order here on purpose.
        val meC = listOf(byteArrayOf(1, 2), byteArrayOf(0, 4))

        val state = client.parseAtemState(mapOf("_top" to listOf(top), "_MeC" to meC))

        assertEquals(listOf(4, 2), state.keyersPerMe, "indexed by M/E, not by arrival order")
    }

    @Test
    fun `a mix effect that reports no keyer count is zero rather than missing`() {
        val state = client.parseAtemState(
            mapOf("_top" to listOf(byteArrayOf(3, 0, 0)), "_MeC" to listOf(byteArrayOf(0, 4))),
        )

        // The list is indexed by M/E number, so a gap has to be filled or every later index shifts.
        assertEquals(listOf(4, 0, 0), state.keyersPerMe)
    }

    @Test
    fun `an empty state still produces a usable object`() {
        val state = client.parseAtemState(emptyMap())

        assertEquals(0, state.mixEffectCount)
        assertTrue(state.stillSlots.isEmpty())
        assertTrue(state.clipSlots.isEmpty())
        assertTrue(state.keyersPerMe.isEmpty())
    }

    // ── Command framing ─────────────────────────────────────────────────────────

    @Test
    fun `commands are split out of a packet by their length headers`() {
        // A packet is a 12-byte header then back-to-back commands: len(2) pad(2) name(4) data.
        fun cmd(name: String, data: ByteArray): ByteArray {
            val out = ByteArray(8 + data.size)
            u16(out.size).copyInto(out, 0)
            name.toByteArray(Charsets.US_ASCII).copyInto(out, 4)
            data.copyInto(out, 8)
            return out
        }
        val packet = ByteArray(12) + cmd("VidM", byteArrayOf(10)) + cmd("_top", byteArrayOf(2, 0, 3))

        val commands = client.parseAllCommands(packet)

        assertEquals(listOf("VidM", "_top"), commands.map { it.first })
        assertEquals(10, commands[0].second[0].toInt())
        assertEquals(3, commands[1].second[2].toInt())
    }

    @Test
    fun `a command claiming to run past the packet end stops the scan`() {
        val out = ByteArray(8)
        u16(999).copyInto(out, 0)   // says 999 bytes in an 8-byte tail
        "VidM".toByteArray(Charsets.US_ASCII).copyInto(out, 4)

        val commands = client.parseAllCommands(ByteArray(12) + out)

        // Truncated or corrupt UDP must not be read past its end.
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `a header-only packet yields no commands`() {
        assertTrue(client.parseAllCommands(ByteArray(12)).isEmpty())
    }

    // ── Malformed payloads the parsers still have to survive ────────────────────

    @Test
    fun `a used still slot with a zero-length name reports no name`() {
        // The device sets isUsed before the name is written during an upload. Reading a
        // zero-length name as anything but empty would slice into the hash bytes that follow.
        val slots = client.parseStillSlots(mapOf("MPfe" to listOf(stillSlot(1, used = true, name = ""))))

        assertEquals("", slots.single().name)
        assertTrue(slots.single().isUsed, "it is still an occupied slot")
    }

    @Test
    fun `a clip name filling the whole field is read to its end`() {
        // The name is null-terminated inside a fixed 64-byte field. A name that fills the field
        // has no terminator, and scanning for one must fall back to the field length rather than
        // return -1 and slice to a negative end.
        val name = "x".repeat(64)
        val slots = client.parseClipSlots(mapOf("MPCS" to listOf(clipSlot(0, used = true, name = name))))

        assertEquals(name, slots.single().name)
    }

    @Test
    fun `a media pool settings payload shorter than its layout leaves capacity unknown`() {
        // Distinct from MPSp being absent entirely (covered above): here the command arrived but
        // is truncated. Reading it would report a capacity of whatever follows in memory, and the
        // upload path sizes clips against that number.
        val (maxFrames, unassigned) = client.parseMediaPoolSettings(
            mapOf("MPSp" to listOf(ByteArray(9))),
        )

        assertTrue(maxFrames.isEmpty())
        assertEquals(0, unassigned)
    }

    @Test
    fun `a mix effect config payload too short to carry a keyer count is skipped`() {
        // _MeC is one command per M/E bus. A truncated one must not be read as "bus 0 has N
        // keyers" — the bus it describes simply keeps its default of none.
        val state = client.parseAtemState(
            mapOf(
                "_top" to listOf(byteArrayOf(1, 0, 1)),
                "_MeC" to listOf(ByteArray(1)),
            ),
        )

        assertEquals(listOf(0), state.keyersPerMe)
    }

    @Test
    fun `a command header claiming an impossible length stops the scan`() {
        // A length below the 8-byte command header cannot be advanced past — trusting it would
        // leave the offset unmoved and spin forever on the same bytes.
        val packet = ByteArray(12 + 8)
        packet[12] = 0
        packet[13] = 3   // a 3-byte command: shorter than its own header

        assertTrue(client.parseAllCommands(packet).isEmpty())
    }

    // ── The file-description payload's optional pieces ──────────────────────────

    @Test
    fun `a blank file name is written as no name at all`() {
        // Stills carry a name and clip frames do not; the caller passes null for a frame, but an
        // empty string reaches here too and must leave the name field zeroed rather than write a
        // zero-length string over it.
        val payload = client.buildFileDescriptionPayload(transferId = 7, name = "", md5 = ByteArray(16) { 9 })

        assertEquals(212, payload.size)
        assertTrue(payload.copyOfRange(2, 66).all { it.toInt() == 0 }, "the name field stays clear")
        assertTrue(payload.copyOfRange(194, 210).all { it.toInt() == 9 }, "the md5 is still written")
    }

    @Test
    fun `a file name longer than the field is truncated to fit`() {
        // The field is 64 bytes and the switcher does not bounds-check what it is sent; writing
        // past it would run into the md5 area of the same buffer.
        val payload = client.buildFileDescriptionPayload(
            transferId = 1,
            name = "n".repeat(100),
            md5 = ByteArray(16),
        )

        assertEquals(212, payload.size)
        assertTrue(payload.copyOfRange(2, 66).all { it.toInt() == 'n'.code }, "the field is filled")
        assertEquals(0, payload[66].toInt(), "and nothing is written past it")
    }

    @Test
    fun `a short md5 is written without overrunning the digest field`() {
        val payload = client.buildFileDescriptionPayload(transferId = 1, name = null, md5 = ByteArray(4) { 7 })

        assertEquals(212, payload.size)
        assertTrue(payload.copyOfRange(194, 198).all { it.toInt() == 7 })
        assertTrue(payload.copyOfRange(198, 210).all { it.toInt() == 0 })
    }
}

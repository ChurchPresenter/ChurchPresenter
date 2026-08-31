package org.churchpresenter.ndi

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val HANDLE_VALUE = 0x1234L
private const val SECOND_HANDLE_VALUE = 0x5678L
private const val FINDER_HANDLE_VALUE = 0x9abcL
private const val RECEIVER_HANDLE_VALUE = 0xdef0L

/**
 * A stand-in for the C symbols `libndi` exports.
 *
 * [NdiLibC] is an interface, which is what makes this possible: everything [JnaNdiLibrary] does
 * around those symbols — growing and reusing the native pixel buffer, populating the two ABI
 * structs, guarding a dead handle — is ordinary logic and is exercised here against a fake, without
 * an NDI Runtime on the machine. What stays uncovered is [JnaNdiLibrary.Companion.load], the one
 * call that genuinely binds a shared library.
 *
 * The structs and pointers are real JNA objects; only the library behind them is not.
 */
private class FakeNdiLibC(
    private val versionString: String? = "NDI SDK 6.0.0",
    private val supportedCpu: Boolean = true,
    private val initializes: Boolean = true,
    private val createReturns: Pointer? = Pointer(HANDLE_VALUE),
    private val findCreateReturns: Pointer? = Pointer(FINDER_HANDLE_VALUE),
    private val recvCreateReturns: Pointer? = Pointer(RECEIVER_HANDLE_VALUE),
) : NdiLibC {
    val createSettings = mutableListOf<NdiSendCreateStruct>()
    val videoFrames = mutableListOf<NdiVideoFrameStruct>()
    val destroyedSenders = mutableListOf<Long>()
    var destroyCount = 0
    var connectionsAnswer = 0
    var lastConnectionTimeout = -1

    override fun NDIlib_initialize(): Boolean = initializes
    override fun NDIlib_destroy() { destroyCount++ }
    override fun NDIlib_version(): String? = versionString
    override fun NDIlib_is_supported_CPU(): Boolean = supportedCpu

    override fun NDIlib_send_create(settings: NdiSendCreateStruct): Pointer? {
        createSettings += settings
        return createReturns
    }

    override fun NDIlib_send_destroy(sender: Pointer) {
        destroyedSenders += Pointer.nativeValue(sender)
    }

    override fun NDIlib_send_send_video_v2(sender: Pointer, frame: NdiVideoFrameStruct) {
        videoFrames += frame
    }

    override fun NDIlib_send_get_no_connections(sender: Pointer, timeoutMs: Int): Int {
        lastConnectionTimeout = timeoutMs
        return connectionsAnswer
    }

    // ── The receive half ────────────────────────────────────────────

    val findSettings = mutableListOf<NdiFindCreateStruct>()
    val recvSettings = mutableListOf<NdiRecvCreateStruct>()
    val destroyedFinders = mutableListOf<Long>()
    val destroyedReceivers = mutableListOf<Long>()
    val freedFrames = mutableListOf<NdiVideoFrameStruct>()
    var waits = 0
    var lastWaitTimeout = -1
    var lastCaptureTimeout = -1

    /** The frame type the next capture answers with, and how it fills the caller's struct. */
    var nextFrameType = FRAME_TYPE_NONE
    var fillCapturedFrame: (NdiVideoFrameStruct) -> Unit = {}

    /** The source array discovery hands back, held so its native strings stay alive. */
    private var sources: Array<NdiSourceStruct>? = null

    /** Publishes [names] as the sources a finder will report, in real native memory. */
    fun advertise(vararg names: Pair<String, String>) {
        if (names.isEmpty()) {
            sources = null
            return
        }
        @Suppress("UNCHECKED_CAST")
        val array = NdiSourceStruct().toArray(names.size) as Array<NdiSourceStruct>
        array.forEachIndexed { index, struct ->
            struct.p_ndi_name = names[index].first
            struct.p_url_address = names[index].second
            struct.write()
        }
        sources = array
    }

    override fun NDIlib_find_create_v2(settings: NdiFindCreateStruct): Pointer? {
        findSettings += settings
        return findCreateReturns
    }

    override fun NDIlib_find_destroy(finder: Pointer) {
        destroyedFinders += Pointer.nativeValue(finder)
    }

    override fun NDIlib_find_wait_for_sources(finder: Pointer, timeoutMs: Int): Boolean {
        waits++
        lastWaitTimeout = timeoutMs
        return sources != null
    }

    override fun NDIlib_find_get_current_sources(finder: Pointer, count: IntByReference): Pointer? {
        val found = sources ?: return null
        count.value = found.size
        return found.first().pointer
    }

    override fun NDIlib_recv_create_v3(settings: NdiRecvCreateStruct): Pointer? {
        recvSettings += settings
        return recvCreateReturns
    }

    override fun NDIlib_recv_destroy(receiver: Pointer) {
        destroyedReceivers += Pointer.nativeValue(receiver)
    }

    override fun NDIlib_recv_capture_v2(
        receiver: Pointer,
        video: NdiVideoFrameStruct?,
        audio: Pointer?,
        metadata: Pointer?,
        timeoutMs: Int,
    ): Int {
        lastCaptureTimeout = timeoutMs
        if (video != null && nextFrameType == FRAME_TYPE_VIDEO) fillCapturedFrame(video)
        return nextFrameType
    }

    override fun NDIlib_recv_free_video_v2(receiver: Pointer, video: NdiVideoFrameStruct) {
        freedFrames += video
    }
}

private const val FRAME_TYPE_NONE = 0
private const val FRAME_TYPE_VIDEO = 1
private const val FRAME_TYPE_AUDIO = 2
private const val HIGHEST_BANDWIDTH = 100
private const val LOWEST_BANDWIDTH = 0

/** A YUV FourCC — a real NDI format, and one this module deliberately does not read. */
private const val UYVY_FOURCC = 0x59565955

/** Native memory holding [rows] of pixel bytes at [stride], as the runtime hands a frame over. */
private fun nativeFrame(rows: List<ByteArray>, stride: Int): Memory {
    val memory = Memory((rows.size.toLong() * stride).coerceAtLeast(1))
    memory.clear()
    rows.forEachIndexed { index, row -> memory.write(index.toLong() * stride, row, 0, row.size) }
    return memory
}

private fun frame(
    bytes: ByteArray,
    width: Int,
    height: Int,
    format: NdiPixelFormat = NdiPixelFormat.BGRA,
    rateN: Int = 30_000,
    rateD: Int = 1_000,
) = NdiVideoFrame(bytes, width, height, format, rateN, rateD)

class JnaNdiLibraryTest {

    @Test
    fun `version and cpu support are passed straight through`() {
        val lib = JnaNdiLibrary(FakeNdiLibC(versionString = "NDI SDK 6.1.1"))
        assertEquals("NDI SDK 6.1.1", lib.version())
        assertTrue(lib.isSupportedCpu())
    }

    @Test
    fun `a runtime that reports no version reads as empty rather than crashing`() {
        assertEquals("", JnaNdiLibrary(FakeNdiLibC(versionString = null)).version())
    }

    @Test
    fun `initialize reports what the runtime said`() {
        assertTrue(JnaNdiLibrary(FakeNdiLibC()).initialize())
        assertTrue(!JnaNdiLibrary(FakeNdiLibC(initializes = false)).initialize())
    }

    @Test
    fun `sendCreate returns the pointer as an opaque handle`() {
        assertEquals(HANDLE_VALUE, JnaNdiLibrary(FakeNdiLibC()).sendCreate("Stage"))
    }

    @Test
    fun `a refused sendCreate is a zero handle, not a null pointer dereference`() {
        assertEquals(0L, JnaNdiLibrary(FakeNdiLibC(createReturns = null)).sendCreate("Stage"))
    }

    @Test
    fun `the create struct carries the name and clocks video but not audio`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendCreate("Stage")
        val settings = c.createSettings.single()
        assertEquals("Stage", settings.p_ndi_name)
        assertTrue(settings.clock_video)
        // Clocking audio on a sender that never sends any would pace the runtime against a stream
        // that never arrives.
        assertTrue(!settings.clock_audio)
    }

    @Test
    fun `blank groups are sent as null rather than as an empty group name`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendCreate("Stage", groups = "")
        assertEquals(null, c.createSettings.single().p_groups)
    }

    @Test
    fun `a group name is passed through`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendCreate("Stage", groups = "Sanctuary")
        assertEquals("Sanctuary", c.createSettings.single().p_groups)
    }

    @Test
    fun `clockVideo can be turned off`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendCreate("Stage", clockVideo = false)
        assertTrue(!c.createSettings.single().clock_video)
    }

    @Test
    fun `the video struct carries the dimensions, fourcc, rate and stride`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendVideo(HANDLE_VALUE, frame(ByteArray(8 * 4 * 4), 8, 4, NdiPixelFormat.BGRX, 60_000, 1_001))
        val sent = c.videoFrames.single()
        assertEquals(8, sent.xres)
        assertEquals(4, sent.yres)
        assertEquals(NdiPixelFormat.BGRX.fourCc, sent.FourCC)
        assertEquals(60_000, sent.frame_rate_N)
        assertEquals(1_001, sent.frame_rate_D)
        assertEquals(8 * 4, sent.line_stride_in_bytes)
    }

    @Test
    fun `the aspect ratio is derived from the frame's own dimensions`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendVideo(HANDLE_VALUE, frame(ByteArray(4 * 2 * 4), 4, 2))
        assertEquals(2f, c.videoFrames.single().picture_aspect_ratio)
    }

    @Test
    fun `a zero-sized frame sends nothing instead of taking the render loop down`() {
        // Memory(0) throws rather than allocating nothing, so an output misconfigured to a zero
        // dimension used to reach an IllegalArgumentException on the pump's coroutine.
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendVideo(HANDLE_VALUE, frame(ByteArray(0), 0, 0))
        JnaNdiLibrary(c).sendVideo(HANDLE_VALUE, frame(ByteArray(0), 1920, 0))
        assertTrue(c.videoFrames.isEmpty())
    }

    @Test
    fun `the pixels reach native memory in the order they were given`() {
        val c = FakeNdiLibC()
        val bytes = byteArrayOf(1, 2, 3, 4)
        JnaNdiLibrary(c).sendVideo(HANDLE_VALUE, frame(bytes, 1, 1))
        val data = assertNotNull(c.videoFrames.single().p_data)
        assertEquals(bytes.toList(), data.getByteArray(0, 4).toList())
    }

    @Test
    fun `the native buffer is reused across frames of the same size`() {
        val c = FakeNdiLibC()
        val lib = JnaNdiLibrary(c)
        lib.sendVideo(HANDLE_VALUE, frame(byteArrayOf(1, 2, 3, 4), 1, 1))
        lib.sendVideo(HANDLE_VALUE, frame(byteArrayOf(5, 6, 7, 8), 1, 1))
        // Same allocation, and the second frame's contents actually landed in it.
        assertSame(c.videoFrames[0].p_data, c.videoFrames[1].p_data)
        assertEquals(listOf<Byte>(5, 6, 7, 8), c.videoFrames[1].p_data!!.getByteArray(0, 4).toList())
    }

    @Test
    fun `a larger frame grows the buffer`() {
        val c = FakeNdiLibC()
        val lib = JnaNdiLibrary(c)
        lib.sendVideo(HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        lib.sendVideo(HANDLE_VALUE, frame(ByteArray(64), 4, 4))
        assertTrue(c.videoFrames[0].p_data !== c.videoFrames[1].p_data)
    }

    @Test
    fun `a smaller frame reuses the already-large buffer rather than shrinking it`() {
        val c = FakeNdiLibC()
        val lib = JnaNdiLibrary(c)
        lib.sendVideo(HANDLE_VALUE, frame(ByteArray(64), 4, 4))
        lib.sendVideo(HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        assertSame(c.videoFrames[0].p_data, c.videoFrames[1].p_data)
    }

    @Test
    fun `two senders get a buffer each rather than writing over one another`() {
        // One JnaNdiLibrary is shared by every sender the runtime hands out, and each sender is
        // driven by its own pump coroutine. A single buffer would have two outputs writing into the
        // same native memory from two threads.
        val c = FakeNdiLibC()
        val lib = JnaNdiLibrary(c)
        lib.sendVideo(HANDLE_VALUE, frame(byteArrayOf(1, 2, 3, 4), 1, 1))
        lib.sendVideo(SECOND_HANDLE_VALUE, frame(byteArrayOf(5, 6, 7, 8), 1, 1))
        assertTrue(c.videoFrames[0].p_data !== c.videoFrames[1].p_data)
        // The first sender's frame is still its own, not the second's.
        assertEquals(listOf<Byte>(1, 2, 3, 4), c.videoFrames[0].p_data!!.getByteArray(0, 4).toList())
    }

    @Test
    fun `destroying one sender releases its buffer and leaves the others alone`() {
        val c = FakeNdiLibC()
        val lib = JnaNdiLibrary(c)
        lib.sendVideo(HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        lib.sendVideo(SECOND_HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        lib.sendDestroy(HANDLE_VALUE)
        lib.sendVideo(SECOND_HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        // The surviving sender kept its allocation...
        assertSame(c.videoFrames[1].p_data, c.videoFrames[2].p_data)
        // ...and the destroyed one's is gone, so a stray frame allocates afresh rather than writing
        // into memory that was freed with the handle.
        lib.sendVideo(HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        assertTrue(c.videoFrames[0].p_data !== c.videoFrames[3].p_data)
    }

    @Test
    fun `sending to a dead handle does nothing`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendVideo(0L, frame(ByteArray(4), 1, 1))
        assertTrue(c.videoFrames.isEmpty())
    }

    @Test
    fun `connectionCount asks the runtime and passes the timeout along`() {
        val c = FakeNdiLibC()
        c.connectionsAnswer = 3
        assertEquals(3, JnaNdiLibrary(c).connectionCount(HANDLE_VALUE, timeoutMs = 250))
        assertEquals(250, c.lastConnectionTimeout)
    }

    @Test
    fun `connectionCount on a dead handle is zero without asking the runtime`() {
        val c = FakeNdiLibC()
        assertEquals(0, JnaNdiLibrary(c).connectionCount(0L))
        assertEquals(-1, c.lastConnectionTimeout)
    }

    @Test
    fun `sendDestroy passes the handle back as a pointer`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendDestroy(HANDLE_VALUE)
        assertEquals(listOf(HANDLE_VALUE), c.destroyedSenders)
    }

    @Test
    fun `destroying a dead handle does nothing`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).sendDestroy(0L)
        assertTrue(c.destroyedSenders.isEmpty())
    }

    @Test
    fun `destroy releases every sender's native buffer and takes the runtime down`() {
        val c = FakeNdiLibC()
        val lib = JnaNdiLibrary(c)
        lib.sendVideo(HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        lib.sendVideo(SECOND_HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        lib.destroy()
        assertEquals(1, c.destroyCount)
        // The buffers are gone, so the next frame allocates afresh rather than writing into freed
        // memory.
        lib.sendVideo(HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        lib.sendVideo(SECOND_HANDLE_VALUE, frame(ByteArray(4), 1, 1))
        assertTrue(c.videoFrames[0].p_data !== c.videoFrames[2].p_data)
        assertTrue(c.videoFrames[1].p_data !== c.videoFrames[3].p_data)
    }

    @Test
    fun `destroy on a library that never sent a frame is harmless`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).destroy()
        assertEquals(1, c.destroyCount)
    }

    // ── The receive half ────────────────────────────────────────────

    @Test
    fun `discovery is created with the caller's flags, and a refusal is a zero handle`() {
        val c = FakeNdiLibC()
        val handle = JnaNdiLibrary(c).findCreate(showLocalSources = false, groups = "Sanctuary")
        assertEquals(FINDER_HANDLE_VALUE, handle)
        assertEquals(false, c.findSettings.single().show_local_sources)
        assertEquals("Sanctuary", c.findSettings.single().p_groups)

        val refused = FakeNdiLibC(findCreateReturns = null)
        assertEquals(0L, JnaNdiLibrary(refused).findCreate())
    }

    @Test
    fun `blank groups reach the runtime as null, which is what selects the default groups`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).findCreate()
        assertEquals(null, c.findSettings.single().p_groups)
        assertEquals(true, c.findSettings.single().show_local_sources)
    }

    @Test
    fun `the runtime's source array is read out by name and address`() {
        val c = FakeNdiLibC()
        c.advertise("BOOTH (Camera 1)" to "192.168.1.20:5961", "BOOTH (Graphics)" to "")
        val sources = JnaNdiLibrary(c).findSources(FINDER_HANDLE_VALUE)

        assertEquals(
            listOf(
                NdiSourceInfo("BOOTH (Camera 1)", "192.168.1.20:5961"),
                NdiSourceInfo("BOOTH (Graphics)", ""),
            ),
            sources,
        )
    }

    @Test
    fun `a zero wait does not block, and a real one does`() {
        val c = FakeNdiLibC()
        c.advertise("BOOTH (Camera 1)" to "")
        val lib = JnaNdiLibrary(c)

        lib.findSources(FINDER_HANDLE_VALUE, timeoutMs = 0)
        assertEquals(0, c.waits, "a redraw must not stall on discovery")

        lib.findSources(FINDER_HANDLE_VALUE, timeoutMs = 250)
        assertEquals(1, c.waits)
        assertEquals(250, c.lastWaitTimeout)
    }

    @Test
    fun `an empty network and a dead finder both read as no sources`() {
        val c = FakeNdiLibC()
        val lib = JnaNdiLibrary(c)
        assertEquals(emptyList(), lib.findSources(FINDER_HANDLE_VALUE))
        assertEquals(emptyList(), lib.findSources(0L))
    }

    @Test
    fun `destroying a finder passes the handle through, and a dead one is ignored`() {
        val c = FakeNdiLibC()
        val lib = JnaNdiLibrary(c)
        lib.findDestroy(FINDER_HANDLE_VALUE)
        lib.findDestroy(0L)
        assertEquals(listOf(FINDER_HANDLE_VALUE), c.destroyedFinders)
    }

    @Test
    fun `a receiver is created against the named source at the bandwidth asked for`() {
        val c = FakeNdiLibC()
        val handle = JnaNdiLibrary(c).recvCreate(
            NdiSourceInfo("BOOTH (Camera 1)", "192.168.1.20:5961"),
            NdiBandwidth.LOWEST,
            receiverName = "Canvas",
        )

        assertEquals(RECEIVER_HANDLE_VALUE, handle)
        val settings = c.recvSettings.single()
        assertEquals("BOOTH (Camera 1)", settings.source_to_connect_to.p_ndi_name)
        assertEquals("192.168.1.20:5961", settings.source_to_connect_to.p_url_address)
        assertEquals("Canvas", settings.p_ndi_recv_name)
        assertEquals(LOWEST_BANDWIDTH, settings.bandwidth)
    }

    @Test
    fun `a source with no address, and no receiver name, send null rather than empty strings`() {
        val c = FakeNdiLibC()
        JnaNdiLibrary(c).recvCreate(NdiSourceInfo("BOOTH (Graphics)"))

        val settings = c.recvSettings.single()
        assertEquals(null, settings.source_to_connect_to.p_url_address)
        assertEquals(null, settings.p_ndi_recv_name)
        assertEquals(HIGHEST_BANDWIDTH, settings.bandwidth)
        assertEquals(false, settings.allow_video_fields, "the runtime deinterlaces for us")
    }

    @Test
    fun `a runtime that refuses to connect gives a zero handle`() {
        val lib = JnaNdiLibrary(FakeNdiLibC(recvCreateReturns = null))
        assertEquals(0L, lib.recvCreate(NdiSourceInfo("BOOTH (Camera 1)")))
    }

    @Test
    fun `a captured video frame is copied out of the runtime's memory and then freed`() {
        val c = FakeNdiLibC()
        val pixels = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val memory = nativeFrame(listOf(pixels), stride = 8)
        c.nextFrameType = FRAME_TYPE_VIDEO
        c.fillCapturedFrame = { it.xres = 2; it.yres = 1; it.FourCC = NdiPixelFormat.BGRA.fourCc; it.p_data = memory }

        val frame = assertNotNull(JnaNdiLibrary(c).recvCaptureVideo(RECEIVER_HANDLE_VALUE, timeoutMs = 100))

        assertEquals(2, frame.width)
        assertEquals(1, frame.height)
        assertEquals(NdiPixelFormat.BGRA, frame.format)
        assertEquals(pixels.toList(), frame.bgra.take(pixels.size))
        assertEquals(100, c.lastCaptureTimeout)
        assertEquals(1, c.freedFrames.size, "the runtime's frame must go back to it")
    }

    @Test
    fun `padded rows are unpacked, so a frame with a stride is not sheared`() {
        val c = FakeNdiLibC()
        val rows = listOf(byteArrayOf(1, 2, 3, 4), byteArrayOf(5, 6, 7, 8))
        // Eight bytes per row on the wire for a one-pixel-wide frame: four of pixel, four of pad.
        val memory = nativeFrame(rows, stride = 8)
        c.nextFrameType = FRAME_TYPE_VIDEO
        c.fillCapturedFrame = {
            it.xres = 1; it.yres = 2; it.FourCC = NdiPixelFormat.BGRX.fourCc
            it.p_data = memory; it.line_stride_in_bytes = 8
        }

        val frame = assertNotNull(JnaNdiLibrary(c).recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))

        assertEquals(listOf<Byte>(1, 2, 3, 4, 5, 6, 7, 8), frame.bgra.take(8))
        assertEquals(NdiPixelFormat.BGRX, frame.format)
    }

    @Test
    fun `an audio or metadata frame is not a picture and answers null`() {
        val c = FakeNdiLibC()
        c.nextFrameType = FRAME_TYPE_AUDIO
        assertEquals(null, JnaNdiLibrary(c).recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
        assertTrue(c.freedFrames.isEmpty(), "nothing was handed over, so nothing is freed")
    }

    @Test
    fun `a dead receiver handle captures nothing`() {
        val c = FakeNdiLibC()
        c.nextFrameType = FRAME_TYPE_VIDEO
        assertEquals(null, JnaNdiLibrary(c).recvCaptureVideo(0L, 0))
        assertEquals(-1, c.lastCaptureTimeout, "the runtime should not have been asked")
    }

    @Test
    fun `a frame in a format we do not read is dropped, and still freed`() {
        val c = FakeNdiLibC()
        c.nextFrameType = FRAME_TYPE_VIDEO
        c.fillCapturedFrame = {
            it.xres = 2; it.yres = 1; it.FourCC = UYVY_FOURCC; it.p_data = nativeFrame(listOf(ByteArray(8)), 8)
        }

        assertEquals(null, JnaNdiLibrary(c).recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
        assertEquals(1, c.freedFrames.size, "or the runtime leaks that frame for the whole service")
    }

    @Test
    fun `a frame with no data pointer, or no pixels, is dropped rather than read`() {
        val c = FakeNdiLibC()
        c.nextFrameType = FRAME_TYPE_VIDEO
        c.fillCapturedFrame = { it.xres = 2; it.yres = 1; it.FourCC = NdiPixelFormat.BGRA.fourCc }
        val lib = JnaNdiLibrary(c)
        assertEquals(null, lib.recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))

        c.fillCapturedFrame = {
            it.xres = 0; it.yres = 0; it.FourCC = NdiPixelFormat.BGRA.fourCc
            it.p_data = nativeFrame(listOf(ByteArray(4)), 4)
        }
        assertEquals(null, lib.recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
    }

    @Test
    fun `one buffer per receiver is reused across frames and not shared between receivers`() {
        val c = FakeNdiLibC()
        c.nextFrameType = FRAME_TYPE_VIDEO
        val memory = nativeFrame(listOf(byteArrayOf(1, 2, 3, 4)), stride = 4)
        c.fillCapturedFrame = { it.xres = 1; it.yres = 1; it.FourCC = NdiPixelFormat.BGRA.fourCc; it.p_data = memory }
        val lib = JnaNdiLibrary(c)

        val first = assertNotNull(lib.recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
        val second = assertNotNull(lib.recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
        assertSame(first.bgra, second.bgra)

        val other = assertNotNull(lib.recvCaptureVideo(SECOND_HANDLE_VALUE, 0))
        assertTrue(first.bgra !== other.bgra, "two receivers must not write into one buffer")
    }

    @Test
    fun `destroying a receiver drops its buffer, and a dead handle is ignored`() {
        val c = FakeNdiLibC()
        c.nextFrameType = FRAME_TYPE_VIDEO
        val memory = nativeFrame(listOf(byteArrayOf(1, 2, 3, 4)), stride = 4)
        c.fillCapturedFrame = { it.xres = 1; it.yres = 1; it.FourCC = NdiPixelFormat.BGRA.fourCc; it.p_data = memory }
        val lib = JnaNdiLibrary(c)

        val first = assertNotNull(lib.recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
        lib.recvDestroy(RECEIVER_HANDLE_VALUE)
        lib.recvDestroy(0L)
        assertEquals(listOf(RECEIVER_HANDLE_VALUE), c.destroyedReceivers)

        val afterwards = assertNotNull(lib.recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
        assertTrue(first.bgra !== afterwards.bgra, "the old buffer went with the old receiver")
    }

    @Test
    fun `destroy releases receive buffers too`() {
        val c = FakeNdiLibC()
        c.nextFrameType = FRAME_TYPE_VIDEO
        val memory = nativeFrame(listOf(byteArrayOf(1, 2, 3, 4)), stride = 4)
        c.fillCapturedFrame = { it.xres = 1; it.yres = 1; it.FourCC = NdiPixelFormat.BGRA.fourCc; it.p_data = memory }
        val lib = JnaNdiLibrary(c)

        val first = assertNotNull(lib.recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
        lib.destroy()
        val afterwards = assertNotNull(lib.recvCaptureVideo(RECEIVER_HANDLE_VALUE, 0))
        assertTrue(first.bgra !== afterwards.bgra)
    }

    @Test
    fun `loading a runtime that is not there returns null rather than throwing`() {
        // The one call that binds a real shared library. It cannot succeed on a machine with no NDI
        // installed, but it must fail as a null and not as an UnsatisfiedLinkError out of the app.
        assertEquals(null, JnaNdiLibrary.load("/nonexistent/libndi.so.6"))
    }
}

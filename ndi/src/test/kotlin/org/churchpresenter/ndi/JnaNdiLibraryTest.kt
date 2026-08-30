package org.churchpresenter.ndi

import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val HANDLE_VALUE = 0x1234L
private const val SECOND_HANDLE_VALUE = 0x5678L

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

    @Test
    fun `loading a runtime that is not there returns null rather than throwing`() {
        // The one call that binds a real shared library. It cannot succeed on a machine with no NDI
        // installed, but it must fail as a null and not as an UnsatisfiedLinkError out of the app.
        assertEquals(null, JnaNdiLibrary.load("/nonexistent/libndi.so.6"))
    }
}

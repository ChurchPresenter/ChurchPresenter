package org.churchpresenter.omt

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SENDER = 0x1234L
private const val RECEIVER = 0x5678L
private const val FRAME_TYPE_VIDEO = 2
private const val FRAME_TYPE_AUDIO = 4
private const val BGRA_BYTES = 4

/**
 * A stand-in for the C symbols `libomt` exports.
 *
 * [OmtLibC] is an interface, which is what makes this possible: everything [JnaOmtLibrary] does
 * around those symbols — the reused native buffer, the two ABI structs, the discovery array, the
 * strided copy of a received frame — is ordinary logic exercised here with no library installed.
 * The structs and pointers are real JNA objects; only the library behind them is not.
 */
private class FakeOmtLibC(
    private val sendCreateReturns: Pointer? = Pointer(SENDER),
    private val receiveCreateReturns: Pointer? = Pointer(RECEIVER),
) : OmtLibC {
    var loggingFilename: String? = "unset"
    val settings = mutableListOf<Pair<String, String>>()
    val sendCreates = mutableListOf<Pair<String, Int>>()
    val senderInfos = mutableListOf<OmtSenderInfoStruct>()
    val frames = mutableListOf<OmtMediaFrameStruct>()
    val sentPixels = mutableListOf<ByteArray>()
    val destroyedSenders = mutableListOf<Long>()
    val receiveCreates = mutableListOf<List<Any>>()
    val receiveFlags = mutableListOf<Pair<Long, Int>>()
    val destroyedReceivers = mutableListOf<Long>()
    var shutdowns = 0
    var connections = 0

    /** What omt_send_getaddress writes and returns. */
    var address: String = ""
    var addressLength: Int? = null

    /** What omt_discovery_getaddresses answers: the array pointer and the count it reports. */
    var discoveryArray: Pointer? = null
    var discoveryCount = 0

    /** What omt_receive answers, and the arguments it was last called with. */
    var nextFrame: Pointer? = null
    var lastReceive: Triple<Long, Int, Int>? = null

    override fun omt_setloggingfilename(filename: String?) {
        loggingFilename = filename
    }

    override fun omt_settings_set_string(name: String, value: String) {
        settings += name to value
    }

    override fun omt_send_create(name: String, quality: Int): Pointer? {
        sendCreates += name to quality
        return sendCreateReturns
    }

    override fun omt_send_setsenderinformation(instance: Pointer, info: OmtSenderInfoStruct) {
        senderInfos += info
    }

    override fun omt_send_getaddress(instance: Pointer, address: ByteArray, maxLength: Int): Int {
        val bytes = this.address.toByteArray(Charsets.UTF_8)
        bytes.copyInto(address, endIndex = minOf(bytes.size, maxLength))
        return addressLength ?: (bytes.size + 1)
    }

    override fun omt_send(instance: Pointer, frame: OmtMediaFrameStruct): Int {
        frames += frame
        sentPixels += frame.Data!!.getByteArray(0, frame.DataLength)
        return frame.DataLength
    }

    override fun omt_send_connections(instance: Pointer): Int = connections

    override fun omt_send_destroy(instance: Pointer) {
        destroyedSenders += Pointer.nativeValue(instance)
    }

    override fun omt_discovery_getaddresses(count: IntByReference): Pointer? {
        count.value = discoveryCount
        return discoveryArray
    }

    override fun omt_receive_create(address: String, frameTypes: Int, format: Int, flags: Int): Pointer? {
        receiveCreates += listOf(address, frameTypes, format, flags)
        return receiveCreateReturns
    }

    override fun omt_receive(instance: Pointer, frameTypes: Int, timeoutMilliseconds: Int): Pointer? {
        lastReceive = Triple(Pointer.nativeValue(instance), frameTypes, timeoutMilliseconds)
        return nextFrame
    }

    override fun omt_receive_setflags(instance: Pointer, flags: Int) {
        receiveFlags += Pointer.nativeValue(instance) to flags
    }

    override fun omt_receive_destroy(instance: Pointer) {
        destroyedReceivers += Pointer.nativeValue(instance)
    }

    override fun omt_shutdown() {
        shutdowns++
    }
}

/** A native `OMTMediaFrame` the library would hand back, over [data] with rows [stride] bytes apart. */
private fun nativeFrame(
    width: Int,
    height: Int,
    stride: Int = width * BGRA_BYTES,
    codec: Int = OmtCodec.BGRA.fourCc,
    flags: Int = VIDEO_FLAG_ALPHA,
    type: Int = FRAME_TYPE_VIDEO,
    fill: (row: Int, col: Int) -> Int = { row, col -> row * 16 + col },
    dataLength: Int? = null,
    withData: Boolean = true,
): Pointer {
    val bytes = ByteArray(maxOf(1, stride * height))
    for (row in 0 until height) {
        for (col in 0 until width * BGRA_BYTES) bytes[row * stride + col] = fill(row, col).toByte()
    }
    val data = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
    val struct = OmtMediaFrameStruct().apply {
        Type = type
        Codec = codec
        Width = width
        Height = height
        Stride = stride
        Flags = flags
        FrameRateN = 30_000
        FrameRateD = 1_000
        Data = if (withData) data else null
        DataLength = dataLength ?: bytes.size
    }
    struct.write()
    keepAlive += data
    keepAlive += struct
    return struct.pointer
}

/** Native memory the tests' frames point into, kept reachable until the JVM exits. */
private val keepAlive = mutableListOf<Any>()

/** A native array of C strings, as `omt_discovery_getaddresses` returns. */
private fun cStringArray(vararg values: String?): Pointer {
    val array = Memory((Native.POINTER_SIZE * values.size).toLong())
    values.forEachIndexed { i, value ->
        val pointer = value?.let { s ->
            Memory((s.toByteArray().size + 1).toLong()).apply { setString(0, s, "UTF-8") }.also { keepAlive += it }
        }
        array.setPointer((Native.POINTER_SIZE * i).toLong(), pointer)
    }
    keepAlive += array
    return array
}

class JnaOmtLibraryTest {

    @Test
    fun `logging and the discovery server reach the library as given`() {
        val c = FakeOmtLibC()
        val lib = JnaOmtLibrary(c)
        lib.setLoggingFilename("/logs/omt.log")
        lib.setDiscoveryServer("  omt://server:6400 ")
        assertEquals("/logs/omt.log", c.loggingFilename)
        assertEquals(listOf("DiscoveryServer" to "omt://server:6400"), c.settings)
        lib.setLoggingFilename(null)
        assertNull(c.loggingFilename)
    }

    @Test
    fun `a sender is created with its quality and reported by handle`() {
        val c = FakeOmtLibC()
        assertEquals(SENDER, JnaOmtLibrary(c).sendCreate("Lyrics", OmtQuality.HIGH))
        assertEquals(listOf("Lyrics" to 100), c.sendCreates)
    }

    @Test
    fun `a refused sender is handle zero`() {
        assertEquals(0L, JnaOmtLibrary(FakeOmtLibC(sendCreateReturns = null)).sendCreate("x", OmtQuality.DEFAULT))
    }

    @Test
    fun `sender information is written as fixed C strings`() {
        val c = FakeOmtLibC()
        JnaOmtLibrary(c).sendSetSenderInformation(SENDER, "ChurchPresenter", "Maker", "1.2")
        val info = c.senderInfos.single()
        assertEquals("ChurchPresenter", info.ProductName.decodeToString().substringBefore('\u0000'))
        assertEquals("Maker", info.Manufacturer.decodeToString().substringBefore('\u0000'))
        assertEquals("1.2", info.Version.decodeToString().substringBefore('\u0000'))
        assertEquals(OMT_MAX_STRING_LENGTH, info.ProductName.size)
    }

    @Test
    fun `nothing is asked of a dead sender handle`() {
        val c = FakeOmtLibC()
        val lib = JnaOmtLibrary(c)
        lib.sendSetSenderInformation(0L, "a", "b", "c")
        lib.sendVideo(0L, OmtVideoFrame(ByteArray(4), 1, 1, true, 30_000, 1_000))
        lib.sendDestroy(0L)
        assertEquals("", lib.sendAddress(0L))
        assertEquals(0, lib.connectionCount(0L))
        assertTrue(c.senderInfos.isEmpty() && c.frames.isEmpty() && c.destroyedSenders.isEmpty())
    }

    @Test
    fun `a C string is terminated and cut to fit`() {
        val out = fixedCString("abcdef", size = 4)
        assertContentEquals(byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0), out)
    }

    @Test
    fun `the advertised address is read up to its terminator`() {
        val c = FakeOmtLibC().apply { address = "HOST (Lyrics)" }
        assertEquals("HOST (Lyrics)", JnaOmtLibrary(c).sendAddress(SENDER))
    }

    @Test
    fun `an address the library could not give is blank`() {
        val c = FakeOmtLibC().apply { address = "ignored"; addressLength = 0 }
        assertEquals("", JnaOmtLibrary(c).sendAddress(SENDER))
    }

    @Test
    fun `an address filling the whole buffer is read to its end`() {
        val long = "x".repeat(OMT_MAX_STRING_LENGTH + 10)
        val c = FakeOmtLibC().apply { address = long }
        assertEquals(OMT_MAX_STRING_LENGTH, JnaOmtLibrary(c).sendAddress(SENDER).length)
    }

    @Test
    fun `a frame is sent as BGRA with its size, rate, stride and alpha flag`() {
        val c = FakeOmtLibC()
        val pixels = ByteArray(2 * 1 * BGRA_BYTES) { it.toByte() }
        val sent = OmtVideoFrame(pixels, 2, 1, alpha = true, frameRateN = 25_000, frameRateD = 1_000)
        JnaOmtLibrary(c).sendVideo(SENDER, sent)
        val frame = c.frames.single()
        assertEquals(FRAME_TYPE_VIDEO, frame.Type)
        assertEquals(-1L, frame.Timestamp, "the sender clocks and paces the frames itself")
        assertEquals(OmtCodec.BGRA.fourCc, frame.Codec)
        assertEquals(2, frame.Width)
        assertEquals(1, frame.Height)
        assertEquals(8, frame.Stride)
        assertEquals(VIDEO_FLAG_ALPHA, frame.Flags)
        assertEquals(25_000, frame.FrameRateN)
        assertEquals(1_000, frame.FrameRateD)
        assertEquals(2f, frame.AspectRatio)
        assertEquals(8, frame.DataLength)
        assertContentEquals(pixels, c.sentPixels.single())
    }

    @Test
    fun `an opaque frame carries no alpha flag`() {
        val c = FakeOmtLibC()
        JnaOmtLibrary(c).sendVideo(SENDER, OmtVideoFrame(ByteArray(4), 1, 1, alpha = false, 30_000, 1_000))
        assertEquals(0, c.frames.single().Flags)
    }

    @Test
    fun `a zero-sized frame is not sent`() {
        val c = FakeOmtLibC()
        JnaOmtLibrary(c).sendVideo(SENDER, OmtVideoFrame(ByteArray(0), 0, 10, true, 30_000, 1_000))
        assertTrue(c.frames.isEmpty())
    }

    @Test
    fun `the native buffer is reused for a frame that fits and regrown for one that does not`() {
        val c = FakeOmtLibC()
        val lib = JnaOmtLibrary(c)
        lib.sendVideo(SENDER, OmtVideoFrame(ByteArray(16), 2, 2, true, 30_000, 1_000))
        lib.sendVideo(SENDER, OmtVideoFrame(ByteArray(4), 1, 1, true, 30_000, 1_000))
        lib.sendVideo(SENDER, OmtVideoFrame(ByteArray(64), 4, 4, true, 30_000, 1_000))
        val (first, second, third) = c.frames.map { Pointer.nativeValue(it.Data) }
        assertEquals(first, second, "a smaller frame goes into the same buffer")
        assertTrue(third != first, "a larger frame needs a larger buffer")
    }

    @Test
    fun `the connection count comes from the library`() {
        val c = FakeOmtLibC().apply { connections = 2 }
        assertEquals(2, JnaOmtLibrary(c).connectionCount(SENDER))
    }

    @Test
    fun `destroying a sender reaches the library`() {
        val c = FakeOmtLibC()
        val lib = JnaOmtLibrary(c)
        lib.sendVideo(SENDER, OmtVideoFrame(ByteArray(4), 1, 1, true, 30_000, 1_000))
        lib.sendDestroy(SENDER)
        assertEquals(listOf(SENDER), c.destroyedSenders)
    }

    @Test
    fun `discovery copies every address out of the array`() {
        val c = FakeOmtLibC().apply {
            discoveryArray = cStringArray("A (One)", null, "B (Two)")
            discoveryCount = 3
        }
        assertEquals(listOf("A (One)", "B (Two)"), JnaOmtLibrary(c).discoveryAddresses())
    }

    @Test
    fun `discovery with no array, no sources or an absurd count is empty`() {
        val lib = FakeOmtLibC()
        assertTrue(JnaOmtLibrary(lib).discoveryAddresses().isEmpty())
        lib.discoveryArray = cStringArray("A (One)")
        lib.discoveryCount = 0
        assertTrue(JnaOmtLibrary(lib).discoveryAddresses().isEmpty())
        lib.discoveryCount = 1_000_000
        assertTrue(JnaOmtLibrary(lib).discoveryAddresses().isEmpty(), "a count no network produces is not walked")
    }

    @Test
    fun `a receiver asks for video only, as BGRA, with the preview flag when asked`() {
        val c = FakeOmtLibC()
        val lib = JnaOmtLibrary(c)
        assertEquals(RECEIVER, lib.recvCreate("omt://host:6400", preview = false))
        assertEquals(RECEIVER, lib.recvCreate("HOST (Cam)", preview = true))
        assertEquals(listOf<Any>("omt://host:6400", FRAME_TYPE_VIDEO, 2, 0), c.receiveCreates[0])
        assertEquals(listOf<Any>("HOST (Cam)", FRAME_TYPE_VIDEO, 2, 1), c.receiveCreates[1])
    }

    @Test
    fun `a receiver for no address, or one the library refuses, is handle zero`() {
        assertEquals(0L, JnaOmtLibrary(FakeOmtLibC()).recvCreate("  ", preview = false))
        assertEquals(0L, JnaOmtLibrary(FakeOmtLibC(receiveCreateReturns = null)).recvCreate("x", false))
    }

    @Test
    fun `a packed frame is copied in one read with its alpha flag`() {
        val c = FakeOmtLibC().apply { nextFrame = nativeFrame(2, 2) }
        val frame = assertNotNull(JnaOmtLibrary(c).recvCaptureVideo(RECEIVER, 50))
        assertEquals(Triple(RECEIVER, FRAME_TYPE_VIDEO, 50), c.lastReceive)
        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertTrue(frame.alpha)
        assertEquals(30_000, frame.frameRateN)
        assertEquals(1_000, frame.frameRateD)
        assertEquals(16, frame.bgra[8].toInt(), "row 1 starts right after row 0")
    }

    @Test
    fun `a padded frame is copied row by row into packed rows`() {
        val c = FakeOmtLibC().apply { nextFrame = nativeFrame(2, 2, stride = 12) }
        val frame = assertNotNull(JnaOmtLibrary(c).recvCaptureVideo(RECEIVER, 0))
        // Row 1's first byte was written at 12 in the padded source and belongs at 8 here.
        assertEquals(16, frame.bgra[8].toInt())
        assertEquals(0, frame.bgra[0].toInt())
    }

    @Test
    fun `a frame without the alpha flag, or sent as BGRX, is opaque`() {
        val unflagged = FakeOmtLibC().apply { nextFrame = nativeFrame(1, 1, flags = 0) }
        assertFalse(assertNotNull(JnaOmtLibrary(unflagged).recvCaptureVideo(RECEIVER, 0)).alpha)
        val bgrx = FakeOmtLibC().apply { nextFrame = nativeFrame(1, 1, codec = OmtCodec.BGRX.fourCc) }
        assertFalse(assertNotNull(JnaOmtLibrary(bgrx).recvCaptureVideo(RECEIVER, 0)).alpha)
    }

    @Test
    fun `nothing is read from a timeout, a dead handle or a frame this module does not read`() {
        val lib = FakeOmtLibC()
        assertNull(JnaOmtLibrary(lib).recvCaptureVideo(RECEIVER, 0), "a timeout")
        assertNull(JnaOmtLibrary(lib).recvCaptureVideo(0L, 0), "a dead handle")
        for (frame in listOf(
            nativeFrame(1, 1, type = FRAME_TYPE_AUDIO),
            nativeFrame(1, 1, codec = 0x59565955),
            nativeFrame(0, 1),
            nativeFrame(1, 1, withData = false),
            nativeFrame(2, 4, dataLength = 8),
        )) {
            lib.nextFrame = frame
            assertNull(JnaOmtLibrary(lib).recvCaptureVideo(RECEIVER, 0))
        }
    }

    @Test
    fun `a receiver's buffer is reused while frames fit`() {
        val c = FakeOmtLibC()
        val lib = JnaOmtLibrary(c)
        c.nextFrame = nativeFrame(2, 2)
        val first = assertNotNull(lib.recvCaptureVideo(RECEIVER, 0)).bgra
        c.nextFrame = nativeFrame(1, 1)
        val second = assertNotNull(lib.recvCaptureVideo(RECEIVER, 0)).bgra
        assertTrue(first === second)
    }

    @Test
    fun `preview is switched and a receiver destroyed through the library`() {
        val c = FakeOmtLibC()
        val lib = JnaOmtLibrary(c)
        lib.recvSetPreview(RECEIVER, true)
        lib.recvSetPreview(RECEIVER, false)
        lib.recvSetPreview(0L, true)
        lib.recvDestroy(RECEIVER)
        lib.recvDestroy(0L)
        assertEquals(listOf(RECEIVER to 1, RECEIVER to 0), c.receiveFlags)
        assertEquals(listOf(RECEIVER), c.destroyedReceivers)
    }

    @Test
    fun `shutdown frees the buffers and stops the library`() {
        val c = FakeOmtLibC()
        val lib = JnaOmtLibrary(c)
        lib.sendVideo(SENDER, OmtVideoFrame(ByteArray(4), 1, 1, true, 30_000, 1_000))
        lib.shutdown()
        assertEquals(1, c.shutdowns)
    }

    @Test
    fun `libvmx is looked for beside libomt by the same extension`() {
        assertEquals("libvmx.dylib", JnaOmtLibrary.codecFileFor("libomt.dylib"))
        assertEquals("libvmx.dll", JnaOmtLibrary.codecFileFor("libomt.dll"))
        assertEquals("libvmx.so", JnaOmtLibrary.codecFileFor("libomt.so"))
        assertNull(JnaOmtLibrary.codecFileFor("libomt"))
    }

    @Test
    fun `a library that is not there does not load`() {
        val dir = kotlin.io.path.createTempDirectory("omt-load").toFile()
        try {
            // A libvmx beside it that is not a library either: the preload fails the same way.
            java.io.File(dir, "libvmx.so").writeText("not a library")
            assertNull(JnaOmtLibrary.load(java.io.File(dir, "libomt.so").absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }
}

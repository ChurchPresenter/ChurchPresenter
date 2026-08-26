package org.churchpresenter.ndi

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure

private const val PROGRESSIVE = 1
private const val SYNTHESIZE_TIMECODE = Long.MAX_VALUE
private const val SIXTEEN_NINE = 16f / 9f

/**
 * The NDI send API as JNA sees it — the flat C symbols `libndi` exports, six of which is the whole
 * of what this app needs.
 *
 * Declared against the SDK's C signatures rather than the `NDIlib_v5_load()` struct-of-pointers the
 * headers wrap them in: the shared library exports both, and the flat symbols are the form JNA can
 * bind without hand-rolling a function-pointer table.
 */
@Suppress("FunctionNaming")  // These are the C symbols' own names; renaming them unbinds them.
internal interface NdiLibC : Library {
    fun NDIlib_initialize(): Boolean
    fun NDIlib_destroy()
    fun NDIlib_version(): String?
    fun NDIlib_is_supported_CPU(): Boolean
    fun NDIlib_send_create(settings: NdiSendCreateStruct): Pointer?
    fun NDIlib_send_destroy(sender: Pointer)
    fun NDIlib_send_send_video_v2(sender: Pointer, frame: NdiVideoFrameStruct)
    fun NDIlib_send_get_no_connections(sender: Pointer, timeoutMs: Int): Int
}

/** `NDIlib_send_create_t`. Field order is the ABI and must not be reordered. */
@Suppress("VariableNaming")  // Field names are matched to the C struct by JNA and are the ABI.
@Structure.FieldOrder("p_ndi_name", "p_groups", "clock_video", "clock_audio")
internal open class NdiSendCreateStruct : Structure() {
    @JvmField var p_ndi_name: String? = null
    @JvmField var p_groups: String? = null
    @JvmField var clock_video: Boolean = true

    // False deliberately, and it matters: clocking audio on a sender that never sends any audio
    // makes the runtime pace itself against a stream that will never arrive.
    @JvmField var clock_audio: Boolean = false
}

/** `NDIlib_video_frame_v2_t`. Field order is the ABI and must not be reordered. */
@Suppress("VariableNaming")  // Field names are matched to the C struct by JNA and are the ABI.
@Structure.FieldOrder(
    "xres", "yres", "FourCC", "frame_rate_N", "frame_rate_D", "picture_aspect_ratio",
    "frame_format_type", "timecode", "p_data", "line_stride_in_bytes", "p_metadata", "timestamp",
)
internal open class NdiVideoFrameStruct : Structure() {
    @JvmField var xres: Int = 0
    @JvmField var yres: Int = 0
    @JvmField var FourCC: Int = 0
    @JvmField var frame_rate_N: Int = 30_000
    @JvmField var frame_rate_D: Int = 1_000
    @JvmField var picture_aspect_ratio: Float = SIXTEEN_NINE
    @JvmField var frame_format_type: Int = PROGRESSIVE

    /** `NDIlib_send_timecode_synthesize` — let the runtime stamp frames from its own clock. */
    @JvmField var timecode: Long = SYNTHESIZE_TIMECODE
    @JvmField var p_data: Pointer? = null
    @JvmField var line_stride_in_bytes: Int = 0
    @JvmField var p_metadata: String? = null
    @JvmField var timestamp: Long = 0
}

/**
 * [NdiLibrary] over a real NDI Runtime loaded from disk.
 *
 * Everything above this talks to the interface, so this class is the module's whole native surface
 * and [load] is its one untestable line — binding a library that is not present on a CI machine and
 * cannot be shipped to one.
 *
 * Not thread-safe by itself: [sendVideo] writes into a native buffer it reuses across calls, sized
 * on first use and regrown only when a larger frame arrives. Each [NdiSender] drives its own pump
 * coroutine and its own instance, which is what keeps that safe — see [NdiSender].
 */
class JnaNdiLibrary internal constructor(private val lib: NdiLibC) : NdiLibrary {

    private var buffer: Memory? = null

    companion object {
        /**
         * Binds the NDI Runtime at [libraryPath], or returns null when it cannot be loaded.
         *
         * The single genuinely uncovered line in this module. Everything it can fail with —
         * missing file, wrong architecture, a runtime too old to export a symbol — arrives as an
         * `UnsatisfiedLinkError` rather than an exception, which is why that is caught by name
         * rather than as `Throwable`.
         */
        fun load(libraryPath: String): JnaNdiLibrary? = try {
            JnaNdiLibrary(Native.load(libraryPath, NdiLibC::class.java))
        } catch (e: UnsatisfiedLinkError) {
            org.churchpresenter.diagnostics.CrashReporter.reportException(
                RuntimeException("NDI runtime at $libraryPath could not be loaded", e),
                "Loading the NDI runtime",
            )
            null
        }
    }

    override fun version(): String = lib.NDIlib_version().orEmpty()

    override fun isSupportedCpu(): Boolean = lib.NDIlib_is_supported_CPU()

    override fun initialize(): Boolean = lib.NDIlib_initialize()

    override fun sendCreate(name: String, groups: String, clockVideo: Boolean): Long {
        val settings = NdiSendCreateStruct().apply {
            p_ndi_name = name
            p_groups = groups.ifBlank { null }
            clock_video = clockVideo
        }
        return Pointer.nativeValue(lib.NDIlib_send_create(settings) ?: return 0L)
    }

    override fun sendVideo(sender: Long, frame: NdiVideoFrame) {
        if (sender == 0L) return
        val needed = frameSizeBytes(frame.width, frame.height).toLong()
        // A zero-sized frame is nothing to send, and `Memory(0)` throws rather than allocating
        // nothing — so a misconfigured output with a zero dimension would take the render loop down
        // instead of quietly sending no picture.
        if (needed <= 0) return
        val target = buffer?.takeIf { it.size() >= needed } ?: Memory(needed).also {
            buffer?.close()
            buffer = it
        }
        target.write(0, frame.bgra, 0, needed.toInt())
        val native = NdiVideoFrameStruct().apply {
            xres = frame.width
            yres = frame.height
            FourCC = frame.format.fourCc
            frame_rate_N = frame.frameRateN
            frame_rate_D = frame.frameRateD
            // Safe to divide: a zero dimension returned above.
            picture_aspect_ratio = frame.width.toFloat() / frame.height.toFloat()
            p_data = target
            line_stride_in_bytes = lineStrideBytes(frame.width)
        }
        lib.NDIlib_send_send_video_v2(Pointer(sender), native)
    }

    override fun connectionCount(sender: Long, timeoutMs: Int): Int =
        if (sender == 0L) 0 else lib.NDIlib_send_get_no_connections(Pointer(sender), timeoutMs)

    override fun sendDestroy(sender: Long) {
        if (sender == 0L) return
        lib.NDIlib_send_destroy(Pointer(sender))
    }

    override fun destroy() {
        buffer?.close()
        buffer = null
        lib.NDIlib_destroy()
    }
}

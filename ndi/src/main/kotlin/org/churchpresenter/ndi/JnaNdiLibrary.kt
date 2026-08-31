package org.churchpresenter.ndi

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import io.sentry.SentryLevel
import org.churchpresenter.diagnostics.CrashReporter
import java.util.concurrent.ConcurrentHashMap

private const val PROGRESSIVE = 1
private const val SYNTHESIZE_TIMECODE = Long.MAX_VALUE
private const val SIXTEEN_NINE = 16f / 9f

/** `NDIlib_recv_color_format_BGRX_BGRA` — every source arrives as one of this module's two formats. */
private const val COLOR_FORMAT_BGRX_BGRA = 0

/** `NDIlib_frame_type_video`, the one answer from `recv_capture` that carries a picture. */
private const val FRAME_TYPE_VIDEO = 1

/** `NDIlib_recv_bandwidth_e`: the full stream, and the sender's free low-resolution proxy. */
private const val BANDWIDTH_HIGHEST = 100
private const val BANDWIDTH_LOWEST = 0

/**
 * The NDI API as JNA sees it — the flat C symbols `libndi` exports, sixteen of which are the whole
 * of what this app needs: four for the runtime itself, four to put a source on the network, and
 * eight to find one and take it off again.
 *
 * Declared against the SDK's C signatures rather than the `NDIlib_v5_load()` struct-of-pointers the
 * headers wrap them in: the shared library exports both, and the flat symbols are the form JNA can
 * bind without hand-rolling a function-pointer table.
 */
// TooManyFunctions: these are the symbols `libndi` exports, and JNA binds one interface to one
// library — splitting them by theme would not reduce the surface, only spread it over two names.
@Suppress("FunctionNaming", "TooManyFunctions")  // C symbols' own names; renaming them unbinds them.
internal interface NdiLibC : Library {
    fun NDIlib_initialize(): Boolean
    fun NDIlib_destroy()
    fun NDIlib_version(): String?
    fun NDIlib_is_supported_CPU(): Boolean
    fun NDIlib_send_create(settings: NdiSendCreateStruct): Pointer?
    fun NDIlib_send_destroy(sender: Pointer)
    fun NDIlib_send_send_video_v2(sender: Pointer, frame: NdiVideoFrameStruct)
    fun NDIlib_send_get_no_connections(sender: Pointer, timeoutMs: Int): Int
    fun NDIlib_find_create_v2(settings: NdiFindCreateStruct): Pointer?
    fun NDIlib_find_destroy(finder: Pointer)
    fun NDIlib_find_wait_for_sources(finder: Pointer, timeoutMs: Int): Boolean
    fun NDIlib_find_get_current_sources(finder: Pointer, count: IntByReference): Pointer?
    fun NDIlib_recv_create_v3(settings: NdiRecvCreateStruct): Pointer?
    fun NDIlib_recv_destroy(receiver: Pointer)
    fun NDIlib_recv_capture_v2(
        receiver: Pointer,
        video: NdiVideoFrameStruct?,
        audio: Pointer?,
        metadata: Pointer?,
        timeoutMs: Int,
    ): Int
    fun NDIlib_recv_free_video_v2(receiver: Pointer, video: NdiVideoFrameStruct)
}

/** `NDIlib_find_create_t`. Field order is the ABI and must not be reordered. */
@Suppress("VariableNaming")  // Field names are matched to the C struct by JNA and are the ABI.
@Structure.FieldOrder("show_local_sources", "p_groups", "p_extra_ips")
internal open class NdiFindCreateStruct : Structure() {
    @JvmField var show_local_sources: Boolean = true
    @JvmField var p_groups: String? = null
    @JvmField var p_extra_ips: String? = null
}

/**
 * `NDIlib_source_t`. Field order is the ABI and must not be reordered.
 *
 * The second field is a union of `p_url_address` and `p_ip_address` in the SDK's header; both are a
 * `const char*` at the same offset, so one field reads either.
 */
@Suppress("VariableNaming")  // Field names are matched to the C struct by JNA and are the ABI.
@Structure.FieldOrder("p_ndi_name", "p_url_address")
internal open class NdiSourceStruct : Structure {
    @JvmField var p_ndi_name: String? = null
    @JvmField var p_url_address: String? = null

    constructor() : super()

    /** Reads a source the runtime allocated — how the discovery array is walked. */
    constructor(memory: Pointer) : super(memory) {
        read()
    }
}

/** `NDIlib_recv_create_v3_t`. Field order is the ABI and must not be reordered. */
@Suppress("VariableNaming")  // Field names are matched to the C struct by JNA and are the ABI.
@Structure.FieldOrder(
    "source_to_connect_to", "color_format", "bandwidth", "allow_video_fields", "p_ndi_recv_name",
)
internal open class NdiRecvCreateStruct : Structure() {
    /** By value, inline in this struct — not a pointer to one. JNA nests a Structure field so. */
    @JvmField var source_to_connect_to: NdiSourceStruct = NdiSourceStruct()
    @JvmField var color_format: Int = COLOR_FORMAT_BGRX_BGRA
    @JvmField var bandwidth: Int = BANDWIDTH_HIGHEST

    // False deliberately: the runtime then deinterlaces an interlaced source itself, so a capture
    // loop never has to work out that it has been handed half a picture.
    @JvmField var allow_video_fields: Boolean = false
    @JvmField var p_ndi_recv_name: String? = null
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
 * [sendVideo] writes into a native buffer it reuses across calls, sized on first use and regrown
 * only when a larger frame arrives. **That buffer is per sender handle, not per library.** One
 * instance of this class is shared by every sender the runtime hands out — see [NdiRuntimeHost] —
 * and each sender is driven by its own pump coroutine, so a single buffer would have two outputs
 * writing into the same native memory from two threads, tearing a frame at best and reading a
 * pointer the other thread had just freed and regrown at worst. Keyed by handle, each sender's
 * buffer is touched only by that sender's pump, which is what keeps the reuse safe — see
 * [NdiSender].
 */
class JnaNdiLibrary internal constructor(private val lib: NdiLibC) : NdiLibrary {

    /** One reused pixel buffer per sender handle. See the class doc for why it is not one buffer. */
    private val buffers = ConcurrentHashMap<Long, Memory>()

    /**
     * One reused frame buffer per receiver handle, for the same reason and with the same rule: a
     * receiver is driven by one capture loop, so its buffer is touched by one thread.
     */
    private val received = ConcurrentHashMap<Long, ByteArray>()

    companion object {
        /**
         * Binds the NDI Runtime at [libraryPath], or returns null when it cannot be loaded.
         *
         * The single genuinely uncovered line in this module. Everything it can fail with —
         * missing file, wrong architecture, a runtime too old to export a symbol — arrives as an
         * `UnsatisfiedLinkError` rather than an exception, which is why that is caught by name
         * rather than as `Throwable`.
         */
        /**
         * The runtime at [libraryPath], or null when it will not load.
         *
         * A load failure is not reported. The NDI Runtime is an optional download the operator
         * installs themselves — the same standing as VLC and ffmpeg — and
         * [NdiRuntimeStatus.LoadFailed] already carries the path to the settings card, which says
         * it "may be for a different processor architecture, or too old" and links to the
         * download. An event on top of that told us nothing the operator was not already being
         * told, and it arrived once per attempt: the card's "look again" button made a single
         * unusable install into eight reports from one church.
         *
         * The fact still rides along as a tag and a breadcrumb, so if something else in the
         * session does report, the failed runtime is visible in it. Same shape as `jcef.blocked`.
         */
        fun load(libraryPath: String): JnaNdiLibrary? = try {
            JnaNdiLibrary(Native.load(libraryPath, NdiLibC::class.java))
        } catch (e: UnsatisfiedLinkError) {
            runCatching {
                CrashReporter.setTag("ndi.load_failed", "true")
                CrashReporter.breadcrumb(
                    "NDI runtime at $libraryPath could not be loaded: ${e.message}",
                    category = "ndi",
                    level = SentryLevel.WARNING,
                )
            }
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
        val target = buffers[sender]?.takeIf { it.size() >= needed }
            ?: Memory(needed).also { buffers.put(sender, it)?.close() }
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
        // The handle is dead, so its buffer can never be written again; keeping it would leak a
        // frame's worth of native memory per output an operator removes during a service.
        buffers.remove(sender)?.close()
    }

    override fun findCreate(showLocalSources: Boolean, groups: String): Long {
        val settings = NdiFindCreateStruct().apply {
            show_local_sources = showLocalSources
            p_groups = groups.ifBlank { null }
        }
        return Pointer.nativeValue(lib.NDIlib_find_create_v2(settings) ?: return 0L)
    }

    override fun findSources(finder: Long, timeoutMs: Int): List<NdiSourceInfo> {
        if (finder == 0L) return emptyList()
        val handle = Pointer(finder)
        // Answer 0 immediately with what is already known; anything else blocks for at most that
        // long and returns early the moment the list changes.
        if (timeoutMs > 0) lib.NDIlib_find_wait_for_sources(handle, timeoutMs)
        val count = IntByReference()
        val first = lib.NDIlib_find_get_current_sources(handle, count) ?: return emptyList()
        if (count.value <= 0) return emptyList()
        // The runtime owns this array and keeps it alive until the next call on the same finder,
        // so the names are copied out of it here rather than held.
        return NdiSourceStruct(first).toArray(count.value).map { struct ->
            val source = struct as NdiSourceStruct
            NdiSourceInfo(source.p_ndi_name.orEmpty(), source.p_url_address.orEmpty())
        }
    }

    override fun findDestroy(finder: Long) {
        if (finder == 0L) return
        lib.NDIlib_find_destroy(Pointer(finder))
    }

    override fun recvCreate(source: NdiSourceInfo, bandwidth: NdiBandwidth, receiverName: String): Long {
        val settings = NdiRecvCreateStruct()
        settings.source_to_connect_to.p_ndi_name = source.name.ifBlank { null }
        settings.source_to_connect_to.p_url_address = source.address.ifBlank { null }
        settings.bandwidth = if (bandwidth == NdiBandwidth.LOWEST) BANDWIDTH_LOWEST else BANDWIDTH_HIGHEST
        settings.p_ndi_recv_name = receiverName.ifBlank { null }
        return Pointer.nativeValue(lib.NDIlib_recv_create_v3(settings) ?: return 0L)
    }

    override fun recvCaptureVideo(receiver: Long, timeoutMs: Int): NdiVideoFrame? {
        if (receiver == 0L) return null
        val handle = Pointer(receiver)
        val native = NdiVideoFrameStruct()
        // Null for audio and metadata: the runtime then consumes and drops both rather than
        // queueing them behind a caller that will never ask for them.
        if (lib.NDIlib_recv_capture_v2(handle, native, null, null, timeoutMs) != FRAME_TYPE_VIDEO) return null
        return try {
            copyReceivedFrame(receiver, native)
        } finally {
            // The frame's pixels belong to the runtime until this returns them, and a capture loop
            // that skipped it — because the format was one we do not read, say — would leak a
            // frame's worth of native memory per frame for the length of the service.
            lib.NDIlib_recv_free_video_v2(handle, native)
        }
    }

    /**
     * The runtime's frame copied into [receiver]'s own buffer, or null when it is not one we read.
     *
     * **[NdiVideoFrameStruct.line_stride_in_bytes] is not `width * 4`** in general — the runtime is
     * entitled to pad rows — so the copy is row by row unless the frame happens to be packed, in
     * which case it is one read rather than 1,080 of them.
     */
    private fun copyReceivedFrame(receiver: Long, native: NdiVideoFrameStruct): NdiVideoFrame? {
        val data = native.p_data ?: return null
        val format = NdiPixelFormat.ofFourCc(native.FourCC) ?: return null
        val needed = frameSizeBytes(native.xres, native.yres)
        if (needed <= 0) return null
        val target = received[receiver]?.takeIf { it.size >= needed }
            ?: ByteArray(needed).also { received[receiver] = it }
        val packed = lineStrideBytes(native.xres)
        val stride = native.line_stride_in_bytes.takeIf { it > packed } ?: packed
        if (stride == packed) {
            data.read(0, target, 0, needed)
        } else {
            for (row in 0 until native.yres) {
                data.read(row.toLong() * stride, target, row * packed, packed)
            }
        }
        return NdiVideoFrame(
            target, native.xres, native.yres, format, native.frame_rate_N, native.frame_rate_D,
        )
    }

    override fun recvDestroy(receiver: Long) {
        if (receiver == 0L) return
        lib.NDIlib_recv_destroy(Pointer(receiver))
        received.remove(receiver)
    }

    override fun destroy() {
        for (buffer in buffers.values) buffer.close()
        buffers.clear()
        received.clear()
        lib.NDIlib_destroy()
    }
}

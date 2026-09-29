package org.churchpresenter.omt

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import io.sentry.SentryLevel
import org.churchpresenter.diagnostics.CrashReporter
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** `OMTFrameType_Video`. */
private const val FRAME_TYPE_VIDEO = 2

/** `OMTVideoFlags_Alpha`: the fourth byte of a BGRA frame is transparency, not padding. */
internal const val VIDEO_FLAG_ALPHA = 2

/** `OMTPreferredVideoFormat_BGRA`: whatever the sender encoded, hand it back as BGRA. */
private const val PREFERRED_FORMAT_BGRA = 2

/** `OMTReceiveFlags_None` and `OMTReceiveFlags_Preview`. */
private const val RECEIVE_FLAGS_NONE = 0
private const val RECEIVE_FLAGS_PREVIEW = 1

/**
 * `Timestamp = -1`: the sender stamps frames itself and throttles `omt_send` to the frame rate the
 * frame declares — what NDI's `clock_video` does, and what keeps a receiver's timing sane when the
 * pump's own cadence drifts.
 */
private const val TIMESTAMP_SENDER_CLOCKED = -1L

/** `OMT_MAX_STRING_LENGTH`, the size of every `char[]` in `OMTSenderInfo`. */
internal const val OMT_MAX_STRING_LENGTH = 1_024

/** The setting `libomt` reads its discovery server from. */
private const val SETTING_DISCOVERY_SERVER = "DiscoveryServer"

/**
 * More sources than any real network carries. Not a limit on discovery: a bound on how much a
 * nonsense count can make this read, as `:ndi` bounds its own.
 */
private const val MAX_DISCOVERED_SOURCES = 1_024

/**
 * The `libomt` API as JNA sees it — the flat C symbols `libomt.h` declares, fourteen of which are
 * the whole of what this app needs.
 */
@Suppress("FunctionNaming", "TooManyFunctions")  // C symbols' own names; renaming them unbinds them.
internal interface OmtLibC : Library {
    fun omt_setloggingfilename(filename: String?)
    fun omt_settings_set_string(name: String, value: String)
    fun omt_send_create(name: String, quality: Int): Pointer?
    fun omt_send_setsenderinformation(instance: Pointer, info: OmtSenderInfoStruct)
    fun omt_send_getaddress(instance: Pointer, address: ByteArray, maxLength: Int): Int
    fun omt_send(instance: Pointer, frame: OmtMediaFrameStruct): Int
    fun omt_send_connections(instance: Pointer): Int
    fun omt_send_destroy(instance: Pointer)
    fun omt_discovery_getaddresses(count: IntByReference): Pointer?
    fun omt_receive_create(address: String, frameTypes: Int, format: Int, flags: Int): Pointer?
    fun omt_receive(instance: Pointer, frameTypes: Int, timeoutMilliseconds: Int): Pointer?
    fun omt_receive_setflags(instance: Pointer, flags: Int)
    fun omt_receive_destroy(instance: Pointer)
    fun omt_shutdown()
}

/**
 * `OMTMediaFrame`. Field order is the ABI and must not be reordered.
 *
 * Every C `enum` here is an `int` — `libomt.h` pins each with an `_INT32 = 0x7fffffff` member for
 * exactly that reason — so they are `Int`s, and JNA's default alignment puts [Timestamp] on its
 * eight-byte boundary as the C compiler does.
 */
@Suppress("VariableNaming")  // Field names are matched to the C struct by JNA and are the ABI.
@Structure.FieldOrder(
    "Type", "Timestamp", "Codec", "Width", "Height", "Stride", "Flags", "FrameRateN", "FrameRateD",
    "AspectRatio", "ColorSpace", "SampleRate", "Channels", "SamplesPerChannel", "Data", "DataLength",
    "CompressedData", "CompressedLength", "FrameMetadata", "FrameMetadataLength",
)
internal open class OmtMediaFrameStruct : Structure {
    @JvmField var Type: Int = FRAME_TYPE_VIDEO
    @JvmField var Timestamp: Long = TIMESTAMP_SENDER_CLOCKED
    @JvmField var Codec: Int = 0
    @JvmField var Width: Int = 0
    @JvmField var Height: Int = 0
    @JvmField var Stride: Int = 0
    @JvmField var Flags: Int = 0
    @JvmField var FrameRateN: Int = 0
    @JvmField var FrameRateD: Int = 0
    @JvmField var AspectRatio: Float = 0f
    @JvmField var ColorSpace: Int = 0
    @JvmField var SampleRate: Int = 0
    @JvmField var Channels: Int = 0
    @JvmField var SamplesPerChannel: Int = 0
    @JvmField var Data: Pointer? = null
    @JvmField var DataLength: Int = 0
    @JvmField var CompressedData: Pointer? = null
    @JvmField var CompressedLength: Int = 0
    @JvmField var FrameMetadata: Pointer? = null
    @JvmField var FrameMetadataLength: Int = 0

    constructor() : super()

    /** Reads a frame the library owns — what `omt_receive` returns a pointer to. */
    constructor(memory: Pointer) : super(memory) {
        read()
    }
}

/** `OMTSenderInfo`: six fixed `char[1024]`s. Field order is the ABI and must not be reordered. */
@Suppress("VariableNaming")  // Field names are matched to the C struct by JNA and are the ABI.
@Structure.FieldOrder("ProductName", "Manufacturer", "Version", "Reserved1", "Reserved2", "Reserved3")
internal open class OmtSenderInfoStruct : Structure() {
    @JvmField var ProductName = ByteArray(OMT_MAX_STRING_LENGTH)
    @JvmField var Manufacturer = ByteArray(OMT_MAX_STRING_LENGTH)
    @JvmField var Version = ByteArray(OMT_MAX_STRING_LENGTH)
    @JvmField var Reserved1 = ByteArray(OMT_MAX_STRING_LENGTH)
    @JvmField var Reserved2 = ByteArray(OMT_MAX_STRING_LENGTH)
    @JvmField var Reserved3 = ByteArray(OMT_MAX_STRING_LENGTH)
}

/**
 * [value] as a NUL-terminated UTF-8 `char[]` of [size] bytes, truncated to fit.
 *
 * Truncated on a byte count, so a name cut mid-character would leave half a UTF-8 sequence —
 * harmless to C, and no name the app writes here is anywhere near a kilobyte.
 */
internal fun fixedCString(value: String, size: Int = OMT_MAX_STRING_LENGTH): ByteArray {
    val out = ByteArray(size)
    val bytes = value.toByteArray(Charsets.UTF_8)
    bytes.copyInto(out, endIndex = minOf(bytes.size, size - 1))
    return out
}

/**
 * [OmtLibrary] over a real `libomt` loaded from disk.
 *
 * Everything above this talks to the interface, so this class is the module's whole native surface
 * and [load] is its one untestable line.
 *
 * [sendVideo] writes into a native buffer it reuses across calls, **one per sender handle** — each
 * sender is driven by its own pump, so a single shared buffer would have two outputs writing into
 * the same native memory from two threads. The receive side keeps one byte array per receiver
 * handle under the same rule.
 */
class JnaOmtLibrary internal constructor(private val lib: OmtLibC) : OmtLibrary {

    private val buffers = ConcurrentHashMap<Long, Memory>()
    private val received = ConcurrentHashMap<Long, ByteArray>()

    companion object {
        /**
         * The codec library `libomt` encodes and decodes with, preloaded from beside it.
         *
         * `libomt` is a .NET NativeAOT library and finds `libvmx` by name at run time rather than
         * through a link-time dependency, so nothing tells the dynamic loader where it is. Loading
         * it first by its full path puts it in the process under that name before `libomt` asks.
         * Held for the life of the process: JNA may unload a library nothing references.
         */
        @Volatile
        private var codec: NativeLibrary? = null

        /**
         * The `libomt` at [libraryPath], or null when it will not load.
         *
         * Not reported, for the reason `JnaNdiLibrary.load` gives: `OmtRuntimeStatus.LoadFailed`
         * already carries the path to the settings card, and a report per "look again" press turns
         * one unusable install into a stream of events. It rides along as a tag and a breadcrumb.
         */
        fun load(libraryPath: String): JnaOmtLibrary? = try {
            val file = File(libraryPath)
            codecFileFor(file.name)?.let { name ->
                val vmx = File(file.parentFile, name)
                if (vmx.isFile && codec == null) codec = NativeLibrary.getInstance(vmx.absolutePath)
            }
            JnaOmtLibrary(
                Native.load(
                    libraryPath,
                    OmtLibC::class.java,
                    mapOf(Library.OPTION_STRING_ENCODING to Charsets.UTF_8.name()),
                ),
            )
        } catch (e: UnsatisfiedLinkError) {
            runCatching {
                CrashReporter.setTag("omt.load_failed", "true")
                CrashReporter.breadcrumb(
                    "OMT library at $libraryPath could not be loaded: ${e.message}",
                    category = "omt",
                    level = SentryLevel.WARNING,
                )
            }
            null
        }

        /** `libvmx`'s file name beside a `libomt` called [omtFileName], by the same extension. */
        internal fun codecFileFor(omtFileName: String): String? {
            val ext = omtFileName.substringAfterLast('.', "")
            return if (ext.isEmpty()) null else "libvmx.$ext"
        }
    }

    override fun setLoggingFilename(path: String?) = lib.omt_setloggingfilename(path)

    override fun setDiscoveryServer(url: String) =
        lib.omt_settings_set_string(SETTING_DISCOVERY_SERVER, url.trim())

    override fun sendCreate(name: String, quality: OmtQuality): Long =
        Pointer.nativeValue(lib.omt_send_create(name, quality.native) ?: return 0L)

    override fun sendSetSenderInformation(sender: Long, product: String, manufacturer: String, version: String) {
        if (sender == 0L) return
        val info = OmtSenderInfoStruct().apply {
            ProductName = fixedCString(product)
            Manufacturer = fixedCString(manufacturer)
            Version = fixedCString(version)
        }
        lib.omt_send_setsenderinformation(Pointer(sender), info)
    }

    override fun sendAddress(sender: Long): String {
        if (sender == 0L) return ""
        val buffer = ByteArray(OMT_MAX_STRING_LENGTH)
        val length = lib.omt_send_getaddress(Pointer(sender), buffer, buffer.size)
        if (length <= 0) return ""
        // The returned length counts the terminator, and a value longer than the buffer is cut to it.
        val end = buffer.indexOf(0.toByte()).takeIf { it >= 0 } ?: buffer.size
        return String(buffer, 0, end, Charsets.UTF_8)
    }

    override fun sendVideo(sender: Long, frame: OmtVideoFrame) {
        if (sender == 0L) return
        val needed = frameSizeBytes(frame.width, frame.height)
        // A zero-sized frame is nothing to send, and `Memory(0)` throws rather than allocating
        // nothing — the bug `:ndi` found the hard way.
        if (needed <= 0) return
        val target = buffers[sender]?.takeIf { it.size() >= needed }
            ?: Memory(needed.toLong()).also { buffers.put(sender, it)?.close() }
        target.write(0, frame.bgra, 0, needed)
        val native = OmtMediaFrameStruct().apply {
            Codec = OmtCodec.BGRA.fourCc
            Width = frame.width
            Height = frame.height
            Stride = lineStrideBytes(frame.width)
            Flags = if (frame.alpha) VIDEO_FLAG_ALPHA else 0
            FrameRateN = frame.frameRateN
            FrameRateD = frame.frameRateD
            // Safe to divide: a zero dimension returned above.
            AspectRatio = frame.width.toFloat() / frame.height.toFloat()
            Data = target
            DataLength = needed
        }
        lib.omt_send(Pointer(sender), native)
    }

    override fun connectionCount(sender: Long): Int =
        if (sender == 0L) 0 else lib.omt_send_connections(Pointer(sender))

    override fun sendDestroy(sender: Long) {
        if (sender == 0L) return
        lib.omt_send_destroy(Pointer(sender))
        // The handle is dead, so its buffer can never be written again; keeping it would leak a
        // frame's worth of native memory per output an operator removes during a service.
        buffers.remove(sender)?.close()
    }

    override fun discoveryAddresses(): List<String> {
        val count = IntByReference()
        val array = lib.omt_discovery_getaddresses(count) ?: return emptyList()
        if (count.value !in 1..MAX_DISCOVERED_SOURCES) return emptyList()
        // The library owns this array until the next call, so the strings are copied out here.
        return array.getPointerArray(0, count.value).mapNotNull { it?.getString(0, Charsets.UTF_8.name()) }
    }

    override fun recvCreate(address: String, preview: Boolean): Long {
        if (address.isBlank()) return 0L
        val handle = lib.omt_receive_create(
            address,
            FRAME_TYPE_VIDEO,
            PREFERRED_FORMAT_BGRA,
            if (preview) RECEIVE_FLAGS_PREVIEW else RECEIVE_FLAGS_NONE,
        ) ?: return 0L
        return Pointer.nativeValue(handle)
    }

    override fun recvCaptureVideo(receiver: Long, timeoutMs: Int): OmtVideoFrame? {
        if (receiver == 0L) return null
        val pointer = lib.omt_receive(Pointer(receiver), FRAME_TYPE_VIDEO, timeoutMs) ?: return null
        return copyReceivedFrame(receiver, OmtMediaFrameStruct(pointer))
    }

    /**
     * The library's frame copied into [receiver]'s own buffer, or null when it is not one we read.
     *
     * **`Stride` is not `Width * 4`** in general — a decoder is entitled to pad rows — so the copy is
     * row by row unless the frame happens to be packed.
     */
    private fun copyReceivedFrame(receiver: Long, native: OmtMediaFrameStruct): OmtVideoFrame? {
        if (native.Type != FRAME_TYPE_VIDEO) return null
        val data = native.Data ?: return null
        val codec = OmtCodec.ofFourCc(native.Codec) ?: return null
        val needed = frameSizeBytes(native.Width, native.Height)
        if (needed <= 0) return null
        val packed = lineStrideBytes(native.Width)
        val stride = native.Stride.takeIf { it > packed } ?: packed
        // A frame claiming more rows than its data holds is not one to walk off the end of.
        if (native.DataLength in 1 until stride * (native.Height - 1) + packed) return null
        val target = received[receiver]?.takeIf { it.size >= needed }
            ?: ByteArray(needed).also { received[receiver] = it }
        if (stride == packed) {
            data.read(0, target, 0, needed)
        } else {
            for (row in 0 until native.Height) {
                data.read(row.toLong() * stride, target, row * packed, packed)
            }
        }
        val alpha = codec == OmtCodec.BGRA && (native.Flags and VIDEO_FLAG_ALPHA) != 0
        return OmtVideoFrame(target, native.Width, native.Height, alpha, native.FrameRateN, native.FrameRateD)
    }

    override fun recvSetPreview(receiver: Long, preview: Boolean) {
        if (receiver == 0L) return
        lib.omt_receive_setflags(Pointer(receiver), if (preview) RECEIVE_FLAGS_PREVIEW else RECEIVE_FLAGS_NONE)
    }

    override fun recvDestroy(receiver: Long) {
        if (receiver == 0L) return
        lib.omt_receive_destroy(Pointer(receiver))
        received.remove(receiver)
    }

    override fun shutdown() {
        for (buffer in buffers.values) buffer.close()
        buffers.clear()
        received.clear()
        lib.omt_shutdown()
    }
}

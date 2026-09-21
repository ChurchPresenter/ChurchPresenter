package org.churchpresenter.app.churchpresenter.utils

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

private const val DEVICE_NAME_CHARS = 32
private const val DEVICE_STRING_CHARS = 128
private const val DISPLAY_DEVICE_ACTIVE = 0x1
private const val MAX_ADAPTERS = 16
private const val NUL = '\u0000'

/**
 * One display adapter, as the OS reports it for a given display.
 *
 * [deviceId] is the PCI identifier (`PCI\VEN_10DE&DEV_2182&...`); [GpuInfo.vendorOf] reads the
 * vendor out of it. It is the field that matters most: whether a GPU fault is an NVIDIA, AMD or
 * Intel driver problem is the first question asked of one, and the adapter *name* does not answer
 * it reliably — "Radeon (TM) RX 480" and "AMD Radeon(TM) RX 6500 XT" share no common prefix.
 *
 * **[adapterName] and the vendor behind [deviceId] can legitimately disagree, and both are kept on
 * purpose.** The name comes from the *driver*, the id from the *hardware*. A machine with no vendor
 * driver installed falls back to Windows' generic display driver and reports
 * `Microsoft Basic Display Adapter` while keeping its real PCI id — measured on this hardware as
 * `Microsoft Basic Display Adapter (Intel)`, an Intel UHD 630 with no Intel driver present. Those
 * are two different facts, and the second one — that the machine is on an unaccelerated fallback
 * driver — is a strong diagnosis on its own, so neither half is worth discarding for the other.
 *
 * It also means the `1414` (Microsoft) entry in `VENDORS` is narrower than it looks: a
 * basic-display-adapter machine keeps its hardware's vendor id, so that mapping only fires for
 * genuinely virtual adapters, not for a driverless physical one.
 */
internal data class DisplayAdapter(
    val displayName: String,
    val adapterName: String,
    val deviceId: String,
    val active: Boolean,
)

/**
 * Which GPU is actually driving the screens, for the crash report and the diagnostic dump.
 *
 * A renderer fault's first question is which driver it happened on, and until this existed no
 * report carried it: the crash file recorded OS and Java only, and the Sentry tags stopped at
 * `render.api`, which says what skiko chose but not what it ran on. So a group of GPU driver
 * access violations could not be split by vendor — the one axis that decides whether a backend
 * default is the right one.
 *
 * **Read through `EnumDisplayDevices`, deliberately, and not out of the registry.** The obvious
 * registry source — `Control\Class\{4d36e968-...}\0000` — enumerates every *driver ever installed*
 * rather than the hardware present: on the machine this was written against it lists five adapters,
 * the first of which is an AMD card that has not been in the box for years, while the displays are
 * driven by a GeForce. A report naming the wrong vendor is worse than one naming none, because
 * nothing downstream can tell that it is wrong. `EnumDisplayDevices` answers per display, and
 * reports what is actually driving it.
 */
object GpuInfo {

    /** Minimal `user32` binding: JNA's own `User32` declares `EnumDisplayMonitors` but not this. */
    internal interface Display : StdCallLibrary {
        @Suppress("FunctionNaming")
        fun EnumDisplayDevicesW(device: String?, devNum: Int, info: DisplayDevice, flags: Int): Boolean

        companion object {
            val INSTANCE: Display by lazy {
                Native.load("user32", Display::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }
        }
    }

    @Structure.FieldOrder("cb", "DeviceName", "DeviceString", "StateFlags", "DeviceID", "DeviceKey")
    @Suppress("VariableNaming")
    internal class DisplayDevice : Structure() {
        @JvmField var cb: Int = 0
        @JvmField var DeviceName: CharArray = CharArray(DEVICE_NAME_CHARS)
        @JvmField var DeviceString: CharArray = CharArray(DEVICE_STRING_CHARS)
        @JvmField var StateFlags: Int = 0
        @JvmField var DeviceID: CharArray = CharArray(DEVICE_STRING_CHARS)
        @JvmField var DeviceKey: CharArray = CharArray(DEVICE_STRING_CHARS)
    }

    /** PCI vendor ids. Virtual adapters are named because "no real GPU" is itself the diagnosis. */
    private val VENDORS = mapOf(
        "10de" to "NVIDIA", "1002" to "AMD", "1022" to "AMD", "8086" to "Intel",
        "1414" to "Microsoft", "15ad" to "VMware", "1ab8" to "Parallels",
        "80ee" to "VirtualBox", "1b36" to "QEMU", "1af4" to "virtio", "5333" to "S3",
    )

    /** The vendor [deviceId] names, or "unknown" when it carries no `VEN_xxxx`. */
    internal fun vendorOf(deviceId: String): String {
        val ven = Regex("VEN_([0-9A-Fa-f]{4})").find(deviceId)?.groupValues?.get(1)?.lowercase()
            ?: return "unknown"
        return VENDORS[ven] ?: "0x$ven"
    }

    /** A C string out of a fixed-width JNA char buffer — everything up to the first NUL. */
    internal fun readCString(buf: CharArray): String {
        val end = buf.indexOf(NUL).let { if (it < 0) buf.size else it }
        return String(buf, 0, end)
    }

    /**
     * The active adapters, deduplicated by name.
     *
     * Four displays on one card report the same adapter four times, so the list is distinct rather
     * than per-display: the report wants "which GPUs", not "how many cables".
     */
    internal fun distinctActive(adapters: List<DisplayAdapter>): List<DisplayAdapter> =
        adapters.filter { it.active && it.adapterName.isNotBlank() }.distinctBy { it.adapterName }

    /** One line for the diagnostic report, e.g. `NVIDIA GeForce GTX 1660 Ti (NVIDIA)`. */
    internal fun summarise(adapters: List<DisplayAdapter>): String {
        val active = distinctActive(adapters)
        if (active.isEmpty()) return "unknown"
        return active.joinToString(" + ") { "${it.adapterName} (${vendorOf(it.deviceId)})" }
    }

    /**
     * The tags a crash report carries. Empty when nothing could be read, so a platform without this
     * call sends no tag at all rather than one saying "unknown" — an absent tag and a tag whose
     * value is a guess are very different things to group a crash by.
     */
    internal fun tags(adapters: List<DisplayAdapter>): Map<String, String> {
        val active = distinctActive(adapters)
        if (active.isEmpty()) return emptyMap()
        return mapOf(
            "gpu.vendor" to active.joinToString("+") { vendorOf(it.deviceId) },
            "gpu.adapter" to active.joinToString(" + ") { it.adapterName },
            "gpu.count" to active.size.toString(),
        )
    }

    /** Whether this platform has the call at all. */
    internal fun isSupported(osName: String = System.getProperty("os.name", "")): Boolean =
        osName.lowercase().contains("win")

    /**
     * Walks `EnumDisplayDevices`, which is the one step that needs the real OS.
     *
     * [fill] is taken as a parameter for the reason `WindowsWindowCapture.listWindowsWith` takes its
     * `User32`: the loop, the NUL-trimming and the active-flag test are ordinary logic and are
     * tested with a stand-in, leaving only the call itself uncovered.
     */
    internal fun enumerateWith(
        newDevice: () -> DisplayDevice = { DisplayDevice() },
        fill: (Int, DisplayDevice) -> Boolean,
    ): List<DisplayAdapter> {
        val out = mutableListOf<DisplayAdapter>()
        for (i in 0 until MAX_ADAPTERS) {
            val d = newDevice()
            d.cb = d.size()
            if (!fill(i, d)) break
            out.add(
                DisplayAdapter(
                    displayName = readCString(d.DeviceName),
                    adapterName = readCString(d.DeviceString),
                    deviceId = readCString(d.DeviceID),
                    active = (d.StateFlags and DISPLAY_DEVICE_ACTIVE) != 0,
                )
            )
        }
        return out
    }

    private fun enumerate(): List<DisplayAdapter> {
        if (!isSupported()) return emptyList()
        return try {
            enumerateWith { i, d -> Display.INSTANCE.EnumDisplayDevicesW(null, i, d, 0) }
        } catch (_: Throwable) {
            emptyList()   // a missing user32 export must never stop the app starting
        }
    }

    /** Read once per process: the adapters do not change under a running app. */
    private val adapters: List<DisplayAdapter> by lazy { enumerate() }

    /** The `GPU:` line for the diagnostic report. */
    val summary: String get() = summarise(adapters)

    /** The GPU tags for the crash reporter, empty when nothing could be read. */
    fun crashTags(): Map<String, String> = tags(adapters)
}

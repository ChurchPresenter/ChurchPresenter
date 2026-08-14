package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.TestSingletons
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An **opt-in manual harness** that exercises DeckLinkManager against a real Blackmagic DeckLink
 * card. It is not part of the ordinary unit suite and does nothing unless explicitly asked for:
 *
 * ```
 * ./gradlew :composeApp:jvmTest -PdecklinkHardware=true --tests '*DeckLinkHardwareTest*'
 * ```
 *
 * **Why it is gated rather than merely self-skipping.** Reaching the native calls has real side
 * effects that must never happen during a routine `:composeApp:check`:
 * - `open` pushes a frame to whatever the card's output is wired to — on a machine mid-service
 *   that is a visible glitch on the program feed — and installs a JVM shutdown hook that sends
 *   three more black frames and sleeps 100 ms at teardown.
 * - Loading the driver costs far more than the ~1s per-test bar the suite is held to.
 * - It resets [DeckLinkManager]'s private memoized `available` field (see below), which
 *   [DeckLinkManagerTest] depends on being latched to `false`. Leaving that reset out of the
 *   default run keeps the two suites independent instead of coupled through a restore.
 *
 * With the flag off, [setUp] touches nothing at all — no reflection, no system properties, no
 * library load — and every test returns at its [assumeAvailable] guard.
 *
 * With the flag on: the `available` field is a private memoizing `Boolean?`, so once the
 * guard-clause tests in [DeckLinkManagerTest] latch it to `false` it stays false for the rest of
 * the JVM. This class resets it to `null` and sets `compose.application.resources.dir` to the
 * directory holding the platform's DeckLink JNI library — chosen by the same `os.name` split
 * `isAvailable()` itself uses, so a card fitted to a Linux or macOS box is reachable too — giving
 * `isAvailable()` a fresh chance to load it; both are restored afterwards so
 * [DeckLinkManagerTest]'s assumptions still hold if it runs later in the same JVM.
 *
 * The `println`s below are the point of a manual harness rather than stray debug output — what a
 * card reported is the result you run this for — and are the same exemption the PresentationEngine's
 * `dumpKeynote`/`dumpTiming` CLI tasks carry in DEVELOPMENT_GUIDE.md.
 */
class DeckLinkHardwareTest {

    private var realHome: String? = null
    private var tempHome: File? = null
    private var savedAvailable: Any? = SENTINEL
    private var savedResDir: String? = null

    /** Lazy so that with the flag off this class never even loads [DeckLinkManager]. */
    private val availableField by lazy {
        DeckLinkManager::class.java.getDeclaredField("available").apply { isAccessible = true }
    }

    @BeforeTest
    fun setUp() {
        if (!HARDWARE_ENABLED) return
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-decklink-hw").toFile()
        System.setProperty("user.home", tempHome!!.absolutePath)

        savedAvailable = availableField.get(DeckLinkManager)
        savedResDir = System.getProperty("compose.application.resources.dir")

        // Reset the memoized availability and point at the native library's directory.
        // Gradle's test working dir may differ from the project root, so walk up to find it.
        availableField.set(DeckLinkManager, null)
        // The same os.name split DeckLinkManager.isAvailable() uses to choose the library name,
        // kept in step with it so the harness looks for the file the loader will actually load.
        // The repo ships all three, so a card on Linux or macOS is reachable too.
        val osName = System.getProperty("os.name").lowercase()
        val (platformDir, libName) = when {
            osName.contains("win") -> "windows" to "decklink_jni.dll"
            osName.contains("mac") -> "macos" to "libdecklink_jni.dylib"
            else -> "linux" to "libdecklink_jni.so"
        }
        val candidates = listOf(
            File("composeApp/src/jvmMain/appResources/$platformDir"),
            File("src/jvmMain/appResources/$platformDir"),
            File("../composeApp/src/jvmMain/appResources/$platformDir"),
        )
        val libDir = candidates.firstOrNull { File(it, libName).exists() }
        if (libDir != null) {
            System.setProperty("compose.application.resources.dir", libDir.absolutePath)
        } else {
            println("[DeckLinkHardwareTest] Could not find $libName; cwd=${File(".").absolutePath}")
        }
    }

    @AfterTest
    fun tearDown() {
        if (!HARDWARE_ENABLED) return
        // Restore the memoized field so guard-clause tests in DeckLinkManagerTest still work
        if (savedAvailable !== SENTINEL) {
            availableField.set(DeckLinkManager, savedAvailable)
        }
        if (savedResDir != null) {
            System.setProperty("compose.application.resources.dir", savedResDir!!)
        } else {
            System.clearProperty("compose.application.resources.dir")
        }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome?.deleteRecursively()
    }

    private fun assumeAvailable(): Boolean {
        if (!HARDWARE_ENABLED) return false
        if (!DeckLinkManager.isAvailable()) {
            println("[DeckLinkHardwareTest] Native library not loadable — skipping hardware test")
            return false
        }
        return true
    }

    // ── Device enumeration ────────────────────────────────────────────────────────

    @Test
    fun `listDevices returns at least one device when hardware is present`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        assertTrue(devices.isNotEmpty(), "DeckLink card is installed but listDevices() returned empty")
        println("[DeckLinkHardwareTest] Found ${devices.size} device(s): ${devices.map { "${it.index}=${it.name}" }}")
    }

    @Test
    fun `listDevices returns devices with sequential indices starting from 0`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        if (devices.isEmpty()) return
        assertEquals(0, devices.first().index)
        devices.forEachIndexed { i, dev -> assertEquals(i, dev.index) }
    }

    @Test
    fun `device names are non-blank`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        devices.forEach { assertTrue(it.name.isNotBlank(), "Device ${it.index} has a blank name") }
    }

    // ── Output info ───────────────────────────────────────────────────────────────

    @Test
    fun `getOutputInfo returns dimensions for the first device before opening`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        if (devices.isEmpty()) return
        // Some cards report output info even before open(); others return null — both are valid.
        val info = DeckLinkManager.getOutputInfo(devices.first().index)
        if (info != null) {
            assertTrue(info.width > 0, "Output width should be positive")
            assertTrue(info.height > 0, "Output height should be positive")
            assertTrue(info.fps > 0, "FPS should be positive")
            println("[DeckLinkHardwareTest] Output info: ${info.width}x${info.height} @ ${info.fps} fps")
        } else {
            println("[DeckLinkHardwareTest] getOutputInfo returned null before open (expected for some cards)")
        }
    }

    // ── Input modes ───────────────────────────────────────────────────────────────

    @Test
    fun `listInputModes returns at least one mode for the first device`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        if (devices.isEmpty()) return
        val modes = DeckLinkManager.listInputModes(devices.first().index)
        assertTrue(modes.isNotEmpty(), "DeckLink card should report at least one input mode")
        modes.forEach {
            assertTrue(it.name.isNotBlank(), "Input mode name should not be blank")
        }
        println("[DeckLinkHardwareTest] Input modes: ${modes.map { it.name }}")
    }

    // ── Video connections ─────────────────────────────────────────────────────────

    @Test
    fun `listVideoConnections returns at least one connection for the first device`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        if (devices.isEmpty()) return
        val conns = DeckLinkManager.listVideoConnections(devices.first().index)
        assertTrue(conns.isNotEmpty(), "DeckLink card should report at least one video connection")
        conns.forEach {
            assertTrue(it.name.isNotBlank(), "Connection name should not be blank")
        }
        println("[DeckLinkHardwareTest] Video connections: ${conns.map { "${it.name}(${it.value})" }}")
    }

    // ── Output connections ────────────────────────────────────────────────────────

    @Test
    fun `listOutputConnections returns connections or handles missing native symbol`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        if (devices.isEmpty()) return
        try {
            val conns = DeckLinkManager.listOutputConnections(devices.first().index)
            println("[DeckLinkHardwareTest] Output connections: ${conns.map { "${it.name}(${it.value})" }}")
        } catch (_: UnsatisfiedLinkError) {
            println("[DeckLinkHardwareTest] nativeListOutputConnections not in DLL — symbol not compiled")
        }
    }

    // ── Device status ─────────────────────────────────────────────────────────────

    @Test
    fun `getDeviceStatus returns a status or handles missing native symbol`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        if (devices.isEmpty()) return
        try {
            val status = DeckLinkManager.getDeviceStatus(devices.first().index)
            if (status != null) {
                println(
                    "[DeckLinkHardwareTest] Status: signalLocked=${status.signalLocked}, busy=${status.busy}, modeCod" +
                        "e=${status.detectedModeCode}"
                )
            } else {
                println("[DeckLinkHardwareTest] getDeviceStatus returned null")
            }
        } catch (_: UnsatisfiedLinkError) {
            println("[DeckLinkHardwareTest] nativeGetDeviceStatus not in DLL — symbol not compiled")
        }
    }

    // ── Open / send / close cycle ─────────────────────────────────────────────────

    @Test
    fun `open send black frame and close completes without error`() {
        if (!assumeAvailable()) return
        val devices = DeckLinkManager.listDevices()
        if (devices.isEmpty()) return
        val idx = devices.first().index
        val opened = DeckLinkManager.open(idx)
        if (!opened) {
            println("[DeckLinkHardwareTest] Could not open device $idx for output (may be busy)")
            return
        }
        try {
            assertTrue(DeckLinkManager.isOutputActive(idx))
            val info = DeckLinkManager.getOutputInfo(idx)
            val w = info?.width ?: 1920
            val h = info?.height ?: 1080
            DeckLinkManager.sendFrame(idx, IntArray(w * h), w, h)
            println("[DeckLinkHardwareTest] Sent one black frame to device $idx at ${w}x${h}")
        } finally {
            DeckLinkManager.close(idx)
        }
    }

    companion object {
        private val SENTINEL = Any()

        /**
         * Opt-in switch, forwarded from `-PdecklinkHardware=true` by the `Test` task config in
         * `composeApp/build.gradle.kts`. Absent — which is every CI run and every ordinary local
         * run — this whole class is inert.
         */
        private val HARDWARE_ENABLED: Boolean =
            System.getProperty("churchpresenter.decklinkHardware") == "true"
    }
}

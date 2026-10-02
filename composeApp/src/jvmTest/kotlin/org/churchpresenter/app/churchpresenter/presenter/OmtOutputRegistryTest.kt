package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.omt.FakeOmtLibrary
import org.churchpresenter.omt.OmtQuality
import org.churchpresenter.omt.OmtRuntimeHost
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.sharedui.models.Presenting

private const val LIB_PATH = "/opt/omt/libomt.dylib"
private const val WAIT_MS = 4_000L

/**
 * Which renderer answers for which OMT output index, and what the Canvas gets over the same library.
 *
 * Runs over `:omt`'s fake through the host's constructor seam — no library loaded, nothing global.
 */
class OmtOutputRegistryTest {

    /** Cancelled after each test: a started pump renders forever, and an uncancelled one leaks. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun registry(lib: FakeOmtLibrary? = FakeOmtLibrary(), path: String? = LIB_PATH) =
        OmtOutputRegistry(
            OmtRuntimeHost(locate = { _, _ -> path }, loader = { lib }, discoveryServiceAvailable = { true }),
        )

    private fun context() = OffscreenOutputContext(
        presenterManager = PresenterManager(),
        appSettingsState = mutableStateOf(AppSettings()),
        screenAssignmentState = mutableStateOf(ScreenAssignment()),
        effectiveModeState = mutableStateOf(Presenting.NONE),
        kind = OffscreenOutputKind.OMT,
    )

    /** 8x8, because nothing here reads a pixel and a 1080p tick allocates 8 MB. */
    private fun OmtOutputRegistry.add(index: Int, assignment: ScreenAssignment = ScreenAssignment()) =
        createRenderer(
            index = index,
            assignment = assignment.copy(omtWidth = 8, omtHeight = 8),
            context = context(),
            screenAssignmentState = mutableStateOf(assignment),
            name = "Output $index",
            product = "ChurchPresenter",
            version = "9.9",
        )

    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(2)
        }
    }

    @Test
    fun `nothing is available before the library starts`() {
        val r = registry()
        assertEquals(OmtRuntimeStatus.NotInstalled, r.status.value)
        assertNull(r.add(0))
        assertNull(r.createReceiver("x", preview = false))
        assertTrue(r.discoverSources().isEmpty())
        assertEquals(0, r.size)
    }

    @Test
    fun `ensureStarted publishes what it found`() {
        val r = registry()
        val status = r.ensureStarted(bundledDir = "/opt/omt", logFile = "/l.log", discoveryServer = "omt://s:1")
        assertIs<OmtRuntimeStatus.Ready>(status)
        assertEquals(status, r.status.value)
        assertEquals(OmtRuntimeStatus.LoadFailed(LIB_PATH), registry(lib = null).ensureStarted())
    }

    @Test
    fun `a renderer is created with the output's mode, quality and what made it`() {
        val lib = FakeOmtLibrary()
        val r = registry(lib).apply { ensureStarted() }
        val renderer = r.add(0, ScreenAssignment(omtQuality = Constants.OMT_QUALITY_HIGH))
        assertNotNull(renderer).start(scope)
        waitFor("the sender to open") { lib.created.isNotEmpty() }
        assertEquals(listOf("Output 0"), lib.created)
        assertEquals(listOf(OmtQuality.HIGH), lib.qualities)
        assertEquals(listOf(Triple("ChurchPresenter", "ChurchPresenter", "9.9")), lib.senderInformation)
        assertTrue(r.hasRenderer(0))
        assertFalse(r.hasRenderer(1))
    }

    @Test
    fun `replacing an output at the same index stops the old renderer`() {
        val lib = FakeOmtLibrary()
        val r = registry(lib).apply { ensureStarted() }
        assertNotNull(r.add(0)).start(scope)
        waitFor("the first sender") { lib.created.size == 1 }
        r.add(0)
        assertEquals(1, lib.destroyed.size, "the old source left the network")
        assertEquals(1, r.size)
    }

    @Test
    fun `releasing a superseded renderer leaves the live one registered`() {
        val r = registry().apply { ensureStarted() }
        val old = assertNotNull(r.add(0))
        val live = assertNotNull(r.add(0))
        r.release(0, old)
        assertTrue(r.hasRenderer(0))
        r.release(0, live)
        assertFalse(r.hasRenderer(0))
    }

    @Test
    fun `counts and names come from the renderer at that index, and nothing from an empty one`() {
        val lib = FakeOmtLibrary().apply { connections = 2 }
        val r = registry(lib).apply { ensureStarted() }
        assertEquals(0, r.receiverCount(5))
        assertEquals("", r.addressOf(5))
        assertNotNull(r.add(0)).start(scope)
        waitFor("the sender to open") { lib.created.isNotEmpty() }
        assertEquals(2, r.receiverCount(0))
        assertEquals("FAKEHOST (Output 0)", r.addressOf(0))
    }

    @Test
    fun `the Canvas gets receivers and discovery over the same library`() {
        val lib = FakeOmtLibrary().apply { discovered = listOf("HOST (Cam)") }
        val r = registry(lib).apply { ensureStarted() }
        assertEquals(listOf("HOST (Cam)"), r.discoverSources())
        assertTrue(assertNotNull(r.createReceiver("HOST (Cam)", preview = true)).open())
        assertEquals(listOf("HOST (Cam)" to true), lib.receiversCreated)
    }

    @Test
    fun `stopping all takes every source off the network`() {
        val lib = FakeOmtLibrary()
        val r = registry(lib).apply { ensureStarted() }
        assertNotNull(r.add(0)).start(scope)
        assertNotNull(r.add(1)).start(scope)
        waitFor("both senders") { lib.created.size == 2 }
        r.stopAll()
        assertEquals(2, lib.destroyed.size)
        assertEquals(0, r.size)
    }
}

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.ndi.FakeNdiLibrary
import org.churchpresenter.ndi.NdiRuntimeHost
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LIB_PATH = "/opt/ndi/libndi.dylib"

/**
 * Which renderer answers for which output index.
 *
 * This is where a "No receivers" report lands — the count is only ever as right as the bookkeeping
 * under it — and none of it was reachable while it lived inside the `NdiManager` object, which
 * hardcoded its own runtime host. The host is now a constructor parameter, so the whole thing runs
 * over `:ndi`'s fake with no NDI installed.
 */
class NdiOutputRegistryTest {

    /**
     * The scope every renderer here is started on, cancelled when the class finishes.
     *
     * Not optional and not tidiness: a started [ComposeScenePump] renders and reads back a full
     * frame every tick forever. Started on an uncancelled scope it outlives the test, and the
     * accumulated readbacks exhausted the heap — which surfaced as `OutOfMemoryError` inside two
     * unrelated screenshot suites that happened to be running in the same fork when it ran out.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun registry(lib: FakeNdiLibrary = FakeNdiLibrary()) =
        NdiOutputRegistry(NdiRuntimeHost(locate = { LIB_PATH }, loader = { lib }))

    private fun context() = OffscreenOutputContext(
        presenterManager = PresenterManager(),
        appSettingsState = mutableStateOf(AppSettings()),
        screenAssignmentState = mutableStateOf(ScreenAssignment()),
        effectiveModeState = mutableStateOf(Presenting.NONE),
        kind = OffscreenOutputKind.NDI,
    )

    /** 8x8, because nothing here reads a pixel and a 1080p tick allocates 8 MB. */
    private val tiny = ScreenAssignment(ndiWidth = 8, ndiHeight = 8)

    private fun NdiOutputRegistry.add(index: Int, name: String = "Output $index") = createRenderer(
        index = index,
        assignment = tiny,
        context = context(),
        screenAssignmentState = mutableStateOf(ScreenAssignment()),
        name = name,
    )

    // ── Starting the runtime ────────────────────────────────────────────────────

    @Test
    fun `nothing is registered before the runtime starts`() {
        val r = registry()
        assertNull(r.add(0), "a renderer cannot exist before the runtime is ready")
        assertEquals(0, r.size)
        assertFalse(r.hasRenderer(0))
    }

    @Test
    fun `ensureStarted publishes the status it found`() {
        val r = registry()
        assertEquals(NdiRuntimeStatus.NotInstalled, r.status.value, "nothing is assumed before looking")
        assertTrue(r.ensureStarted().isReady)
        assertTrue(r.status.value.isReady, "the card reads this, so it has to move")
    }

    @Test
    fun `a runtime that will not load leaves nothing registered`() {
        val r = NdiOutputRegistry(NdiRuntimeHost(locate = { LIB_PATH }, loader = { null }))
        assertEquals(NdiRuntimeStatus.LoadFailed(LIB_PATH), r.ensureStarted())
        assertNull(r.add(0))
    }

    // ── The index bookkeeping — what a "No receivers" report turns on ───────────

    @Test
    fun `a renderer answers for its own index and no other`() {
        val lib = FakeNdiLibrary()
        lib.connections = 3
        val r = registry(lib)
        r.ensureStarted()
        assertNotNull(r.add(1))
        r.add(1)?.start(scope)

        assertTrue(r.hasRenderer(1))
        assertFalse(r.hasRenderer(0), "an index with no output must not borrow another's renderer")
        assertEquals(0, r.connectionCount(0), "and must report nothing rather than someone else's count")
    }

    @Test
    fun `several outputs keep their own renderers`() {
        val r = registry()
        r.ensureStarted()
        val a = assertNotNull(r.add(0, "First"))
        val b = assertNotNull(r.add(1, "Second"))

        assertEquals(2, r.size)
        assertTrue(a !== b)
    }

    @Test
    fun `replacing an output at the same index stops the old renderer`() {
        // A resolution or mode change arrives here as a new renderer for the same index; leaving the
        // old one open would keep a stale source advertised alongside the new one.
        val lib = FakeNdiLibrary()
        val r = registry(lib)
        r.ensureStarted()
        val first = assertNotNull(r.add(0, "First"))
        first.start(scope)
        assertEquals(1, lib.created.size)

        assertNotNull(r.add(0, "Second"))

        assertEquals(1, r.size, "still one output at index 0")
        assertEquals(1, lib.destroyed.size, "the old sender was taken off the network")
    }

    @Test
    fun `releasing the renderer that is registered deregisters it`() {
        val r = registry()
        r.ensureStarted()
        val renderer = assertNotNull(r.add(0))

        r.release(0, renderer)

        assertFalse(r.hasRenderer(0))
        assertEquals(0, r.connectionCount(0))
    }

    @Test
    fun `releasing a superseded renderer leaves the live one registered`() {
        // The ordering that made this conditional necessary: Compose creates the replacement before
        // disposing the one it replaced, so the old renderer's dispose arrives last. An
        // unconditional remove here would deregister the live output — and the card would then
        // report "No receivers" for a source that is on the network.
        val r = registry()
        r.ensureStarted()
        val old = assertNotNull(r.add(0, "Old"))
        val new = assertNotNull(r.add(0, "New"))

        r.release(0, old)
        assertTrue(r.hasRenderer(0), "the replacement must survive the old one's disposal")

        // And it is specifically `new` that is registered: releasing that one does deregister it,
        // which releasing `old` did not.
        r.release(0, new)
        assertFalse(r.hasRenderer(0))
    }

    // ── Receiver counts ─────────────────────────────────────────────────────────

    @Test
    fun `an index with no output reports no receivers rather than throwing`() {
        assertEquals(0, registry().connectionCount(7))
    }

    @Test
    fun `a registered but unstarted output reports no receivers`() {
        // The sender is only on the network once the renderer starts, so before that there is
        // genuinely nobody watching — 0 here is correct, not a bug.
        val lib = FakeNdiLibrary()
        lib.connections = 5
        val r = registry(lib)
        r.ensureStarted()
        r.add(0)
        assertEquals(0, r.connectionCount(0))
    }

    @Test
    fun `a started output reports what the runtime says`() {
        val lib = FakeNdiLibrary()
        lib.connections = 4
        val r = registry(lib)
        r.ensureStarted()
        val renderer = assertNotNull(r.add(0))
        renderer.start(scope)

        assertEquals(4, r.connectionCount(0), "this is the number the settings card shows")
    }

    // ── Shutdown ────────────────────────────────────────────────────────────────

    @Test
    fun `stopAll takes every source off the network and forgets them`() {
        val lib = FakeNdiLibrary()
        val r = registry(lib)
        r.ensureStarted()
        r.add(0, "A")?.start(scope)
        r.add(1, "B")?.start(scope)

        r.stopAll()

        assertEquals(0, r.size)
        assertEquals(2, lib.destroyed.size, "both senders were destroyed, not just forgotten")
    }

    @Test
    fun `stopAll on an empty registry is harmless`() {
        registry().stopAll()
    }
}

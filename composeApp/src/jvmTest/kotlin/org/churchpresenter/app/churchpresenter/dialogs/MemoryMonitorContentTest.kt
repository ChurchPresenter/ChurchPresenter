@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.every
import io.mockk.mockk
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryMonitorContentTest {

    private val mb = 1024L * 1024L

    private fun dialog(
        heapUsed: Long = 100L * mb,
        heapCommitted: Long = 200L * mb,
        heapMax: Long = 500L * mb,
        nonHeapUsed: Long = 50L * mb,
        nonHeapCommitted: Long = 80L * mb,
        gcCount: Long = 5L,
        gcTimeMs: Long = 120L,
        history: List<Long> = listOf(10L * mb, 20L * mb, 30L * mb),
        block: ComposeUiTest.(forceGcCalls: () -> Int) -> Unit,
    ) {
        var forceGcCalls = 0
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MemoryMonitorDialogContent(
                        heapUsed = heapUsed,
                        heapCommitted = heapCommitted,
                        heapMax = heapMax,
                        nonHeapUsed = nonHeapUsed,
                        nonHeapCommitted = nonHeapCommitted,
                        gcCount = gcCount,
                        gcTimeMs = gcTimeMs,
                        history = history,
                        onForceGc = { forceGcCalls++ },
                    )
                }
            }
            block { forceGcCalls }
        }
    }

    @Test
    fun `heap figures are shown formatted in megabytes`() = dialog {
        onNodeWithText("100 MB").assertExists()
        onNodeWithText("200 MB").assertExists()
        onNodeWithText("500 MB").assertExists()
    }

    @Test
    fun `non-heap figures are shown formatted in megabytes`() = dialog {
        onNodeWithText("50 MB").assertExists()
        onNodeWithText("80 MB").assertExists()
    }

    @Test
    fun `an unknown heap max is shown as a dash rather than 0 MB`() = dialog(heapMax = 0L) {
        onNodeWithText("—").assertExists()
    }

    @Test
    fun `gc count and time are shown together`() = dialog {
        onNodeWithText("5 (120 ms)").assertExists()
    }

    @Test
    fun `clicking Force GC calls the handler`() = dialog { forceGcCalls ->
        onNodeWithText("Force GC").performClick()
        assertEquals(1, forceGcCalls())
    }

    @Test
    fun `MemoryMonitorWindow renders nothing when not visible`() = runComposeUiTest {
        setContent {
            MemoryMonitorWindow(isVisible = false, theme = ThemeMode.LIGHT, onClose = {})
        }
        onNodeWithText("Force GC").assertDoesNotExist()
    }

    // ── readMemorySnapshot ──────────────────────────────────────────────────────

    @Test
    fun `a snapshot of the real JVM reports sane, non-negative figures`() {
        val snapshot = readMemorySnapshot(
            ManagementFactory.getMemoryMXBean(),
            ManagementFactory.getGarbageCollectorMXBeans(),
        )

        assertTrue(snapshot.heapUsed > 0, "the JVM this test runs in has allocated some heap")
        assertTrue(snapshot.heapCommitted >= snapshot.heapUsed)
        assertTrue(snapshot.nonHeapUsed >= 0)
        assertTrue(snapshot.gcCount >= 0)
        assertTrue(snapshot.gcTimeMs >= 0)
    }

    private fun fakeGcBean(count: Long, time: Long): GarbageCollectorMXBean {
        val bean = mockk<GarbageCollectorMXBean>()
        every { bean.collectionCount } returns count
        every { bean.collectionTime } returns time
        return bean
    }

    @Test
    fun `gc totals sum across every collector`() {
        val snapshot = readMemorySnapshot(
            ManagementFactory.getMemoryMXBean(),
            listOf(fakeGcBean(count = 3, time = 40), fakeGcBean(count = 5, time = 60)),
        )

        assertEquals(8, snapshot.gcCount)
        assertEquals(100, snapshot.gcTimeMs)
    }

    @Test
    fun `a collector reporting a negative count or time counts as zero rather than going negative`() {
        val snapshot = readMemorySnapshot(
            ManagementFactory.getMemoryMXBean(),
            listOf(fakeGcBean(count = -1, time = -1), fakeGcBean(count = 4, time = 25)),
        )

        assertEquals(4, snapshot.gcCount, "the unsupported collector must not subtract from the total")
        assertEquals(25, snapshot.gcTimeMs)
    }

    // ── appendSample ────────────────────────────────────────────────────────────

    @Test
    fun `appendSample grows the history until the cap is reached`() {
        val history = mutableListOf<Long>()
        appendSample(history, 1L, maxSamples = 3)
        appendSample(history, 2L, maxSamples = 3)

        assertEquals(listOf(1L, 2L), history)
    }

    @Test
    fun `appendSample evicts the oldest sample once past the cap`() {
        val history = mutableListOf(1L, 2L, 3L)
        appendSample(history, 4L, maxSamples = 3)

        assertEquals(listOf(2L, 3L, 4L), history)
    }

    @Test
    fun `appendSample never lets history exceed the cap even by more than one sample`() {
        val history = mutableListOf(1L, 2L, 3L, 4L, 5L)
        appendSample(history, 6L, maxSamples = 3)

        assertEquals(listOf(4L, 5L, 6L), history)
    }

    // ── MemoryMonitorContent's live polling loop ───────────────────────────────

    private fun ComposeUiTest.countOf(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    @Test
    fun `MemoryMonitorContent shows a real heap reading after its first poll`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MemoryMonitorContent()
            }
        }

        val deadline = System.currentTimeMillis() + 5_000
        var updated = false
        while (System.currentTimeMillis() < deadline && !updated) {
            repeat(3) { SwingUtilities.invokeAndWait { } }
            waitForIdle()
            updated = countOf("0 MB") < 4
            if (!updated) Thread.sleep(10)
        }

        assertTrue(updated, "the first poll must replace at least one initial 0 MB placeholder with a real reading")
        onNodeWithText("Force GC").assertExists()
    }
}

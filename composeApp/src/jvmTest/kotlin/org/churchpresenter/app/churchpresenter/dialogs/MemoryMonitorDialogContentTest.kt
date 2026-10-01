@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryMonitorDialogContentTest {

    private class Calls {
        var forceGcCount = 0
    }

    @OptIn(ExperimentalTestApi::class)
    private fun memoryMonitor(
        heapUsed: Long = 0L,
        heapCommitted: Long = 0L,
        heapMax: Long = 0L,
        nonHeapUsed: Long = 0L,
        nonHeapCommitted: Long = 0L,
        gcCount: Long = 0L,
        gcTimeMs: Long = 0L,
        history: List<Long> = emptyList(),
        block: ComposeUiTest.(Calls) -> Unit,
    ) = runComposeUiTest {
        val calls = Calls()
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
                    onForceGc = { calls.forceGcCount++ },
                )
            }
        }
        block(calls)
    }

    private fun mb(n: Long) = n * 1024L * 1024L

    private fun ComposeUiTest.progressFraction(): Float {
        val node = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNode()
        return node.config[SemanticsProperties.ProgressBarRangeInfo].current
    }

    // ── Heap stats ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the heap used committed and max stats are formatted in MB, with thousands separators`() =
        memoryMonitor(heapUsed = mb(100), heapCommitted = mb(1500), heapMax = mb(2000)) { _ ->
            onNodeWithText("100 MB").assertIsDisplayed()
            onNodeWithText("1,500 MB").assertIsDisplayed()
            onNodeWithText("2,000 MB").assertIsDisplayed()
        }

    @Test
    fun `a heap max of zero shows an em dash instead of a value`() =
        memoryMonitor(heapUsed = mb(50), heapMax = 0L) { _ ->
            onNodeWithText("—").assertIsDisplayed()
        }

    @Test
    fun `the progress bar reflects used over max`() =
        memoryMonitor(heapUsed = mb(250), heapMax = mb(500)) { _ ->
            assertEquals(0.5f, progressFraction())
        }

    @Test
    fun `the progress bar clamps to 1 when used exceeds max`() =
        memoryMonitor(heapUsed = mb(900), heapMax = mb(500)) { _ ->
            assertEquals(1f, progressFraction())
        }

    @Test
    fun `the progress bar reads 0 when max is unknown`() =
        memoryMonitor(heapUsed = mb(250), heapMax = 0L) { _ ->
            assertEquals(0f, progressFraction())
        }

    // ── Non-heap stats ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the non-heap used and committed stats are formatted in MB`() =
        memoryMonitor(nonHeapUsed = mb(64), nonHeapCommitted = mb(96)) { _ ->
            onNodeWithText("64 MB").assertIsDisplayed()
            onNodeWithText("96 MB").assertIsDisplayed()
        }

    // ── GC row ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the GC row shows the collection count and total time`() =
        memoryMonitor(gcCount = 7L, gcTimeMs = 1234L) { _ ->
            onNodeWithText("7 (1234 ms)").assertIsDisplayed()
        }

    // ── Force GC ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the Force GC button invokes the callback`() = memoryMonitor { calls ->
        onNodeWithText("Force GC").performClick()
        waitForIdle()
        assertEquals(1, calls.forceGcCount)
    }

    // ── Static content ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the native-memory note is shown`() = memoryMonitor { _ ->
        onNodeWithText("native memory not shown", substring = true).assertIsDisplayed()
    }
}

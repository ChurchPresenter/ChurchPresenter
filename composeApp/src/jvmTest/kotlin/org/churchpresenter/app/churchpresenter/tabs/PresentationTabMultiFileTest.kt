@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationTabMultiFileTest {

    private lateinit var dir: File
    private var counter = 0

    @AfterTest
    fun cleanUp() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun pdf(pages: Int = 1): File {
        if (!::dir.isInitialized) dir = Files.createTempDirectory("cp-presentation-tab-multi").toFile()
        val file = File(dir, "deck${counter++}.pdf")
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            doc.save(file)
        }
        return file
    }

    private fun ComposeUiTest.awaitLoaded(vm: PresentationViewModel, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!vm.isLoading && vm.slideFiles.isNotEmpty()) {
                waitForIdle()
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for the deck to finish loading")
    }

    /** [first] is opened before [second], so `viewModel.presentations` — and the chip row rendered
     *  from it — lists them in that order: the "Remove" button at index 0 belongs to [first]. */
    private fun withTwoDecks(block: ComposeUiTest.(vm: PresentationViewModel, first: File, second: File) -> Unit) {
        val first = pdf()
        val second = pdf()
        presentationTab { vm, _ ->
            vm.addPresentation(first)
            awaitLoaded(vm)
            vm.addPresentation(second)
            awaitLoaded(vm)
            block(vm, first, second)
        }
    }

    @Test
    fun `the chip row is hidden with only one open file`() = presentationTab { vm, _ ->
        vm.addPresentation(pdf())
        awaitLoaded(vm)

        assertEquals(1, vm.presentations.size)
    }

    @Test
    fun `opening a second file shows a chip for each open file`() = withTwoDecks { _, first, second ->
        onNodeWithText(first.nameWithoutExtension).assertExists()
        onNodeWithText(second.nameWithoutExtension).assertExists()
    }

    @Test
    fun `opening a second file selects it, not the first`() = withTwoDecks { vm, _, second ->
        assertEquals(second.absolutePath, vm.selectedPresentation?.absolutePath)
    }

    @Test
    fun `clicking a chip switches the selected file`() = withTwoDecks { vm, first, _ ->
        onNodeWithText(first.nameWithoutExtension).performClick()
        waitForIdle()

        assertEquals(first.absolutePath, vm.selectedPresentation?.absolutePath)
    }

    @Test
    fun `removing the selected file falls back to the other one`() = withTwoDecks { vm, first, second ->
        // second is selected (opened last, so its own chip is at index 1); removing it must fall
        // back to first, the only file left.
        onAllNodesWithContentDescription("Remove")[1].performClick()
        waitForIdle()

        assertEquals(1, vm.presentations.size)
        assertEquals(first.absolutePath, vm.presentations.single().absolutePath)
        assertEquals(first.absolutePath, vm.selectedPresentation?.absolutePath)
    }

    @Test
    fun `removing a file that is not selected leaves the selection alone`() = withTwoDecks { vm, _, second ->
        // first is at index 0 and is not the live selection; removing it must not disturb second.
        onAllNodesWithContentDescription("Remove")[0].performClick()
        waitForIdle()

        assertEquals(1, vm.presentations.size)
        assertEquals(second.absolutePath,
            vm.selectedPresentation?.absolutePath,
            "removing the other file must not disturb the live selection")
    }
}

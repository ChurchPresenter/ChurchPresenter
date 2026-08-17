@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresentationTabRealDeckTest {

    private lateinit var dir: File
    private var counter = 0

    @AfterTest
    fun cleanUp() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun pdf(pages: Int = 3): File {
        if (!::dir.isInitialized) dir = Files.createTempDirectory("cp-presentation-tab-real").toFile()
        val file = File(dir, "deck${counter++}.pdf")
        PDDocument().use { doc ->
            repeat(pages) { index ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                    stream.newLineAtOffset(72f, 500f)
                    stream.showText("Slide ${index + 1}")
                    stream.endText()
                }
            }
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
            Thread.sleep(50)
        }
        throw AssertionError("timed out waiting for the deck to finish loading")
    }

    private fun withRealDeck(
        pages: Int = 3,
        presenterManager: PresenterManager? = null,
        onInstanceLinkSendProject: ((ScheduleItem) -> Unit)? = null,
        block: ComposeUiTest.(vm: PresentationViewModel, reports: PresentationReports, file: File) -> Unit,
    ) {
        val file = pdf(pages)
        presentationTab(
            presenterManager = presenterManager,
            onInstanceLinkSendProject = onInstanceLinkSendProject,
        ) { vm, reports ->
            vm.addPresentation(file)
            awaitLoaded(vm)
            block(vm, reports, file)
        }
    }

    @Test
    fun `opening a real deck selects it and shows its file name`() = withRealDeck(pages = 3) { vm, _, file ->
        assertEquals(file.absolutePath, vm.selectedPresentation?.absolutePath)
        assertEquals(3, vm.slideFiles.size)
        assertTrue(showsContainingText(file.name))
    }

    @Test
    fun `adding a loaded deck to the schedule reports its real file details`() =
        withRealDeck(pages = 4) { vm, reports, file ->
        presentationButton(PresentationLabel.ADD_TO_SCHEDULE).performClick()
        waitForIdle()

        assertEquals(
            listOf("${file.absolutePath}:${file.nameWithoutExtension}:4:pdf"),
            reports.scheduled,
        )
    }

    @Test
    fun `clear removes the loaded deck and reports to the host`() = withRealDeck(pages = 2) { vm, reports, _ ->
        presentationButton(PresentationLabel.CLEAR).performClick()
        waitForIdle()

        assertEquals(1, reports.clears)
        assertTrue(vm.slideFiles.isEmpty(), "clearing must actually drop the slides, not just notify the host")
    }

    @Test
    fun `Go Live puts the presenter into PRESENTATION mode and opens the output window`() {
        val presenter = PresenterManager()
        withRealDeck(pages = 3, presenterManager = presenter) { _, _, _ ->
            presentationButton(PresentationLabel.GO_LIVE).performClick()
            waitForIdle()

            assertEquals(Presenting.PRESENTATION, presenter.presentingMode.value)
            assertTrue(presenter.showPresenterWindow.value)
        }
    }

    @Test
    fun `Go Live eventually pushes the selected slide's bitmap to the presenter`() {
        val presenter = PresenterManager()
        withRealDeck(pages = 3, presenterManager = presenter) { _, _, _ ->
            presentationButton(PresentationLabel.GO_LIVE).performClick()

            val deadline = System.currentTimeMillis() + 5_000
            while (presenter.selectedSlide.value == null && System.currentTimeMillis() < deadline) {
                waitForIdle()
                Thread.sleep(20)
            }

            assertTrue(
                presenter.selectedSlide.value != null,
                "the live slide bitmap must arrive, even though it decodes asynchronously",
            )
        }
    }

    @Test
    fun `Go Live also projects the deck to Instance Link when wired`() {
        val presenter = PresenterManager()
        val sent = mutableListOf<ScheduleItem>()
        withRealDeck(
            pages = 3,
            presenterManager = presenter,
            onInstanceLinkSendProject = { sent += it },
        ) { vm, _, file ->
            presentationButton(PresentationLabel.GO_LIVE).performClick()
            waitForIdle()

            val item = sent.single() as ScheduleItem.PresentationItem
            assertEquals(file.absolutePath, item.filePath)
            assertEquals(file.nameWithoutExtension, item.fileName)
            assertEquals(vm.slideFiles.size, item.slideCount)
            assertEquals("pdf", item.fileType)
        }
    }

    @Test
    fun `double-clicking a thumbnail also projects to Instance Link when wired`() {
        val presenter = PresenterManager()
        val sent = mutableListOf<ScheduleItem>()
        withRealDeck(
            pages = 3,
            presenterManager = presenter,
            onInstanceLinkSendProject = { sent += it },
        ) { _, _, file ->
            waitUntilAtLeastOneExists(hasContentDescription("Slide 2"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Slide 2").performTouchInput {
                down(center)
                up()
                advanceEventTime(50)
                down(center)
                up()
            }
            waitForIdle()

            val item = sent.single() as ScheduleItem.PresentationItem
            assertEquals(file.absolutePath, item.filePath)
        }
    }
}

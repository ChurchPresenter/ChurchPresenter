@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationTabScheduleItemTest {

    private lateinit var dir: File

    @AfterTest
    fun cleanUp() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private fun pdf(pages: Int = 2): File {
        dir = Files.createTempDirectory("cp-presentation-tab-item").toFile()
        val file = File(dir, "deck.pdf")
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            doc.save(file)
        }
        return file
    }

    private fun jpegBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(16, 9, BufferedImage.TYPE_INT_RGB), "jpg", out)
        return out.toByteArray()
    }

    /**
     * Waits for the load to *finish*, then leaves the caller to assert what it produced.
     *
     * Every load path — cached, freshly rendered and remote — bumps `loadGeneration` once, on the
     * main thread, after its whole slide loop has run, and sets `loadError` when it produced
     * nothing. Either one is a positive signal that the loader is done; the earlier waiter instead
     * watched `!isLoading && slideFiles.size == n`, a pair that is also true before the loader has
     * started and that its own comment admitted could settle on a short list and stay there.
     *
     * A load that finished having produced nothing fails here immediately, naming the error,
     * rather than as an ambiguous timeout five seconds later.
     *
     * The timeout only fails the test — it is never the success path.
     */
    private fun ComposeUiTest.awaitLoaded(vm: PresentationViewModel, slides: Int, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            vm.loadError?.let { throw AssertionError("load failed before producing $slides slides: $it") }
            if (vm.loadGeneration > 0) {
                waitForIdle()
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError(
            "timed out waiting for the load to finish: loaded ${vm.slideFiles.size}, " +
                "isLoading=${vm.isLoading}, loadError=${vm.loadError}"
        )
    }

    @Test
    fun `a schedule item whose file exists locally loads it directly`() {
        val file = pdf(pages = 2)
        presentationTab(
            selectedPresentationItem = ScheduleItem.PresentationItem(
                id = "item-1",
                filePath = file.absolutePath,
                fileName = file.nameWithoutExtension,
                slideCount = 2,
                fileType = "pdf",
            ),
        ) { vm, _ ->
            awaitLoaded(vm, slides = 2)

            assertEquals(file.absolutePath, vm.selectedPresentation?.absolutePath)
            assertEquals(2, vm.slideFiles.size)
        }
    }

    @Test
    fun `a schedule item whose file is missing and has no remote fetch loads nothing`() = presentationTab(
        selectedPresentationItem = ScheduleItem.PresentationItem(
            id = "item-2",
            filePath = "/no/such/file/does-not-exist.pdf",
            fileName = "does-not-exist",
            slideCount = 3,
            fileType = "pdf",
        ),
    ) { vm, _ ->
        waitForIdle()

        assertEquals(null, vm.selectedPresentation)
        assertEquals(0, vm.slideFiles.size)
    }

    @Test
    fun `a schedule item whose file is missing falls back to Instance Link fetch`() {
        presentationTab(
            selectedPresentationItem = ScheduleItem.PresentationItem(
                id = "item-3",
                filePath = "/no/such/file/mirrored-deck.pdf",
                fileName = "mirrored-deck",
                slideCount = 2,
                fileType = "pdf",
            ),
            instanceLinkFetchPresentationSlideBytes = { _, _ -> jpegBytes() },
        ) { vm, _ ->
            awaitLoaded(vm, slides = 2)

            assertEquals(2, vm.slideFiles.size)
            assertEquals("mirrored-deck.pdf", vm.selectedPresentation?.name)
        }
    }
}

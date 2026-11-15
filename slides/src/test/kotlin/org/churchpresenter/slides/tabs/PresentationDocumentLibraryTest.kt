@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.ResourceDirectory

class PresentationDocumentLibraryTest {
    @Test
    fun `the presentation library merges configured directories`() {
        val root = Files.createTempDirectory("presentation-multiple-directories").toFile()
        try {
            val primary = root.resolve("primary").apply { mkdirs() }
            val additional = root.resolve("additional").apply { mkdirs() }
            primary.resolve("001 Primary.pdf").writeText("discovery fixture")
            additional.resolve("002 Additional.pdf").writeText("discovery fixture")
            presentationTab(settings = {
                it.copy(
                    presentationStorageDirectory = primary.absolutePath,
                    additionalResourceDirectories = mapOf(
                        ResourceDirectory.PRESENTATIONS to listOf(additional.absolutePath)
                    ),
                )
            }) { _, _ ->
                waitUntilAtLeastOneExists(hasText("001 Primary.pdf"), timeoutMillis = 10_000)
                onNodeWithText("002 Additional.pdf").assertExists()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a resource selection refresh request discovers newly added files immediately`() {
        val root = Files.createTempDirectory("presentation-selection-refresh").toFile()
        try {
            root.resolve("001 Existing.pdf").writeText("discovery fixture")
            presentationTab(settings = { it.copy(presentationStorageDirectory = root.absolutePath) }) { vm, _ ->
                waitUntilAtLeastOneExists(hasText("001 Existing.pdf"), timeoutMillis = 10_000)
                root.resolve("002 Added.pdf").writeText("discovery fixture")
                runOnIdle { vm.refreshDocumentLibrary() }
                waitUntilAtLeastOneExists(hasText("002 Added.pdf"), timeoutMillis = 10_000)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `the top resource menu combines file and folder selection without a refresh button`() =
        presentationTab { _, _ ->
            onNodeWithText("Choose resources ▾").performClick()
            onNodeWithText("Search folder…").assertExists()
            assertTrue(onAllNodesWithText("Select File").fetchSemanticsNodes().isNotEmpty())
            assertEquals(0, onAllNodesWithText("Refresh").fetchSemanticsNodes().size)
        }

    @Test
    fun `clicking a document while another deck is live previews it until explicitly projected`() {
        val root = Files.createTempDirectory("hymn-preview-only-ui").toFile()
        try {
            val pdf = root.resolve("002 Preview Song.pdf")
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.save(pdf)
            }
            val output = FakeSlidesOutput().apply {
                setPresentingMode(Presenting.PRESENTATION)
                setLiveSlide("001 Existing Song.pdf", 0)
                setShowPresenterWindow(true)
            }
            presentationTab(settings = { it.copy(presentationStorageDirectory = root.absolutePath) },
                presenterManager = output) { vm, _ ->
                waitUntilAtLeastOneExists(hasText(pdf.name), timeoutMillis = 10_000)
                onNodeWithText(pdf.name).performClick()
                waitUntil(timeoutMillis = 10_000) { vm.slideFiles.isNotEmpty() }
                assertTrue(vm.previewOnly)
                assertEquals("001 Existing Song.pdf" to 0, output.liveSlide.value)
                onNodeWithContentDescription("Go Live").performClick()
                waitUntil(timeoutMillis = 10_000) { output.liveSlide.value?.first == pdf.name }
                assertTrue(!vm.previewOnly)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `typing hymn initials filters the visible library immediately`() {
        val root = Files.createTempDirectory("hymn-initials-ui").toFile()
        try {
            root.resolve("001 奇异恩典.pdf").writeText("discovery fixture")
            root.resolve("002 赞美救主.pdf").writeText("discovery fixture")
            presentationTab(settings = { it.copy(presentationStorageDirectory = root.absolutePath) }) { _, _ ->
                waitUntilAtLeastOneExists(hasText("001 奇异恩典.pdf"), timeoutMillis = 10_000)
                onNodeWithText("Song title, pinyin initials or number").performTextInput("QYED")
                onNodeWithText("001 奇异恩典.pdf").assertExists()
                assertEquals(0, onAllNodesWithText("002 赞美救主.pdf").fetchSemanticsNodes().size)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `hymn number quick play loads the default book and opens the projection window`() {
        val root = Files.createTempDirectory("hymn-quick-play-ui").toFile()
        try {
            val book = root.resolve("BookA").apply { mkdirs() }
            val pdf = book.resolve("001 Amazing Grace.pdf")
            root.resolve("BookB").apply { mkdirs() }.resolve("999 Other Song.pdf").writeText("outside selected book")
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.save(pdf)
            }
            val output = FakeSlidesOutput()
            presentationTab(settings = {
                it.copy(
                    presentationStorageDirectory = root.absolutePath,
                    presentationSettings = it.presentationSettings.copy(
                        quickPlayBook = "BookA", quickPlayFormat = "pdf",
                    ),
                )
            }, presenterManager = output) { vm, reports ->
                waitUntilAtLeastOneExists(hasText(pdf.name), timeoutMillis = 10_000)
                assertEquals(0, onAllNodesWithText("999 Other Song.pdf").fetchSemanticsNodes().size)
                onNodeWithText("Song title, pinyin initials or number").performTextInput("1")
                assertEquals(1, onAllNodesWithText(pdf.name, substring = true).fetchSemanticsNodes().size)
                onNodeWithText("Song title, pinyin initials or number").performImeAction()
                waitUntil(timeoutMillis = 10_000) { output.showPresenterWindow.value }
                assertEquals(pdf.absolutePath, vm.selectedPresentation?.absolutePath)
                assertTrue(output.isLive(Presenting.PRESENTATION))
                assertTrue(reports.settingsAfterChange?.projectionSettings?.windowedProjection == true)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `the home presentation tab discovers root and nested documents and opens a discovered PDF`() {
        val root = Files.createTempDirectory("presentation-library-ui").toFile()
        try {
            val configured = root.resolve("configured").apply { mkdirs() }
            val nested = configured.resolve("Sunday").apply { mkdirs() }
            val pdf = configured.resolve("root-message.pdf")
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.save(pdf)
            }
            val slides = nested.resolve("nested-slides.pptx").apply { writeText("discovery fixture") }
            val notes = nested.resolve("nested-notes.docx").apply { writeText("discovery fixture") }
            val outside = root.resolve("outside.pdf").apply { writeText("outside configured directory") }
            presentationTab(settings = { it.copy(presentationStorageDirectory = configured.absolutePath) }) { vm, _ ->
                waitUntilAtLeastOneExists(hasText(pdf.name), timeoutMillis = 10_000)
                onNodeWithText(slides.name).assertExists()
                onNodeWithText(notes.name).assertExists()
                assertEquals(0, onAllNodesWithText(outside.name).fetchSemanticsNodes().size)
                assertNull(vm.selectedPresentation)
                onNodeWithText(pdf.name).performClick()
                waitUntil(timeoutMillis = 10_000) { vm.slideFiles.isNotEmpty() }
                assertEquals(pdf.absolutePath, vm.selectedPresentation?.absolutePath)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}

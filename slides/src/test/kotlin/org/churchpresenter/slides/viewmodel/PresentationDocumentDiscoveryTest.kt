package org.churchpresenter.slides.viewmodel

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PresentationDocumentDiscoveryTest {
    @Test
    fun `discovers documents recursively within the configured root and removes duplicates`() = runBlocking {
        val root = Files.createTempDirectory("presentation-discovery").toFile()
        try {
            val folder = root.resolve("presentations").apply { mkdirs() }
            val nested = folder.resolve("Sunday").apply { mkdirs() }
            val expected = listOf("message.PDF", "slides.pptx", "notes.docx", "legacy.doc").map { name ->
                nested.resolve(name).apply { writeText("document") }
            }
            root.resolve("outside.pdf").writeText("outside configured directory")
            nested.resolve("~\$notes.docx").writeText("office temporary file")
            nested.resolve("notes.txt").writeText("unsupported")
            nested.resolve("node_modules").apply { mkdirs() }.resolve("ignored.pdf").writeText("ignored")
            val discovered = PresentationDocumentDiscovery.discover(listOf(folder, nested, folder))
            assertEquals(expected.map { it.absolutePath }.toSet(), discovered.map { it.absolutePath }.toSet())
            assertEquals(expected.size, discovered.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `missing directories produce an empty document list`() = runBlocking {
        val root = Files.createTempDirectory("presentation-discovery-missing").toFile()
        try {
            assertEquals(emptyList(), PresentationDocumentDiscovery.discover(listOf(root.resolve("missing"))))
        } finally {
            root.deleteRecursively()
        }
    }
}

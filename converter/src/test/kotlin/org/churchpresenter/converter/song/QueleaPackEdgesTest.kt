package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueleaPackEdgesTest {

    private val temp: File = Files.createTempDirectory("quelea-pack-edges").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun pack(name: String, vararg entries: Pair<String, String?>): File = File(temp, name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            for ((entryName, body) in entries) {
                zip.putNextEntry(ZipEntry(entryName))
                if (body != null) zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `a pack holding nothing that parses reports each entry instead of writing`() {
        val input = pack("broken.qsp", "notes.txt" to "not xml", "folder/" to null)
        val result = QueleaConverter.convert(input, File(temp, "out"))
        assertTrue(result.outputFiles.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("notes.txt"), result.errors.single())
    }

    @Test
    fun `a pack with no entries at all says it holds no songs`() {
        val input = pack("empty.qsp", "folder/" to null)
        val result = QueleaConverter.convert(input, File(temp, "out"))
        assertEquals(listOf("No songs in empty.qsp"), result.errors)
    }

    @Test
    fun `a file shorter than the zip signature is not a pack`() {
        assertFalse(QueleaConverter.isZip(File(temp, "one-byte.qsp").apply { writeBytes(byteArrayOf(0x50)) }))
    }
}

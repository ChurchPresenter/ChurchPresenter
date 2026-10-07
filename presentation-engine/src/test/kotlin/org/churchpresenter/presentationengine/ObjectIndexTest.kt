package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.keynote.KnFields
import org.churchpresenter.presentationengine.keynote.ObjectIndex
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [ObjectIndex.load] over both forms a `.key` takes -- a package directory and a zip -- and every
 * way it gives up: unreadable input, nothing that parses, and metadata entries missing the fields a
 * data-file name needs.
 */
class ObjectIndexTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-object-index").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun dataInfo(id: Long?, fileName: String?, preferred: String?): ByteArray =
        Fixtures.ProtoWriter().apply {
            id?.let { varintField(KnFields.DATA_INFO_IDENTIFIER, it) }
            preferred?.let { stringField(KnFields.DATA_INFO_PREFERRED_FILE_NAME, it) }
            fileName?.let { stringField(KnFields.DATA_INFO_FILE_NAME, it) }
        }.toByteArray()

    private fun metadata(vararg infos: ByteArray): ByteArray = Fixtures.ProtoWriter().apply {
        infos.forEach { bytesField(KnFields.PACKAGE_METADATA_DATAS, it) }
    }.toByteArray()

    private val plain = Triple(7L, 42, Fixtures.ProtoWriter().apply { varintField(1, 5) }.toByteArray())

    @Test
    fun `a package directory reads its Index and Metadata archives and names its data files`() {
        val bundle = Fixtures.writeKeynoteDir(dir, listOf(plain))
        File(bundle, "Metadata").mkdirs()
        val meta = metadata(
            dataInfo(1, "named.png", "preferred.png"),
            dataInfo(2, null, "only-preferred.jpg"),
            dataInfo(null, "no-id.png", null),
            dataInfo(4, null, null),
        )
        File(bundle, "Metadata/Metadata.iwa")
            .writeBytes(Fixtures.buildIwa(listOf(Triple(9L, KnFields.TYPE_TSP_PACKAGE_METADATA, meta))))
        // Not an archive: ignored rather than read.
        File(bundle, "Index/readme.txt").writeText("not iwa")

        val index = assertNotNull(ObjectIndex.load(bundle))
        assertEquals(42, index.typeOf(7))
        assertEquals(mapOf(1L to "named.png", 2L to "only-preferred.jpg"), index.dataFileNames)
        assertEquals(mapOf(42 to 1, KnFields.TYPE_TSP_PACKAGE_METADATA to 1), index.typeHistogram())
        assertEquals(7L, index.firstOfType(42)?.first)
        assertNull(index.firstOfType(1234))
    }

    @Test
    fun `a zipped deck reads every archive and skips directories and other entries`() {
        val file = File(dir, "deck.key")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Index/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Data/picture.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Index/Document.iwa"))
            zip.write(Fixtures.buildIwa(listOf(plain)))
            zip.closeEntry()
        }

        val index = assertNotNull(ObjectIndex.load(file))
        assertEquals(42, index.typeOf(7))
        assertEquals(emptyMap(), index.dataFileNames)
    }

    @Test
    fun `a file that is not a zip, or a deck with nothing in it, loads as nothing`() {
        val garbage = File(dir, "garbage.key").apply { writeText("not a zip at all") }
        assertNull(ObjectIndex.load(garbage))

        val empty = File(dir, "empty.key").apply { mkdirs() }
        assertNull(ObjectIndex.load(empty))
    }
}

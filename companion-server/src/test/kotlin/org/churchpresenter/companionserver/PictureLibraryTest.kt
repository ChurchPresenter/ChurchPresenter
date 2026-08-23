package org.churchpresenter.companionserver

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PictureLibraryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun imagesIn(dir: File, vararg names: String): List<File> =
        names.map { File(dir, it).apply { writeBytes(byteArrayOf(1, 2, 3)) } }

    @Test
    fun `updating publishes the folder as the active catalogue`() {
        val lib = PictureLibrary()
        val dir = temp.newFolder("easter")
        val files = imagesIn(dir, "a.jpg", "b.jpg")
        val catalog = lib.update("f1", "Easter", dir.absolutePath, files)

        assertEquals("f1", catalog.folderId)
        assertEquals("Easter", catalog.folderName)
        assertEquals(2, catalog.imageTotal)
        assertEquals("f1", lib.activeFolderId)
        assertEquals(catalog, lib.catalog.value)
        assertEquals(catalog, lib.catalogs["f1"])
    }

    @Test
    fun `images are addressable by index in the order they were given`() {
        val lib = PictureLibrary()
        val dir = temp.newFolder("pics")
        val files = imagesIn(dir, "first.jpg", "second.jpg")
        lib.update("f1", "Pics", dir.absolutePath, files)

        assertEquals("first.jpg", lib.imageFile("f1", 0)?.name)
        assertEquals("second.jpg", lib.imageFile("f1", 1)?.name)
    }

    @Test
    fun `an index or folder that does not exist yields no file rather than throwing`() {
        val lib = PictureLibrary()
        assertNull(lib.imageFile("nope", 0))
        lib.update("f1", "Pics", temp.newFolder("p").absolutePath, emptyList())
        assertNull(lib.imageFile("f1", 0))
        assertNull(lib.imageFile("f1", -1))
    }

    @Test
    fun `there is no active folder until one is published`() {
        assertNull(PictureLibrary().activeFolderId)
    }

    @Test
    fun `a scheduled folder is scanned and catalogued so a phone can browse it`() {
        val lib = PictureLibrary()
        val dir = temp.newFolder("scheduled")
        imagesIn(dir, "b.jpg", "a.png", "c.gif")
        lib.registerScheduleFolder("item-1", dir.absolutePath, "Scheduled")

        assertEquals(3, lib.catalogs["item-1"]?.imageTotal)
        // sorted by name, so a client's index matches what the operator sees
        assertEquals(listOf("a.png", "b.jpg", "c.gif"), lib.files["item-1"]?.map { it.name })
    }

    @Test
    fun `non-image files in a scheduled folder are ignored`() {
        val lib = PictureLibrary()
        val dir = temp.newFolder("mixed")
        imagesIn(dir, "keep.jpg", "notes.txt", "song.mp3")
        lib.registerScheduleFolder("item-1", dir.absolutePath, "Mixed")

        assertEquals(listOf("keep.jpg"), lib.files["item-1"]?.map { it.name })
    }

    @Test
    fun `a folder already registered is not rescanned`() {
        val lib = PictureLibrary()
        val dir = temp.newFolder("stable")
        imagesIn(dir, "one.jpg")
        lib.registerScheduleFolder("item-1", dir.absolutePath, "Stable")
        imagesIn(dir, "two.jpg")
        lib.registerScheduleFolder("item-1", dir.absolutePath, "Stable")

        assertEquals(1, lib.files["item-1"]?.size, "second call must not re-read the folder")
    }

    @Test
    fun `a missing or empty folder registers nothing`() {
        val lib = PictureLibrary()
        lib.registerScheduleFolder("gone", File(temp.root, "does-not-exist").absolutePath, "Gone")
        lib.registerScheduleFolder("empty", temp.newFolder("empty").absolutePath, "Empty")
        lib.registerScheduleFolder("file", temp.newFile("a-file.txt").absolutePath, "AFile")

        assertTrue(lib.files.isEmpty())
        assertTrue(lib.catalogs.isEmpty())
    }

    @Test
    fun `locate finds which registered folder holds a path`() {
        val lib = PictureLibrary()
        val dir = temp.newFolder("find")
        val files = imagesIn(dir, "x.jpg", "y.jpg")
        lib.update("f1", "Find", dir.absolutePath, files)

        assertEquals("f1" to 1, lib.locate(files[1].absolutePath))
    }

    @Test
    fun `locate returns nothing for an unknown or absent path`() {
        val lib = PictureLibrary()
        assertEquals(null to null, lib.locate(null))
        assertEquals(null to null, lib.locate(""))
        assertEquals(null to null, lib.locate("/somewhere/else.jpg"))
    }

    @Test
    fun `clearing device uploads drops only the device upload entries`() {
        val lib = PictureLibrary()
        val dir = temp.newFolder("keep")
        val files = imagesIn(dir, "keep.jpg")
        lib.update("folder-from-tab", "Kept", dir.absolutePath, files)
        lib.files[PictureLibrary.DEVICE_UPLOADS_FOLDER_ID] = files
        lib.catalogs[PictureLibrary.DEVICE_UPLOADS_FOLDER_ID] = lib.catalog.value!!
        lib.files["${PictureLibrary.DEVICE_UPLOADS_FOLDER_ID}_2026-08-09"] = files
        lib.catalogs["${PictureLibrary.DEVICE_UPLOADS_FOLDER_ID}_2026-08-09"] = lib.catalog.value!!

        lib.clearDeviceUploads()

        assertTrue(lib.files.containsKey("folder-from-tab"))
        assertTrue(lib.files.keys.none { it.startsWith(PictureLibrary.DEVICE_UPLOADS_FOLDER_ID) })
        assertTrue(lib.catalogs.keys.none { it.startsWith(PictureLibrary.DEVICE_UPLOADS_FOLDER_ID) })
    }
}

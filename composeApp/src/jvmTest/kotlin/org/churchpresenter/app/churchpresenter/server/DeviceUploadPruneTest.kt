package org.churchpresenter.app.churchpresenter.server

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What becomes of decks pushed from a phone.
 *
 * Nothing used to delete them. Evicting the previous upload from the catalogue kept the mobile
 * list tidy while its file stayed in `device_presentations/` for ever, invisible to the UI — and an
 * upload whose name is already taken is renamed rather than replaced, so re-sending one deck grew
 * the folder every time. At 200 MB a deck that made this the fastest-growing directory in the data
 * folder.
 *
 * The sweep keeps a count rather than deleting the previous upload outright, because that one may
 * still be open in the Presentation tab, which renders from the file on disk.
 */
class DeviceUploadPruneTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-device-uploads-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    /** A deck written [minutesAgo] minutes ago, so "newest ten" has something to sort on. */
    private fun deck(name: String, minutesAgo: Long): File =
        File(deviceUploadDir.also { it.mkdirs() }, name).apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(System.currentTimeMillis() - minutesAgo * 60_000)
        }

    private fun namesOnDisk(): Set<String> =
        deviceUploadDir.listFiles().orEmpty().map { it.name }.toSet()

    @Test
    fun `only the newest decks are kept`() {
        val kept = (1..DEVICE_UPLOADS_KEPT).map { deck("recent-$it.pptx", minutesAgo = it.toLong()) }
        val dropped = (1..5).map { deck("ancient-$it.pptx", minutesAgo = 1_000L + it) }

        pruneDeviceUploads()

        assertEquals(kept.map { it.name }.toSet(), namesOnDisk())
        assertTrue(dropped.none { it.exists() }, "every deck ever pushed used to stay here for ever")
    }

    @Test
    fun `the deck just uploaded is never the one that goes`() {
        (1..DEVICE_UPLOADS_KEPT + 5).forEach { deck("old-$it.pptx", minutesAgo = 100L + it) }
        val newest = deck("just-arrived.pptx", minutesAgo = 0)

        pruneDeviceUploads()

        assertTrue(newest.exists(), "the newest upload is the one the tab is about to open")
    }

    @Test
    fun `fewer decks than the limit are all kept`() {
        val three = (1..3).map { deck("deck-$it.pptx", minutesAgo = it.toLong()) }

        pruneDeviceUploads()

        assertEquals(three.map { it.name }.toSet(), namesOnDisk())
    }

    @Test
    fun `pruning an upload folder that does not exist does not throw`() {
        deviceUploadDir.deleteRecursively()

        pruneDeviceUploads()

        assertFalse(deviceUploadDir.exists(), "pruning must not create the folder it was asked to tidy")
    }

    @Test
    fun `nothing outside the upload folder is touched`() {
        val elsewhere = File(home, "my-sermon.pptx").apply { writeBytes(byteArrayOf(1)) }
        (1..DEVICE_UPLOADS_KEPT + 3).forEach { deck("deck-$it.pptx", minutesAgo = it.toLong()) }

        pruneDeviceUploads()

        assertTrue(elsewhere.exists(), "the operator's own decks live outside this folder")
    }
}

package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.runBlocking
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StreamingSettings
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AppShowHostLowerThirdFolderTest {

    private val folder: File = Files.createTempDirectory("cp-show-lt-folder").toFile()
    private val pm = PresenterManager(showPresenterWindowInitially = false)

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun host(lowerThirdFolder: File) = AppShowHost(
        pm,
        { AppSettings(streamingSettings = StreamingSettings(lowerThirdFolder = lowerThirdFolder.path)) },
        ShowOutlets(),
    )

    @Test
    fun `a lower-third folder that is not there has no presets to run`() {
        val error = assertFailsWith<IllegalArgumentException> {
            runBlocking { host(File(folder, "missing")).lowerThird("Pastor") }
        }
        assertEquals("No lower third called Pastor", error.message)
        assertFalse(pm.isLive(Presenting.LOWER_THIRD))
    }

    @Test
    fun `a folder that only shares the preset's name is not a preset`() {
        File(folder, "Pastor").mkdirs()
        val error = assertFailsWith<IllegalArgumentException> {
            runBlocking { host(folder).lowerThird("Pastor") }
        }
        assertEquals("No lower third called Pastor", error.message)
        assertFalse(pm.isLive(Presenting.LOWER_THIRD))
    }
}

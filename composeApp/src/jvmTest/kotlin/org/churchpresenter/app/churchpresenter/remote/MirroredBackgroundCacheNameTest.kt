package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.server.InstanceLinkViewModel
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MirroredBackgroundCacheNameTest {

    private val slot = Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD
    private lateinit var link: InstanceLinkViewModel

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
        link = InstanceLinkViewModel()
        clearSlot()
    }

    @AfterTest
    fun tearDown() {
        clearSlot()
        runCatching { link.dispose() }
    }

    private fun clearSlot() {
        instanceLinkBackgroundCacheDir.listFiles()?.filter { it.name.startsWith("$slot-") }?.forEach { it.delete() }
    }

    private fun download(config: BackgroundConfig) = runBlocking {
        downloadMirroredBackgroundSettings(BackgroundSettings(songLowerThirdBackground = config), link)
    }.songLowerThirdBackground

    @Test
    fun `a file with no extension is cached as a jpg picture or an mp4 clip`() {
        val image = File(instanceLinkBackgroundCacheDir, "$slot-image.jpg").apply { writeText("image") }
        val video = File(instanceLinkBackgroundCacheDir, "$slot-video.mp4").apply { writeText("video") }

        val mirrored = download(
            BackgroundConfig(backgroundImage = "/primary/backdrop", backgroundVideo = "/primary/loop"),
        )

        assertEquals(image.absolutePath, mirrored.backgroundImage)
        assertEquals(video.absolutePath, mirrored.backgroundVideo)
    }

    @Test
    fun `with no primary to fetch from, an uncached slot is blanked`() {
        val mirrored = download(BackgroundConfig(backgroundImage = "/primary/backdrop.png"))

        assertEquals("", mirrored.backgroundImage)
    }
}

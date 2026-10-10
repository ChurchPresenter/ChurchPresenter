package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.server.InstanceLinkViewModel
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MirroredBackgroundUnreachableTest {

    private val link = InstanceLinkViewModel()

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
        clearSlot()
    }

    @AfterTest
    fun tearDown() {
        clearSlot()
        link.dispose()
    }

    private fun clearSlot() {
        instanceLinkBackgroundCacheDir.listFiles()
            ?.filter { it.name.startsWith("${Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD}-") }
            ?.forEach { it.delete() }
    }

    @Test
    fun `a background the primary cannot be reached for is dropped, extension or not`() {
        val remote = BackgroundSettings(
            bibleLowerThirdBackground = BackgroundConfig(
                backgroundImage = "/primary/backgrounds/still",
                backgroundVideo = "/primary/backgrounds/loop",
            ),
        )
        val mirrored = runBlocking { downloadMirroredBackgroundSettings(remote, link) }
        assertEquals("", mirrored.bibleLowerThirdBackground.backgroundImage)
        assertEquals("", mirrored.bibleLowerThirdBackground.backgroundVideo)
    }
}

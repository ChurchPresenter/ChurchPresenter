package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.utils.Constants
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongBackgroundResolveTest {

    @Test
    fun `a colour and a gradient always resolve`() {
        assertTrue(songBackgroundResolves(SongBackground(type = SongBackgroundType.COLOR)))
        assertTrue(songBackgroundResolves(SongBackground(type = SongBackgroundType.GRADIENT)))
    }

    @Test
    fun `an inheriting background resolves to nothing`() {
        assertFalse(songBackgroundResolves(SongBackground()))
    }

    @Test
    fun `a picture resolves only while its file is on this machine`() {
        val file = Files.createTempFile("cp-bg", ".jpg")
        try {
            val present = SongBackground(type = SongBackgroundType.IMAGE, image = file.absolutePathString())
            val missing = SongBackground(type = SongBackgroundType.IMAGE, image = "/nowhere/gone.jpg")

            assertTrue(songBackgroundResolves(present))
            assertFalse(songBackgroundResolves(missing), "a song travels; the picture it names may not")
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a clip with no path resolves to nothing`() {
        assertFalse(songBackgroundResolves(SongBackground(type = SongBackgroundType.VIDEO)))
    }

    @Test
    fun `each type maps onto the background constant the presenter switches on`() {
        assertEquals(Constants.BACKGROUND_IMAGE, songBackgroundTypeConstant(SongBackgroundType.IMAGE))
        assertEquals(Constants.BACKGROUND_VIDEO, songBackgroundTypeConstant(SongBackgroundType.VIDEO))
        assertEquals(Constants.BACKGROUND_COLOR, songBackgroundTypeConstant(SongBackgroundType.COLOR))
        assertEquals(Constants.BACKGROUND_COLOR, songBackgroundTypeConstant(SongBackgroundType.GRADIENT))
    }
}

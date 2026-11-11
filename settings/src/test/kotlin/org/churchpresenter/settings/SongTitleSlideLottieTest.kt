package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Lottie a title slide plays (#971), one for each kind of output. */
class SongTitleSlideLottieTest {

    @Test
    fun `none is set until one is picked`() {
        assertEquals("", SongSettings().titleSlideLottie(lowerThird = false))
        assertEquals("", SongSettings().titleSlideLottie(lowerThird = true))
    }

    @Test
    fun `the full screen and the lower third each keep their own`() {
        val song = SongSettings()
            .withTitleSlideLottie(lowerThird = false, path = "/t/full.json")
            .withTitleSlideLottie(lowerThird = true, path = "/t/band.json")

        assertEquals("/t/full.json", song.titleSlideLottie(lowerThird = false))
        assertEquals("/t/band.json", song.titleSlideLottie(lowerThird = true))
        assertEquals("", song.withTitleSlideLottie(lowerThird = false, path = "").titleSlideLottie(lowerThird = false))
    }

    @Test
    fun `they are saved and read back with the song settings`() {
        val song = SongSettings().withTitleSlideLottie(lowerThird = true, path = "/t/band.json")
        val json = settingsJson.encodeToString(SongSettings.serializer(), song)

        assertEquals(song, settingsJson.decodeFromString(SongSettings.serializer(), json))
    }
}

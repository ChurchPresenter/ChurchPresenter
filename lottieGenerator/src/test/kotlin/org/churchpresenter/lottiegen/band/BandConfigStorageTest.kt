package org.churchpresenter.lottiegen.band

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BandConfigStorageTest {

    private lateinit var temp: File
    private lateinit var savedHome: String

    @BeforeTest
    fun isolateHome() {
        temp = Files.createTempDirectory("band-config-test").toFile()
        savedHome = System.getProperty("user.home")
        System.setProperty("user.home", temp.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", savedHome)
        temp.deleteRecursively()
    }

    @Test
    fun `nothing remembered means the seed`() {
        assertNull(BandConfigStorage.load())
        val seed = BibleLottieGenConfig(canvasW = 100, canvasH = 50)
        assertEquals(seed, BandConfigStorage.restore(seed))
    }

    @Test
    fun `the design is remembered, the canvas comes from the host`() {
        val saved = BibleLottieGenConfig(
            canvasW = 1920, canvasH = 356, bandStyle = BandStyle.WAVE_DECK, accentColor = "#123456", paddingPx = 7,
        )
        BandConfigStorage.save(saved)
        val restored = BandConfigStorage.restore(BibleLottieGenConfig(canvasW = 3840, canvasH = 712))
        assertEquals(BandStyle.WAVE_DECK, restored.bandStyle)
        assertEquals("#123456", restored.accentColor)
        assertEquals(7, restored.paddingPx)
        assertEquals(3840, restored.canvasW)
        assertEquals(712, restored.canvasH)
    }

    @Test
    fun `opened for the other content, the sample and layout are the host's again`() {
        BandConfigStorage.save(
            BibleLottieGenConfig(
                kind = BandContentKind.BIBLE, layout = SlotLayout.SIDE_BY_SIDE, previewText1 = "verse",
            ),
        )
        val seed = BibleLottieGenConfig(kind = BandContentKind.SONG, layout = SlotLayout.SINGLE, previewText1 = "lyric")
        val restored = BandConfigStorage.restore(seed)
        assertEquals(BandContentKind.SONG, restored.kind)
        assertEquals(SlotLayout.SINGLE, restored.layout)
        assertEquals("lyric", restored.previewText1)
        val same = BandConfigStorage.restore(BibleLottieGenConfig(kind = BandContentKind.BIBLE, previewText1 = "other"))
        assertEquals("verse", same.previewText1, "same kind keeps what was remembered")
    }

    @Test
    fun `a corrupt file is ignored`() {
        val dir = File(temp, ".churchpresenter/churchpresenter-lottiegen").apply { mkdirs() }
        File(dir, "bible-band-last.json").writeText("{ not json")
        assertNull(BandConfigStorage.load())
    }
}

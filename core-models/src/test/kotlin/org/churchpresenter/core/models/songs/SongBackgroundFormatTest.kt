package org.churchpresenter.core.models.songs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A song's own background, through the `.song` header both ways.
 *
 * The background travels with the song file, so a key the writer emits and the reader ignores means
 * a church's chosen background quietly reverts the next time the song is opened.
 */
class SongBackgroundFormatTest {

    private val parser = SongFileParser()

    private fun parse(header: String): SongItem? = parser.parseSongContent(
        """
        ---
        $header
        ---

        [Primary]
        title: Amazing Grace

        Amazing grace how sweet the sound
        """.trimIndent(),
    )

    // ── Reading ─────────────────────────────────────────────────────────────────

    @Test
    fun `a song with no background keys inherits`() {
        val song = parse("author: John Newton")

        assertFalse(song!!.background.isCustom)
        assertFalse(song.lowerThirdBackground.isCustom)
        assertEquals(SongBackgroundType.INHERIT, song.background.type)
    }

    @Test
    fun `a colour background is read with its dim and blur`() {
        val song = parse(
            """
            background: color
            background-color: #0d1b2a
            background-dim: 45
            background-blur: 6
            """.trimIndent(),
        )

        assertEquals(SongBackgroundType.COLOR, song!!.background.type)
        assertEquals("#0d1b2a", song.background.color)
        assertEquals(45, song.background.dim)
        assertEquals(6, song.background.blur)
    }

    @Test
    fun `a gradient carries both ends`() {
        val song = parse(
            """
            background: gradient
            background-color: #3b1408
            background-color-end: #7a2c10
            """.trimIndent(),
        )

        assertEquals(SongBackgroundType.GRADIENT, song!!.background.type)
        assertEquals("#3b1408", song.background.color)
        assertEquals("#7a2c10", song.background.colorEnd)
    }

    @Test
    fun `an image path survives the colon in a Windows drive letter`() {
        val song = parse(
            """
            background: image
            background-image: C:\backgrounds\dawn.jpg
            """.trimIndent(),
        )

        assertEquals(SongBackgroundType.IMAGE, song!!.background.type)
        assertEquals("""C:\backgrounds\dawn.jpg""", song.background.image)
        assertEquals("""C:\backgrounds\dawn.jpg""", song.background.mediaPath)
    }

    @Test
    fun `a video names its own path`() {
        val song = parse(
            """
            background: video
            background-video: /media/loop.mp4
            """.trimIndent(),
        )

        assertEquals(SongBackgroundType.VIDEO, song!!.background.type)
        assertEquals("/media/loop.mp4", song.background.mediaPath)
    }

    @Test
    fun `the lower third keeps its own background`() {
        val song = parse(
            """
            background: color
            background-color: #000000
            lower-third-background: color
            lower-third-background-color: #2a1130
            lower-third-background-dim: 30
            """.trimIndent(),
        )

        assertEquals("#000000", song!!.background.color)
        assertEquals("#2a1130", song.lowerThirdBackground.color)
        assertEquals(30, song.lowerThirdBackground.dim)
        assertEquals(0, song.background.dim)
    }

    @Test
    fun `a type this build does not know is read as inheriting`() {
        val song = parse(
            """
            background: hologram
            background-color: #ff0000
            """.trimIndent(),
        )

        assertFalse(song!!.background.isCustom)
    }

    @Test
    fun `dim and blur are clamped to their ranges`() {
        val song = parse(
            """
            background: color
            background-dim: 400
            background-blur: 900
            """.trimIndent(),
        )

        assertEquals(100, song!!.background.dim)
        assertEquals(SONG_BACKGROUND_MAX_BLUR, song.background.blur)
    }

    @Test
    fun `a non-numeric dim falls back to none rather than failing the parse`() {
        val song = parse(
            """
            background: color
            background-dim: quite a lot
            """.trimIndent(),
        )

        assertEquals(0, song!!.background.dim)
    }

    @Test
    fun `a blank colour falls back to black`() {
        val song = parse(
            """
            background: color
            background-color:
            """.trimIndent(),
        )

        assertEquals("#000000", song!!.background.color)
    }

    // ── Writing ─────────────────────────────────────────────────────────────────

    private fun written(song: SongItem): String {
        val file = kotlin.io.path.createTempDirectory("song-bg").resolve("song.song")
        parser.writeSongFile(song, file.toString())
        return file.toFile().readText()
    }

    private fun blank() = SongItem(number = "1", title = "Amazing Grace", lyrics = listOf("Amazing grace"))

    @Test
    fun `a song that inherits writes no background keys at all`() {
        val text = written(blank())

        assertFalse(text.contains("background"))
    }

    @Test
    fun `a background alone is enough to open a header`() {
        val text = written(blank().copy(background = SongBackground(type = SongBackgroundType.COLOR)))

        assertTrue(text.startsWith("---"))
        assertTrue(text.contains("background: color"))
    }

    @Test
    fun `only the keys the chosen type uses are written`() {
        val text = written(
            blank().copy(
                background = SongBackground(
                    type = SongBackgroundType.IMAGE,
                    image = "/pics/dawn.jpg",
                    video = "/never/written.mp4",
                    color = "#123456",
                ),
            ),
        )

        assertTrue(text.contains("background-image: /pics/dawn.jpg"))
        assertFalse(text.contains("background-video"))
        assertFalse(text.contains("background-color"))
    }

    @Test
    fun `dim and blur are only written when they are set`() {
        val text = written(blank().copy(background = SongBackground(type = SongBackgroundType.COLOR)))

        assertFalse(text.contains("background-dim"))
        assertFalse(text.contains("background-blur"))
    }

    @Test
    fun `every field of both backgrounds survives the round trip`() {
        val original = blank().copy(
            author = "John Newton",
            background = SongBackground(
                type = SongBackgroundType.GRADIENT,
                color = "#131a3a",
                colorEnd = "#3a2352",
                dim = 25,
                blur = 3,
            ),
            lowerThirdBackground = SongBackground(
                type = SongBackgroundType.VIDEO,
                video = "/media/loop.mp4",
                dim = 65,
                blur = 12,
            ),
        )

        val reread = parser.parseSongContent(written(original))

        assertEquals(original.background, reread?.background)
        assertEquals(original.lowerThirdBackground, reread?.lowerThirdBackground)
    }

    @Test
    fun `a video background writes its clip and a colour writes its colour`() {
        val video = written(
            blank().copy(background = SongBackground(type = SongBackgroundType.VIDEO, video = "/media/loop.mp4"))
        )
        val colour = written(
            blank().copy(background = SongBackground(type = SongBackgroundType.COLOR, color = "#0d1b2a"))
        )

        assertTrue(video.contains("background-video: /media/loop.mp4"))
        assertTrue(colour.contains("background-color: #0d1b2a"))
    }

    @Test
    fun `mediaPath is empty for the types that have no file`() {
        assertEquals("", SongBackground(type = SongBackgroundType.COLOR).mediaPath)
        assertEquals("", SongBackground(type = SongBackgroundType.GRADIENT).mediaPath)
        assertEquals("", SongBackground().mediaPath)
    }

    @Test
    fun `withBackgroundsOf carries both of a song's backgrounds onto a section`() {
        val song = blank().copy(
            background = SongBackground(type = SongBackgroundType.COLOR, color = "#0d1b2a"),
            lowerThirdBackground = SongBackground(type = SongBackgroundType.COLOR, color = "#2a1130"),
        )

        val section = LyricSection(title = "Amazing Grace").withBackgroundsOf(song)

        assertEquals("#0d1b2a", section.background.color)
        assertEquals("#2a1130", section.lowerThirdBackground.color)
    }
}

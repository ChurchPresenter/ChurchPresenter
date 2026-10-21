package org.churchpresenter.core.models.songs

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A song's own font, size and colours, through the `.song` header both ways.
 *
 * They travel with the song file, so a key the writer emits and the reader drops means the song's
 * look quietly reverts the next time it is opened.
 */
class SongTextOverridesTest {

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

    // ── The model ───────────────────────────────────────────────────────────────

    @Test
    fun `the defaults change nothing`() {
        assertFalse(SongTextOverrides().isCustom)
        assertEquals(1f, SongTextOverrides().sizeScale)
        assertTrue(SongTextOverrides(sizePercent = 120).isCustom)
    }

    @Test
    fun `the size scale is held to the range the editor offers`() {
        assertEquals(1.5f, SongTextOverrides(sizePercent = 150).sizeScale)
        assertEquals(0.5f, SongTextOverrides(sizePercent = 10).sizeScale)
        assertEquals(2f, SongTextOverrides(sizePercent = 900).sizeScale)
    }

    // ── Reading ─────────────────────────────────────────────────────────────────

    @Test
    fun `a song with no override keys follows the profile`() {
        assertFalse(parse("author: John Newton")!!.textOverrides.isCustom)
    }

    @Test
    fun `every override is read`() {
        val song = parse(
            """
            font: Georgia
            size: 120%
            color: #A8D8FF
            voice2: #FF8A65
            """.trimIndent(),
        )

        assertEquals(SongTextOverrides("Georgia", 120, "#A8D8FF", "#FF8A65"), song!!.textOverrides)
    }

    @Test
    fun `a size is read without its percent sign and held to the range`() {
        assertEquals(80, parse("size: 80")!!.textOverrides.sizePercent)
        assertEquals(SongTextOverrides.MAX_SIZE_PERCENT, parse("size: 400%")!!.textOverrides.sizePercent)
        assertEquals(SongTextOverrides.MIN_SIZE_PERCENT, parse("size: 5 %")!!.textOverrides.sizePercent)
    }

    @Test
    fun `a mistyped size or colour costs the override, not the song`() {
        val song = parse(
            """
            size: big
            color: red
            voice2: #12345
            """.trimIndent(),
        )

        assertEquals("Amazing Grace", song!!.title)
        assertFalse(song.textOverrides.isCustom)
    }

    @Test
    fun `a colour with alpha is kept`() {
        assertEquals("#80FFD43B", parse("voice2: #80FFD43B")!!.textOverrides.secondVoiceColor)
    }

    // ── Writing ─────────────────────────────────────────────────────────────────

    private fun written(song: SongItem): String {
        val dir = createTempDirectory("song-look").toFile()
        try {
            val file = dir.resolve("song.song")
            parser.writeSongFile(song, file.path)
            return file.readText()
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun blank() = SongItem(number = "1", title = "Amazing Grace", lyrics = listOf("Amazing grace"))

    @Test
    fun `a song that follows the profile writes no override keys`() {
        assertEquals(emptyList(), songTextOverrideFields(SongTextOverrides()))
        assertFalse(written(blank()).startsWith("---"))
    }

    @Test
    fun `an override alone is enough to open a header`() {
        val text = written(blank().copy(textOverrides = SongTextOverrides(sizePercent = 90)))

        assertTrue(text.startsWith("---"))
        assertTrue(text.contains("size: 90%"))
        assertFalse(text.contains("font:"))
    }

    @Test
    fun `every override survives a write and a read`() {
        val look = SongTextOverrides("Georgia", 130, "#A8D8FF", "#FF8A65")

        val read = parser.parseSongContent(written(blank().copy(textOverrides = look)))

        assertEquals(look, read!!.textOverrides)
        assertEquals(listOf("Amazing grace"), read.lyrics)
    }

    @Test
    fun `a section carries the song's look`() {
        val look = SongTextOverrides(color = "#FFFFFF")

        val section = LyricSection(lines = listOf("x")).withTextOverridesOf(blank().copy(textOverrides = look))

        assertEquals(look, section.textOverrides)
    }
}

package converter.library

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three ways `findDuplicates` can match — by song number, by title, and by lyric content —
 * and the interaction between them.
 *
 * Content matching is the one that earns its keep: the same hymn filed under a translated title
 * and a different number is invisible to the other two, and that is the case a library merged from
 * several sources is full of.
 */
class DuplicateMatchingModesTest {

    private val temp: File = Files.createTempDirectory("converter-dupmodes-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun song(folder: String, fileName: String, title: String, vararg lyrics: String): File {
        val dir = File(temp, folder).apply { mkdirs() }
        return File(dir, "$fileName.song").apply {
            writeText(
                buildString {
                    appendLine("---")
                    appendLine("author: someone")
                    appendLine("---")
                    appendLine()
                    appendLine("[Primary]")
                    appendLine("title: $title")
                    appendLine()
                    appendLine("[Verse 1]")
                    lyrics.forEach { appendLine(it) }
                },
                Charsets.UTF_8,
            )
        }
    }

    private val verse = arrayOf(
        "Amazing grace how sweet the sound",
        "That saved a wretch like me",
        "I once was lost but now am found",
        "Was blind but now I see",
    )

    @Test
    fun `matching by number groups the same number across different folders`() {
        song("bookA", "0042 - Amazing Grace", "Amazing Grace", *verse)
        song("bookB", "0042 - Chudnaya Blagodat", "Chudnaya Blagodat", *verse)

        val groups = DuplicateFinder.findDuplicates(temp, matchByNumber = true, matchByTitle = false)
        assertEquals(1, groups.size, "the shared leading number is the link")
        assertEquals(2, groups.single().songs.size)
    }

    @Test
    fun `matching by number ignores the same number inside one folder`() {
        // Two songs numbered 42 in the same book are a numbering mistake, not a duplicate pair.
        song("bookA", "0042 - One", "One", *verse)
        song("bookA", "0042 - Two", "Two", "Entirely different words here", "Nothing alike at all")

        val groups = DuplicateFinder.findDuplicates(temp, matchByNumber = true, matchByTitle = false)
        assertTrue(groups.isEmpty(), "a within-folder clash is not a cross-library duplicate")
    }

    @Test
    fun `matching by number still requires the lyrics to agree`() {
        song("bookA", "0042 - One", "One", *verse)
        song("bookB", "0042 - Different", "Different", "A completely unrelated hymn", "With its own words")

        val groups = DuplicateFinder.findDuplicates(temp, matchByNumber = true, matchByTitle = false)
        assertTrue(groups.isEmpty(), "a shared number alone is not enough")
    }

    @Test
    fun `content matching finds the same song under a different title and number`() {
        // Neither the title nor the number matches; only the lyrics do.
        song("bookA", "0001 - Amazing Grace", "Amazing Grace", *verse)
        song("bookB", "0500 - O How Sweet", "O How Sweet", *verse)

        val groups = DuplicateFinder.findDuplicates(temp, matchByNumber = false, matchByTitle = false)
        assertEquals(1, groups.size, "the lyric pass caught it")
        assertEquals(2, groups.single().songs.size)
    }

    @Test
    fun `content matching leaves genuinely different songs apart`() {
        song("bookA", "0001 - Amazing Grace", "Amazing Grace", *verse)
        song(
            "bookB", "0002 - Total Praise", "Total Praise",
            "Lord I will lift mine eyes to the hills",
            "Knowing my help is coming from you",
            "Your peace you give me in time of the storm",
        )
        assertTrue(DuplicateFinder.findDuplicates(temp, matchByNumber = false, matchByTitle = false).isEmpty())
    }

    @Test
    fun `a song is reported in only one group`() {
        // Three copies that match by title AND by content must not be double-counted.
        song("bookA", "grace", "Amazing Grace", *verse)
        song("bookB", "grace", "Amazing Grace", *verse)
        song("bookC", "grace", "Amazing Grace", *verse)

        val groups = DuplicateFinder.findDuplicates(temp, matchByNumber = true, matchByTitle = true)
        val allFiles = groups.flatMap { g -> g.songs.map { it.file.canonicalPath } }
        assertEquals(allFiles.distinct().size, allFiles.size, "no song appears in two groups")
        assertEquals(3, allFiles.size)
    }

    @Test
    fun `a group reports a similarity score for each of its songs`() {
        song("bookA", "grace", "Amazing Grace", *verse)
        song("bookB", "grace", "Amazing Grace", *verse)

        val group = DuplicateFinder.findDuplicates(temp).single()
        assertEquals(group.songs.size, group.similarities.size, "one score per song")
        assertTrue(group.similarities.all { it in 0.0..1.0 }, "scores are proportions: ${group.similarities}")
        assertTrue(group.reason.isNotBlank(), "the operator is told why these were grouped")
    }

    @Test
    fun `a library with only one song has nothing to compare`() {
        song("bookA", "only", "Only Song", *verse)
        assertTrue(DuplicateFinder.findDuplicates(temp).isEmpty())
    }

    @Test
    fun `an empty directory yields no groups`() {
        assertTrue(DuplicateFinder.findDuplicates(File(temp, "nothing-here").apply { mkdirs() }).isEmpty())
    }

    @Test
    fun `the threshold decides how close is close enough`() {
        // One line differs out of four.
        song("bookA", "0001 - Grace", "Grace", *verse)
        song("bookB", "0002 - Grace Variant", "Grace Variant", verse[0], verse[1], verse[2], "A different closing line entirely")

        val strict = DuplicateFinder.findDuplicates(temp, threshold = 0.99, matchByTitle = false)
        val lenient = DuplicateFinder.findDuplicates(temp, threshold = 0.5, matchByTitle = false)
        assertTrue(
            lenient.size >= strict.size,
            "a lower bar cannot find fewer duplicates (strict=${strict.size}, lenient=${lenient.size})",
        )
    }
}

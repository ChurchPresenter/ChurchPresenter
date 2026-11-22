package org.churchpresenter.bibleeditor

import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bibleedits.BibleEditsStore
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.VerseChange
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * A Bible folder of two translations and a data folder, under one temp directory a test deletes.
 *
 * Every chapter either Bible has is as long as its numbering makes it, so the one gap is the one
 * meant: the KJV is KJV-numbered, and the "RST" Synodal-numbered (its Romans 14 runs to 26 and 16
 * to 24) and lacks Matthew 17:11.
 */
class EditorFixture(rstContent: String = rst()) {
    val root: File = Files.createTempDirectory("cp-bible-editor").toFile()
    val bibles = File(root, "bibles").also { it.mkdirs() }
    val data = File(root, "data")
    val rst: File = SpbFixture.spbFile(bibles, "rst.spb", rstContent)
    val kjv: File = SpbFixture.spbFile(bibles, "kjv.spb", kjv())

    val store = BibleEditsStore(data)

    fun saveHistory(entries: List<EditEntry>) = store.save(entries)

    fun delete() {
        root.deleteRecursively()
    }

    companion object {
        /** Noon, so "three hours ago" is still today and "a day ago" is yesterday. */
        val NOW: Long = LocalDate.of(2026, 3, 4).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        const val HOUR = 3_600_000L
        const val MATTHEW = 40
        const val MARK = 41
        const val ROMANS = 45

        /** How long each of the fixture's chapters runs. */
        private const val MATTHEW_1_VERSES = 25
        private const val MATTHEW_17_VERSES = 27
        private const val MARK_1_VERSES = 45
        private const val ROMANS_14_SYNODAL = 26
        private const val ROMANS_16_SYNODAL = 24

        val KJV_17 = listOf(
            "And after six days Jesus taketh Peter, James, and John his brother, " +
                "and bringeth them up into an high mountain apart,",
            "And was transfigured before them: and his face did shine as the sun, " +
                "and his raiment was white as the light.",
            "And, behold, there appeared unto them Moses and Elias talking with him.",
            "Then answered Peter, and said unto Jesus, Lord, it is good for us to be here.",
            "While he yet spake, behold, a bright cloud overshadowed them.",
            "And when the disciples heard it, they fell on their face, and were sore afraid.",
            "And Jesus came and touched them, and said, Arise, and be not afraid.",
            "And when they had lifted up their eyes, they saw no man, save Jesus only.",
            "And as they came down from the mountain, Jesus charged them, saying, Tell the vision to no man.",
            "And his disciples asked him, saying, Why then say the scribes that Elias must first come?",
            "Howbeit this kind goeth not out but by prayer and fasting.",
            "And while they abode in Galilee, Jesus said unto them, The Son of man shall be betrayed.",
        )

        /** RST's Matthew 17: verse 11 missing, typos in verses 2 and 9. */
        val RST_TYPO_2 = KJV_17[1].replace("shine", "shnie")
        val RST_TYPO_9 = KJV_17[8].replace("vision", "vison")

        private fun books() = listOf(SpbFixture.Book(MATTHEW, "Matthew", 17), SpbFixture.Book(MARK, "Mark", 1))

        /** [count] verses of [book] [chapter], [known] first and a numbered line after them. */
        private fun chapter(book: Int, chapter: Int, count: Int, known: List<String> = emptyList()) =
            (1..count).map { n -> SpbFixture.Verse(book, chapter, n, known.getOrNull(n - 1) ?: "Verse $chapter:$n.") }

        private fun firstChapters() =
            chapter(MATTHEW, 1, MATTHEW_1_VERSES, listOf("The book of the generation of Jesus Christ.",
                "Abraham begat Isaac.")) +
                chapter(MARK, 1, MARK_1_VERSES, listOf("The beginning of the gospel of Jesus Christ."))

        fun kjv(): String = SpbFixture.buildContent(
            "King James Version", books(),
            firstChapters() + chapter(MATTHEW, 17, MATTHEW_17_VERSES, KJV_17),
        )

        /** The RST without Mark 1:45, so Mark 1 ends a verse short: a gap after the chapter's last verse. */
        fun rstShortMark(): String = rst().lines().filterNot { it.startsWith("B041C001V045") }.joinToString("\n")

        fun rst(): String = SpbFixture.buildContent(
            "Russian Synodal", books() + SpbFixture.Book(ROMANS, "Romans", 16),
            firstChapters() + chapter(MATTHEW, 17, MATTHEW_17_VERSES, KJV_17).mapNotNull { v ->
                when (v.verse) {
                    11 -> null
                    2 -> v.copy(text = RST_TYPO_2)
                    9 -> v.copy(text = RST_TYPO_9)
                    else -> v
                }
            } + chapter(ROMANS, 14, ROMANS_14_SYNODAL) + chapter(ROMANS, 16, ROMANS_16_SYNODAL),
        )

        private fun entry(id: Long, ago: Long, verse: Int, before: VerseChange?, after: VerseChange?,
            revertOf: Long? = null) =
            EditEntry(id, NOW - ago, "rst.spb", MATTHEW, 17, verse, before, after, revertOf,
                original = "Original $verse.")

        /** One of every kind: edited, added, removed, restored and reverted, from minutes to days ago. */
        fun everyKind() = listOf(
            entry(1, 72 * HOUR, 3, null, VerseChange(removed = true)),
            entry(2, 30 * HOUR, 3, VerseChange(removed = true), null),
            entry(3, 2 * HOUR, 11, null, VerseChange(text = "Howbeit this kind goeth not out.", added = true)),
            entry(4, HOUR / 2, 4, null, VerseChange(text = "Then answered Peter.")),
            entry(5, 5 * 60_000L, 4, VerseChange(text = "Then answered Peter."), null, revertOf = 4),
        )

        /** Two saved fixes in RST: verse 2 yesterday, verse 9 three hours ago. */
        fun savedFixes() = listOf(
            EditEntry(1, NOW - 27 * HOUR, "rst.spb", MATTHEW, 17, 2, after = VerseChange(text = KJV_17[1]),
                original = RST_TYPO_2),
            EditEntry(2, NOW - 3 * HOUR, "rst.spb", MATTHEW, 17, 9, after = VerseChange(text = KJV_17[8]),
                original = RST_TYPO_9),
        )
    }
}

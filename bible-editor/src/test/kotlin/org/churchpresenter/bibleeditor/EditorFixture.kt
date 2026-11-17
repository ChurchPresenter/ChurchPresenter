package org.churchpresenter.bibleeditor

import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bibleedits.BibleEditsStore
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.VerseChange
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/** A Bible folder of two translations and a data folder, under one temp directory a test deletes. */
class EditorFixture(withReference: Boolean = true) {
    val root: File = Files.createTempDirectory("cp-bible-editor").toFile()
    val bibles = File(root, "bibles").also { it.mkdirs() }
    val data = File(root, "data")
    val rst: File = SpbFixture.spbFile(bibles, "rst.spb", rst())

    init {
        if (withReference) SpbFixture.spbFile(bibles, "kjv.spb", kjv())
    }

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

        private fun firstChapters() = listOf(
            SpbFixture.Verse(MATTHEW, 1, 1, "The book of the generation of Jesus Christ."),
            SpbFixture.Verse(MATTHEW, 1, 2, "Abraham begat Isaac."),
            SpbFixture.Verse(MARK, 1, 1, "The beginning of the gospel of Jesus Christ."),
        )

        fun kjv(): String = SpbFixture.buildContent(
            "King James Version", books(),
            firstChapters() + KJV_17.mapIndexed { i, t -> SpbFixture.Verse(MATTHEW, 17, i + 1, t) },
        )

        fun rst(): String = SpbFixture.buildContent(
            "Russian Synodal", books(),
            firstChapters() + KJV_17.mapIndexedNotNull { i, t ->
                val n = i + 1
                when (n) {
                    11 -> null
                    2 -> SpbFixture.Verse(MATTHEW, 17, n, RST_TYPO_2)
                    9 -> SpbFixture.Verse(MATTHEW, 17, n, RST_TYPO_9)
                    else -> SpbFixture.Verse(MATTHEW, 17, n, t)
                }
            },
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

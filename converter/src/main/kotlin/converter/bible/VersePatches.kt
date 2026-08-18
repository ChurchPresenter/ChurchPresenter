package converter.bible

/**
 * Known incomplete or incorrect verse texts in source XML Bible files.
 * Key: Triple(bookNumber, chapterNumber, verseNumber)
 * Value: VersePatch — corrected text, optionally restricted to a specific language.
 *        If language is null, the patch applies to all translations.
 *        If minimumPrefixLength > 0, the patch is only applied if the source text
 *        has at least that many characters (safety check to avoid patching a wrong translation).
 */
data class VersePatch(
    val correctedText: String,
    val language: String? = null,
    val minimumPrefixLength: Int = 0,
    val matchText: String? = null   // if set, only apply when existing text matches this exactly
)

/**
 * A verse that is entirely missing from a file and must be inserted.
 * [verseId] is the canonical ID (e.g. "B019C146V006").
 * [bookNum], [displayChap], [displayVers] are the tab-separated display columns.
 * [insertAfterDisplayVers] locates the insertion point within the same chapter.
 */
data class MissingVersePatch(
    val verseId: String,
    val bookNum: Int,
    val displayChap: Int,
    val displayVers: Int,
    val verseText: String,
    val insertAfterDisplayVers: Int
)

object VersePatches {
    /** Text corrections for known truncated/broken verses. */
    val PATCHES: Map<Triple<Int, Int, Int>, VersePatch> = mapOf(
        // 2 Chronicles 2:14 — truncated in Russian Synodal source XML (ends mid-word "...госпо")
        Triple(14, 2, 14) to VersePatch(
            language = "RUS",
            minimumPrefixLength = 20,
            correctedText = "Сына [одной] женщины из дочерей Дановых, — а отец его Тирянин, — умеющего " +
                "делать [изделия] из золота и из серебра, из меди, из железа, из камней и из дерев, " +
                "из [пряжи] пурпурового, яхонтового [цвета], и из виссона, и из багряницы, и " +
                "вырезывать всякую резьбу, и исполнять все, что будет поручено ему вместе с " +
                "художниками твоими и с художниками господина моего Давида, отца твоего."
        ),
        // Psalm 146:6 (display) — grammatical error "до землю" → "до земли"
        Triple(19, 146, 6) to VersePatch(
            matchText = "Смиренных возвышает Господь, а нечестивых унижает до землю.",
            correctedText = "Смиренных возвышает Господь, а нечестивых унижает до земли."
        )
    )

    /** Wrong verse IDs that must be renamed. Key = bad ID in file, Value = correct ID. */
    val ID_CORRECTIONS: Map<String, String> = mapOf(
        "B019C147V096" to "B019C147V006"    // Psalm 146:6 — digit corruption (96 → 06)
    )

    /** Verses missing entirely from the file that must be inserted. */
    val MISSING_VERSES: List<MissingVersePatch> = listOf(
        // Psalm 145:6 (display) = B019C146V006 — absent from Russian Synodal SPB
        MissingVersePatch(
            verseId = "B019C146V006",
            bookNum = 19,
            displayChap = 145,
            displayVers = 6,
            verseText = "Сотворившего небо и землю, море и все, что в них, вечно хранящего верность,",
            insertAfterDisplayVers = 5
        )
    )
}

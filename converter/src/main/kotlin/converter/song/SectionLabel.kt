package converter.song

/**
 * The section names other song apps use, mapped onto the ones ChurchPresenter writes.
 *
 * Two spellings turn up and both have to be read. The OpenLyrics family and OpenLP's database use a
 * letter with an optional number — `v1`, `C`, `b2` — while OpenSong, EasySlides and FreeShow write
 * the word out: `[VERSE1]`, `Chorus`, `PreChorus`. Anything unrecognised is passed through
 * unchanged rather than guessed at, so a hymnal's own `Antiphon` survives the import.
 *
 * [tidy] exists because a numbered label reads badly when there is only one of its kind: OpenLP
 * stores every chorus as `c1` whether the song has one or three, and "Chorus 1" on a song with a
 * single chorus is noise a human then has to edit out of every file.
 */
internal object SectionLabel {

    private val splitWordAndNumber = Regex("""^(.*?)\s*(\d*)$""")

    private val names = mapOf(
        "v" to "Verse", "verse" to "Verse", "vers" to "Verse", "strophe" to "Verse",
        "c" to "Chorus", "chorus" to "Chorus", "refrain" to "Chorus",
        "b" to "Bridge", "bridge" to "Bridge",
        "p" to "Pre-Chorus", "prechorus" to "Pre-Chorus", "pre-chorus" to "Pre-Chorus",
        "t" to "Tag", "tag" to "Tag",
        "e" to "Ending", "ending" to "Ending", "end" to "Ending",
        "i" to "Intro", "intro" to "Intro",
        "o" to "Outro", "outro" to "Outro",
        "coda" to "Coda",
    )

    /** `V1` becomes `Verse 1`, `C` becomes `Chorus`, `Antiphon 2` stays as it is. */
    fun of(marker: String): String {
        val cleaned = marker.trim().removeSurrounding("[", "]").trim()
        val match = splitWordAndNumber.find(cleaned)
        if (cleaned.isEmpty() || match == null) return cleaned.ifEmpty { "Verse" }

        val (word, number) = match.destructured
        // A marker that is nothing but a number is a verse: EasySlides numbers its verses `[1]`,
        // `[2]` and names only the other sections, so reading these as "1" and "2" would leave a
        // whole library's verses labelled with bare digits.
        val name = if (word.isBlank()) "Verse" else names[word.lowercase().replace(" ", "")]
        return when {
            name == null -> cleaned
            word.isBlank() && number.isEmpty() -> cleaned
            number.isEmpty() -> name
            else -> "$name $number"
        }
    }

    /**
     * Drops the ` 1` from a label whose base name appears only once in the song.
     *
     * Verses are left alone: they are numbered because a song has several, and "Verse" on its own
     * reads as a mistake even when there is only one. Choruses are the opposite — OpenLP stores
     * every chorus as `c1` whether the song has one or three, and "Chorus 1" on a song with a
     * single chorus is noise someone then edits out of every file by hand.
     */
    fun tidy(labels: List<String>): List<String> {
        val counts = labels.groupingBy { baseOf(it) }.eachCount()
        return labels.map { label ->
            val base = baseOf(label)
            if (base != "Verse" && label == "$base 1" && counts[base] == 1) base else label
        }
    }

    private fun baseOf(label: String): String =
        splitWordAndNumber.find(label)?.destructured?.component1()?.trim() ?: label
}

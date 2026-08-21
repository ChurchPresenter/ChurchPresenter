package org.churchpresenter.songchords

/** The kind of section a header word names, before it is narrowed to a colour or a bracket. */
enum class SongSectionWordGroup { PRE_CHORUS, CHORUS, BRIDGE, TAG, INTRO, INSTRUMENTAL, VERSE }

/**
 * The words a song file may name a section with, in the languages songbooks actually arrive in.
 *
 * These are **file markers, not interface text**, so they deliberately do not come from string
 * resources: a Russian songbook has to parse for an operator running the app in English, and a
 * Polish one for an operator running it in Russian. Every language is matched at once, always,
 * independently of the chosen locale — keying them off the UI language would be exactly backwards.
 *
 * The single source of truth for the three places that read section words: [SongSectionWords.isChorus]
 * decides `{}` vs `[]` when wrapping a bare header, `sectionKindOf` colours the chip from
 * [SongSectionWords.groupOf], and `ChordSheetImporter` canonicalises to English from [CANONICAL].
 */
object SongSectionWords {

    /**
     * Ordered, because pre-chorus has to be tested before chorus or it matches as a plain one.
     * Words are lowercase; matching lowercases the header first.
     */
    private val WORDS: List<Pair<SongSectionWordGroup, List<String>>> = listOf(
        SongSectionWordGroup.PRE_CHORUS to listOf(
            "pre-chorus", "prechorus", "pre chorus", "предприпев", "передприспів", "przedrefren",
        ),
        SongSectionWordGroup.CHORUS to listOf(
            "chorus", "refrain", "припев", "припеў", "приспів", "refren", "refrén", "refrein",
            "refrään", "kehrvers", "estribillo", "estribilho", "refrão", "coro", "қайырма",
        ),
        SongSectionWordGroup.BRIDGE to listOf(
            "bridge", "бридж", "мост", "міст", "most", "brücke", "puente", "ponte", "brug",
            "punte", "көпір", "sild",
        ),
        SongSectionWordGroup.TAG to listOf(
            "tag", "outro", "ending", "coda", "кода", "концовка", "закінчення", "zakończenie",
            "závěr", "záver", "schluss", "slot", "finale", "final",
        ),
        SongSectionWordGroup.INTRO to listOf(
            "intro", "интро", "вступление", "вступ", "wstęp", "einleitung",
        ),
        SongSectionWordGroup.INSTRUMENTAL to listOf(
            "instrumental", "проигрыш", "програш", "przegrywka",
        ),
        SongSectionWordGroup.VERSE to listOf(
            "verse", "vers", "verso", "куплет", "куплэт", "zwrotka", "strophe", "strofa",
            "estrofa", "estrofe", "couplet", "sloka", "salm", "шумақ",
        ),
    )

    /**
     * What a header may carry after its word: a number, punctuation, a repeat count, a
     * parenthetical — "Verse 2", "Припев:", "Chorus 2x", "Припев (2 раза)".
     *
     * Anything that runs on into further words is a lyric that merely opens with the word, not a
     * header: "Most of all I love you", "Refrain from evil", "Bridge over troubled water". That
     * distinction is what lets the list above carry short, common words like `most` and `slot`
     * without swallowing English lyrics.
     */
    private val HEADER_TAIL = Regex(
        "^[^\\p{L}]*(?:[xх][^\\p{L}]*)?(?:\\(.*\\)|\\[.*\\])?[^\\p{L}]*$",
        RegexOption.IGNORE_CASE,
    )

    /** The canonical English name each group is written as when a chord sheet is imported. */
    val CANONICAL: Map<SongSectionWordGroup, String> = mapOf(
        SongSectionWordGroup.PRE_CHORUS to "Pre-Chorus",
        SongSectionWordGroup.CHORUS to "Chorus",
        SongSectionWordGroup.BRIDGE to "Bridge",
        SongSectionWordGroup.TAG to "Tag",
        SongSectionWordGroup.INTRO to "Intro",
        SongSectionWordGroup.INSTRUMENTAL to "Instrumental",
        SongSectionWordGroup.VERSE to "Verse",
    )

    /**
     * Which group [label] names, or null when no language's word opens it.
     *
     * [label] is the bare header — any `[]`/`{}` already off it.
     */
    fun groupOf(label: String): SongSectionWordGroup? {
        val l = label.trim().lowercase()
        if (l.isEmpty()) return null
        return WORDS.firstOrNull { (_, words) ->
            words.any { w -> l.startsWith(w) && HEADER_TAIL.matches(l.substring(w.length)) }
        }?.first
    }

    /** True when [label] names a chorus — a pre-chorus deliberately does not count as one. */
    fun isChorus(label: String): Boolean = groupOf(label) == SongSectionWordGroup.CHORUS

    /** True when [label] names any section this app knows a word for. */
    fun isKnownSection(label: String): Boolean = groupOf(label) != null

    /**
     * The group a free-text chord-sheet heading names, matched loosely — the word may sit anywhere
     * in a short line ("2. Zwrotka", "CHORUS:"), which a sheet written by hand often does.
     *
     * Length-capped because a lyric line that merely contains the word is not a heading; the strict
     * [groupOf] cannot be used here because such a sheet has no brackets to anchor on.
     */
    fun looseGroupOf(line: String, maxLength: Int = 32): SongSectionWordGroup? {
        val t = line.trim()
        if (t.isEmpty() || t.length > maxLength) return null
        val l = t.lowercase()
        return WORDS.firstOrNull { (_, words) -> words.any { l.contains(it) } }?.first
    }
}

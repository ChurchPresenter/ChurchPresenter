package converter.song

/**
 * Turns a song's lyrics into sections, for the formats that do not hand over a list of slides
 * ready-made.
 *
 * Two jobs, both shared by more than one converter: splitting one run of text on its blank lines
 * (EasyWorship stores a whole song that way), and naming the sections that come out — including the
 * ones the source never named, which is every format's common case.
 */
internal object LyricBlocks {

    private val blankLine = Regex("""\n[ \t]*\n\s*""")
    private val whitespace = Regex("""\s+""")
    private val verseNumber = Regex("""^Verse (\d+)$""")

    private const val NAMES = "verse|vers|strophe|chorus|refrain|bridge|pre[\\s-]?chorus|intro|" +
        "outro|ending|end|tag|slide|coda|instrumental|interlude|куплет|припев|приспів|хор|мост|" +
        "міст|вступление|вступ|окончание|конец"

    // The number is part of the name to match: SongBeamer and Quelea both write `VERSE1` with no
    // space, and a plain `\\b` after the word finds no boundary between `VERSE` and `1`.
    private val labelWords = Regex("^($NAMES)(\\s*\\d+)?\\b", RegexOption.IGNORE_CASE)

    /** A name and nothing else — `Chorus`, `Verse 2`, `PRE-CHORUS` — with no lyric alongside it. */
    private val plainName = Regex("^($NAMES)\\s*\\d*$", RegexOption.IGNORE_CASE)

    private const val MAX_HEADING_LENGTH = 30
    private const val MAX_HEADING_WORDS = 3

    /**
     * Punctuation a heading does not carry but a sung line does. `This is my Father's world:` ends
     * in a colon and is the first line of three of that hymn's verses, not a name for them.
     */
    private val sungPunctuation = Regex("""[,.;!?'’]""")
    private const val MAX_LABEL_LENGTH = 24

    /**
     * The section name [line] states, or null when it is a line of the song.
     *
     * Stricter than [isLabel], because this decides whether to *remove* the line: a heading has to
     * be the whole line, so a verse opening "End of the day" keeps its first line instead of losing
     * it to the label. Three forms are headings and nothing else is — bracketed (`[Chorus]`), a
     * name on its own (`Verse 2`), and a few words ending in a colon (`Head:`), which is how a song
     * written by hand marks a section the vocabulary above does not cover. That last form is capped
     * at [MAX_HEADING_WORDS] and rejects [sungPunctuation] because a lyric can end in a colon too.
     */
    fun headingOf(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val bracketed = (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("{") && trimmed.endsWith("}"))
        val inner = trimmed.trim('[', ']', '{', '}').trim().removeSuffix(":").trim()
        if (inner.isEmpty()) return null
        if (bracketed) return inner
        if (plainName.matches(inner)) return inner
        if (!trimmed.endsWith(":") || trimmed.length > MAX_HEADING_LENGTH) return null
        if (sungPunctuation.containsMatchIn(inner)) return null
        return inner.takeIf { it.split(whitespace).size <= MAX_HEADING_WORDS }
    }

    /**
     * Names a section rather than singing one.
     *
     * The length cap is what keeps a lyric out of the label slot: "Verse of the Lord be with you"
     * opens with a word this would otherwise match, and losing a line of a song is worse than
     * leaving a section unlabelled. A bracketed line is taken as a label whatever its length,
     * since nothing sings brackets.
     */
    fun isLabel(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val bracketed = (trimmed.startsWith("[") && trimmed.endsWith("]")) ||
            (trimmed.startsWith("{") && trimmed.endsWith("}"))
        val inner = trimmed.trim('[', ']', '{', '}').trim()
        if (inner.isEmpty()) return false
        if (bracketed) return true
        return inner.length <= MAX_LABEL_LENGTH && labelWords.containsMatchIn(inner)
    }

    /**
     * Final labels for sections whose source names are [names], null where the source gave none.
     *
     * What an unnamed section is depends on whether the song names any of them at all:
     *
     *  - **Some are named.** Then an unnamed one is the rest of the section above it, carried onto
     *    a second slide because it did not fit — which is how EasyWorship and ProPresenter both
     *    split a long verse. It takes that section's name. Numbering it instead would turn a verse
     *    shown over two slides into "Verse 1" followed by an unrelated-looking "Verse 4".
     *  - **None are named.** Then there is no structure to continue and the sections are numbered
     *    as verses, which is all that can honestly be said about them.
     *
     * Numbering skips whatever the named sections already used, so an explicit "Verse 2" and a
     * generated one never collide.
     */
    fun labels(names: List<String?>): List<String> {
        val named = names.map { name -> name?.takeIf { it.isNotBlank() }?.let { SectionLabel.of(it) } }
        val continues = named.any { it != null }
        val taken = named
            .mapNotNull { label -> label?.let { verseNumber.find(it)?.groupValues?.get(1)?.toIntOrNull() } }
            .toMutableSet()

        var next = 1
        var previous: String? = null
        val filled = named.map { label ->
            val resolved = label
                ?: previous?.takeIf { continues }
                ?: run {
                    while (!taken.add(next)) next++
                    "Verse $next"
                }
            previous = resolved
            resolved
        }
        return SectionLabel.tidy(filled)
    }

    /** [text] as sections, blank-line separated, with leading names lifted into the label. */
    fun split(text: String): List<SongSection> {
        val names = mutableListOf<String?>()
        val bodies = mutableListOf<List<String>>()

        for (block in text.trim().split(blankLine)) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val named = isLabel(lines.first())
            val body = if (named) lines.drop(1) else lines
            // A name with nothing under it labels the block that follows, not one of its own.
            if (body.isEmpty()) continue
            names.add(if (named) lines.first().trim('[', ']', '{', '}').trim() else null)
            bodies.add(body)
        }

        return labels(names).mapIndexed { index, label -> SongSection(label, bodies[index]) }
    }
}

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
    private val verseNumber = Regex("""^Verse (\d+)$""")

    /**
     * Names a section rather than singing one.
     *
     * The length cap is what keeps a lyric out of the label slot: "Verse of the Lord be with you"
     * opens with a word this would otherwise match, and losing a line of a song is worse than
     * leaving a section unlabelled. A bracketed line is taken as a label whatever its length,
     * since nothing sings brackets.
     */
    private val labelWords = Regex(
        "^(verse|vers|strophe|chorus|refrain|bridge|pre[\\s-]?chorus|intro|outro|ending|end|tag|" +
            "slide|coda|куплет|припев|приспів|хор|мост|міст|вступление|вступ|окончание|конец)\\b",
        RegexOption.IGNORE_CASE,
    )

    private const val MAX_LABEL_LENGTH = 24

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
